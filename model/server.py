# -*- coding: utf-8 -*-
"""YOLOv5 双模型校园安防识别服务。"""
import os
import shutil
import threading
import time
from pathlib import Path

import requests
import torch
import uvicorn
from fastapi import FastAPI

# 目录基于本文件位置推导，不依赖绝对路径或用户名。
ROOT = Path(__file__).resolve().parent
YOLOV5_DIR = ROOT / "yolov5"
COCO_PT = Path(os.environ.get(
    "COCO_PT", ROOT / "models" / "yolov5s-coco.pt"))
DFIRE_PT = Path(os.environ.get(
    "DFIRE_PT", ROOT / "models" / "yolov5s-dfire.pt"))
WATCH_DIR = Path(os.environ.get("AI_WATCH_DIR", ROOT / "ai-watch"))
DONE_DIR = Path(os.environ.get("AI_DONE_DIR", WATCH_DIR / "已检测"))

# Java 后端接收 AI 结果的接口（平台端口按讲义 yml 统一为 8888，可用环境变量覆盖）。
BACKEND_URL = os.environ.get("BACKEND_URL", "http://127.0.0.1:8888/api/ai/event")

# 只处理这些图片格式。
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}

WATCH_DIR.mkdir(parents=True, exist_ok=True)
DONE_DIR.mkdir(parents=True, exist_ok=True)

# 同时加载两个现成权重。
coco_model = torch.hub.load(
    str(YOLOV5_DIR), "custom", path=str(COCO_PT), source="local")
dfire_model = torch.hub.load(
    str(YOLOV5_DIR), "custom", path=str(DFIRE_PT), source="local")

# 指南§九第5步要求正常场景图不产生告警；hub 默认 conf=0.25 时 10 号图会误报
# car(28%)，故 COCO 模型阈值上调为 0.4（D-Fire 保持默认 0.25）。
coco_model.conf = 0.4

# 两个入口可能同时调用模型，用锁保证依次识别。
model_lock = threading.Lock()

app = FastAPI(title="YOLOv5 双模型校园安防识别服务")


def result_to_objects(result) -> list[dict]:
    """把 YOLOv5 结果转换成 Java 后端需要的格式。"""
    objects = []
    try:
        img_h, img_w = result.ims[0].shape[:2]  # numpy: 高、宽
    except Exception:
        img_w = img_h = 0
    # 每一行依次是 x1、y1、x2、y2、置信度、类别编号。
    for row in result.xyxy[0].cpu().tolist():
        confidence = float(row[4])
        class_id = int(row[5])
        # 模型类别名称可能是字典或列表。
        class_name = result.names[class_id]
        item = {
            "class": str(class_name).lower(),
            "conf": confidence,
        }
        # 增量字段：归一化到 0~1 的边框，供前端画框；旧消费者只读 class/conf 不受影响。
        if img_w and img_h:
            item["box"] = [round(row[0] / img_w, 4), round(row[1] / img_h, 4),
                           round(row[2] / img_w, 4), round(row[3] / img_h, 4)]
        objects.append(item)
    return objects


def recognize(image_path: Path) -> list[dict]:
    """同一张图片分别交给两个模型，然后合并结果。"""
    with model_lock:
        coco_result = coco_model(str(image_path))
        dfire_result = dfire_model(str(image_path))
    coco_objects = result_to_objects(coco_result)
    dfire_objects = result_to_objects(dfire_result)
    return coco_objects + dfire_objects


@app.get("/")
def home():
    """浏览器访问该接口，检查服务是否启动。"""
    return {
        "status": "ok",
        "cocoModel": str(COCO_PT),
        "dfireModel": str(DFIRE_PT),
        "watch": str(WATCH_DIR),
        "done": str(DONE_DIR),
    }


@app.get("/detect")
def detect(url: str = str(YOLOV5_DIR / "data" / "images" / "bus.jpg")):
    """手工识别接口；不传地址时识别 YOLOv5 自带图片。"""
    image_path = Path(url)
    if not image_path.is_file():
        return {"status": "error", "message": "图片不存在"}
    return {"status": "ok", "objects": recognize(image_path)}


def move_to_done(source: Path) -> Path:
    """把图片移动到已检测；重名时在文件名后添加时间。"""
    target = DONE_DIR / source.name
    if target.exists():
        stamp = time.strftime("%Y%m%d_%H%M%S")
        target = DONE_DIR / f"{source.stem}_{stamp}{source.suffix}"
    shutil.move(str(source), str(target))
    return target


def watch_images():
    """每 2 秒检查一次 ai-watch。"""
    print(f"[监听] {WATCH_DIR}")
    while True:
        for image_path in WATCH_DIR.iterdir():
            # 忽略文件夹和非图片文件。
            if not image_path.is_file():
                continue
            if image_path.suffix.lower() not in IMAGE_SUFFIXES:
                continue
            try:
                # 使用两个模型识别同一张图片。
                objects = recognize(image_path)
                # 识别完成后移动图片。
                done_path = move_to_done(image_path)
                # 按 Java 后端要求组织 JSON。
                payload = {
                    "image": done_path.name,
                    "imagePath": str(done_path),
                    "objects": objects,
                }
                # 提交识别结果。
                response = requests.post(
                    BACKEND_URL, json=payload, timeout=10
                )
                print(
                    f"[完成] {done_path.name} | "
                    f"{objects} | 后端状态 {response.status_code}"
                )
            except Exception as error:
                # 一张图片失败不会停止整个服务。
                print(f"[失败] {image_path.name} | {error}")
        # 每轮扫描后等待 2 秒。
        time.sleep(2)


# 在后台启动目录监听。
threading.Thread(target=watch_images, daemon=True).start()

if __name__ == "__main__":
    # 监听 8000 端口，与 Java 后端配置一致。
    uvicorn.run(app, host="0.0.0.0", port=8000)
