#!/bin/bash
# 一键启动：Docker(MySQL+平台后端) + AI识别服务
ROOT="$(cd "$(dirname "$0")" && pwd)"

# 密码从 .env 读取（.env 不入库）
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi
: "${MYSQL_ROOT_PASSWORD:?请先在 $ROOT/.env 中设置 MYSQL_ROOT_PASSWORD（可参考 .env.example）}"

# 若当前用户不在 docker 组则需要 sudo
if docker info >/dev/null 2>&1; then DOCKER="docker"; else DOCKER="sudo docker"; fi

cd "$ROOT/deploy"
$DOCKER compose --env-file "$ROOT/.env" up -d

if ! curl -s -m 2 localhost:8000/ >/dev/null 2>&1; then
  cd "$ROOT/ai-service"
  setsid bash start-server.sh > /tmp/ai-server.log 2>&1 < /dev/null &
  echo "AI服务启动中(首次加载模型约30秒)..."
fi
for i in $(seq 1 30); do
  a=$(curl -s -m 2 localhost:8000/ >/dev/null 2>&1 && echo ok)
  b=$(curl -s -m 2 -o /dev/null -w %{http_code} localhost:8888/ 2>/dev/null)
  # 8888 已启用 Basic Auth：未带密码返回 401 同样代表服务就绪
  [ "$a" = "ok" ] && { [ "$b" = "200" ] || [ "$b" = "401" ]; } && echo "全部就绪: 平台 http://localhost:8888  AI :8000" && exit 0
  sleep 2
done
echo "有服务未就绪, 查看日志: /tmp/ai-server.log 或 $DOCKER logs deploy-app-1"
