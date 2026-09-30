#!/bin/bash
# 安防平台 · 公网演示一键开关
# 启动：Docker平台 + AI识别服务 + cloudflared公网隧道
# 退出（关窗口/Ctrl+C/隧道崩溃）：自动彻底停止以上全部，公网链接即刻永久失效
#
# 密码从仓库根目录的 .env 读取（.env 不入库）。使用前先：
#   cp .env.example .env   然后填入 MYSQL_ROOT_PASSWORD / APP_AUTH_PASSWORD
BASE="$(cd "$(dirname "$0")/.." && pwd)"

if [ -f "$BASE/.env" ]; then
  set -a; . "$BASE/.env"; set +a
fi
: "${APP_AUTH_USER:?请先在 $BASE/.env 中设置 APP_AUTH_USER（可参考 .env.example）}"
: "${APP_AUTH_PASSWORD:?请先在 $BASE/.env 中设置 APP_AUTH_PASSWORD（可参考 .env.example）}"
PASS="$APP_AUTH_PASSWORD"
LOG=/tmp/cloudflared.log

if docker info >/dev/null 2>&1; then DOCKER="docker"; else DOCKER="sudo docker"; fi

cleanup() {
  trap - EXIT HUP INT TERM
  echo ""
  echo "[停止] 正在关闭全部服务..."
  pkill -x cloudflared 2>/dev/null
  pkill -f "python server.py" 2>/dev/null
  (cd "$BASE/deploy" && $DOCKER compose --env-file "$BASE/.env" stop -t 5 >/dev/null 2>&1)
  echo "[完成] 平台已停止、公网链接已永久失效。本窗口可以关闭了。"
  sleep 4
}
trap cleanup EXIT HUP INT TERM

echo "[1/3] 启动 Docker 平台 + AI识别服务（模型首次加载约30秒，请稍候）..."
bash "$BASE/start-all.sh" || { echo "[错误] 平台或AI服务未就绪，上方有原因。将自动退出。"; exit 1; }

echo "[2/3] 建立公网隧道（Cloudflare 免费线路）..."
cloudflared tunnel --url http://127.0.0.1:8888 --no-autoupdate >"$LOG" 2>&1 &
CF=$!
URL=""
for i in $(seq 1 30); do
  kill -0 "$CF" 2>/dev/null || { echo "[错误] 隧道进程退出，日志如下："; tail -5 "$LOG"; exit 1; }
  URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$LOG" | head -1)
  [ -n "$URL" ] && break
  sleep 2
done
[ -z "$URL" ] && { echo "[错误] 30次尝试未拿到公网地址，可能网络异常。"; exit 1; }

echo "[3/3] 等待公网线路预热..."
for i in $(seq 1 15); do
  c=$(curl -s -m 10 -o /dev/null -w "%{http_code}" "$URL/" 2>/dev/null)
  [ "$c" = "200" ] && break
  sleep 3
done

echo ""
echo "============================================================"
echo "  ✅ 公网演示已就绪！把这个链接发给老师（点开即用，免输密码）："
echo ""
echo "  ${URL}/#auth=${PASS}"
echo ""
echo "  备用：${URL}/  账号 ${APP_AUTH_USER} 密码 ${PASS}"
echo "============================================================"
echo "  ⚠ 演示期间请保持本黑窗口开着（电脑别睡眠）。"
echo "  ⚠ 演示结束：直接点右上角 × 关闭本窗口，一切自动彻底停止。"
wait "$CF"
