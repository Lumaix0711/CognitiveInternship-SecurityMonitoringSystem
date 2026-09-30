#!/bin/bash
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/venv/bin/activate"
cd "$HERE/yolov5-master"
python train.py --weights ../yolov5s-coco.pt --cfg models/yolov5s.yaml \
  --data data/coco128.yaml --epochs 3 --batch-size 16 --img 640 \
  --project "$HERE/runs" --name campus-smoke \
  --device 0 --workers 4 2>&1
echo TRAIN_DONE
