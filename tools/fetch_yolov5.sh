#!/bin/bash
# 克隆官方 ultralytics/yolov5 工程到 model/yolov5。
#
# 本仓库不收录 yolov5 源码：它是 ultralytics 的第三方作品（AGPL-3.0），
# 整份拷贝进来会带来许可义务与体积负担。改为按需克隆固定版本。
#
# 权重文件同样不入库，需自行放到 model/models/：
#   model/models/yolov5s-coco.pt    COCO 通用模型（人/车/动物/物品）
#   model/models/yolov5s-dfire.pt   D-Fire 烟火模型（fire/smoke）
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/model/yolov5"
TAG="v7.0"

if [ -d "$TARGET/.git" ]; then
  echo "已存在：$TARGET"
  exit 0
fi

echo "正在克隆 ultralytics/yolov5 ($TAG) ..."
git clone --depth 1 --branch "$TAG" https://github.com/ultralytics/yolov5.git "$TARGET"
echo "完成：$TARGET"
echo
echo "接下来请自行放置权重："
echo "  $ROOT/model/models/yolov5s-coco.pt"
echo "  $ROOT/model/models/yolov5s-dfire.pt"
