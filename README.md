# 智能安防远程监控与告警平台

基于 YOLOv5 双模型的 AI 视觉安防平台：图片经双模型识别后，由 Java 后端按业务规则映射为告警、写入 MySQL，并通过 WebSocket 实时推送到网页端，支持完整的「待处置 → 处置中 → 已关闭」处置闭环。

技术栈：Java 17 / Spring Boot / MyBatis-Plus / MySQL 8 / WebSocket + Python / FastAPI / YOLOv5 + Docker Compose

## 系统构成

| 组成 | 说明 |
|---|---|
| 网页前端 | Vue 3 单页，五个菜单：监控大屏、设备信息、AI识别记录、实时告警中心、告警处置记录 |
| 平台后端 | Java 17 + Spring Boot，REST 接口 + WebSocket 推送，端口 8888 |
| 数据库 | MySQL 8，表 `device` / `alarm` / `ai_record` |
| AI 识别服务 | Python + FastAPI + YOLOv5 双模型，端口 8000 |
| 部署 | Docker Compose 编排 MySQL 与后端；AI 服务因需访问 GPU 留在宿主机 |

数据流：投放图片 → 双模型识别并合并 `objects` → `POST /api/ai/event` → 后端按规则表映射告警 → 写入 MySQL → WebSocket 广播 `new-alarm` → 前端弹窗刷新 → 人工处置。

## 告警映射规则

规则自上而下匹配，命中第一条即生效：

| 识别结果 | 告警类型 | 等级 |
|---|---|---|
| `fire` / `smoke` | 烟火异常 | 高 |
| `person` ≥ 2 | 人员聚集 | 高 |
| `person` = 1 | 人员闯入 | 中 |
| `car` / `truck` / `bus` | 车辆异常 | 中 |
| `dog` / `cat` 等 | 动物进入 | 中 |
| `suitcase` / `box` 等 | 物品异常 | 中 |
| 其它 / 低于置信度阈值 | 不入库、不推送 | — |

模型只输出「物体」，业务规则负责把物体翻译成有业务含义的告警。COCO 模型置信度阈值 0.4，D-Fire 模型 0.25。

## 目录结构

```
smart-security/
├── backend/            # Java 后端（Spring Boot）
├── ai-service/         # Python 虚拟环境与依赖安装脚本
├── model/
│   ├── server.py       # 双模型识别服务（FastAPI）
│   ├── check_models.py # 权重加载自检
│   └── ai-watch/       # 待识别图片投放目录（运行时生成）
├── deploy/
│   ├── docker-compose.yml
│   └── mysql8-security-monitor.sql
├── tools/
│   ├── fetch_yolov5.sh # 克隆官方 YOLOv5 工程
│   └── public_demo.sh  # 公网演示一键开关
└── start-all.sh        # 一键启动
```

## 环境准备

```bash
cp .env.example .env
# 生成随机密码填入 .env
#   openssl rand -base64 24 | tr -d '/+=' | cut -c1-28
```

`.env` 不会被提交。`MYSQL_ROOT_PASSWORD`、`APP_AUTH_PASSWORD` 缺任一项，compose 会直接报错退出——这是刻意的，避免用弱默认值启动。

## 部署

```bash
# 1. 克隆官方 YOLOv5 工程（v7.0，AGPL-3.0）
bash tools/fetch_yolov5.sh

# 2. 放置权重到 model/models/
#    yolov5s-coco.pt  （COCO 通用：人/车/动物/物品）
#    yolov5s-dfire.pt （D-Fire 烟火：fire/smoke）

# 3. 创建 Python 环境
python3 -m venv ai-service/venv
bash ai-service/install_deps.sh

# 4. 打包后端
cd backend && mvn -q package && cd ..

# 5. 启动
bash start-all.sh
```

平台 <http://localhost:8888>，接口文档 <http://localhost:8888/swagger-ui/index.html>，AI 服务 <http://localhost:8000>。

8888 端口启用了 Basic Auth，账号密码见 `.env` 的 `APP_AUTH_USER` / `APP_AUTH_PASSWORD`。MySQL 只绑定 `127.0.0.1:3306`，不对外暴露。

## 演示

把任意图片放进 `model/ai-watch/`，约 2 秒内会被识别并移动到 `model/ai-watch/已检测/`，网页端弹出对应告警。图片命名建议遵循 `编号_区域_场景.png`，平台会从文件名解析告警区域。

`model/ai-watch/tmp/` 供按需分析接口存放上传文件，与监听目录分离以避免同一张图被处理两次。

## 关于仓库内容

以下内容**有意不收录**在本仓库：

- **测试图片**（`assets/`、`model/test-images/`）——原始素材含可辨识的真实人脸，出于隐私原因排除。演示时请自行准备图片。
- **模型权重**（`*.pt`）——体积大且各有权重自身的许可条款，请自行获取放置。
- **YOLOv5 源码**（`model/yolov5/`）——第三方作品（AGPL-3.0），改由 `tools/fetch_yolov5.sh` 按需克隆。
- **任何密钥**——全部通过 `.env` 注入。

## 文档材料

`docs/` 存放本次认知实习的原始材料，仅作存档参考：

| 文件 | 说明 |
|---|---|
| `认知实习-实习报告-刘孟灏.pdf` | 本人的实习报告（第一人称日记 + 总结） |
| `20260902认知实习指导书.docx` | 课程指导书 |
| `智能安防远程监控与告警平台-实训讲义.docx` | 课程实训讲义 |
| `YOLOv5双模型校园安防实训指南.docx` | 双模型实训指南 |

> ⚠️ 后三份为学校/课程提供 materials，版权归原方所有，此处仅作个人存档，**请勿再行分发**。

## 许可

本项目**代码**以 MIT 许可发布，见 [LICENSE](LICENSE)。

第三方部分另有归属：YOLOv5 为 ultralytics 所有，遵循 AGPL-3.0；`static/js/vue.global.prod.js` 为 Vue.js 官方构建版（MIT）；模型权重各自适用其发布方的许可条款。`docs/` 中的课程材料版权归学校及原讲师所有。

