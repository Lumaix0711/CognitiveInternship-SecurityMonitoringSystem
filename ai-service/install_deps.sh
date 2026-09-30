#!/bin/bash
set -e
cd "$(dirname "$0")"
source venv/bin/activate
IDX="-i https://pypi.tuna.tsinghua.edu.cn/simple"
echo "[1/3] torch==2.9.1 torchvision==0.24.1 (cu128) $(date +%T)"
pip install $IDX torch==2.9.1 torchvision==0.24.1 2>&1 | grep --line-buffered -E 'Downloading.*\(|Installing collected|Successfully installed|ERROR'
echo "[2/3] AI其它依赖 $(date +%T)"
pip install $IDX opencv-python-headless flask requests matplotlib pandas pillow pyyaml scipy tqdm seaborn psutil 2>&1 | grep --line-buffered -E 'Successfully installed|ERROR'
echo "[3/3] 验证GPU $(date +%T)"
python -c "import torch;print('torch',torch.__version__,'cuda:',torch.version.cuda,'可用:',torch.cuda.is_available());print('设备:',torch.cuda.get_device_name(0),'算力:',torch.cuda.get_device_capability(0))"
echo "ALL_DONE $(date +%T)"
