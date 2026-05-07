#!/bin/bash
# VisionGuard 服务器端启动脚本
# 用法（在服务器上）: cd deploy && bash deploy.sh

set -e

# 检查 .env
if [ ! -f .env ]; then
    echo "创建 .env..."
    cp .env.example .env
    echo "请编辑 .env 后重新运行"
    exit 1
fi

echo "=== VisionGuard 启动 ==="
docker compose -f docker-compose.prod.yml down 2>/dev/null || true
docker compose -f docker-compose.prod.yml up -d --build
echo "=== 启动完成 ==="
echo "健康检查: curl http://localhost:3000/api/v1/healthz"
