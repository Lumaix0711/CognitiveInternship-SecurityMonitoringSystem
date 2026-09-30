# -*- coding: utf-8 -*-
"""指南§六：检查两个模型能否加载。"""
import os
from pathlib import Path

import torch

# 目录基于本文件位置推导，不依赖绝对路径或用户名。
ROOT = Path(__file__).resolve().parent
YOLOV5_DIR = ROOT / "yolov5"
COCO_PT = Path(os.environ.get(
    "COCO_PT", ROOT / "models" / "yolov5s-coco.pt"))
DFIRE_PT = Path(os.environ.get(
    "DFIRE_PT", ROOT / "models" / "yolov5s-dfire.pt"))

# source="local" 表示使用 model/yolov5 中的代码。
coco_model = torch.hub.load(
    YOLOV5_DIR, "custom", path=COCO_PT, source="local")
dfire_model = torch.hub.load(
    YOLOV5_DIR, "custom", path=DFIRE_PT, source="local")

print("COCO 模型类别：", coco_model.names)
print("D-Fire 模型类别：", dfire_model.names)
print("两个模型加载成功")
