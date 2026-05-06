#!/bin/bash
# VisionGuard 一键部署脚本
# 用法: chmod +x deploy.sh && ./deploy.sh

set -e

echo "=== VisionGuard 部署脚本 ==="
echo ""

# 检查 .env
if [ ! -f .env ]; then
    echo "[1/3] 创建 .env 文件..."
    cp .env.example .env
    echo "  → 请编辑 .env 填入生产环境配置，然后重新运行 ./deploy.sh"
    echo "  关键配置: DB_PASSWORD, JWT_SECRET"
    exit 1
fi

# 拉取/构建镜像
echo "[1/3] 构建镜像..."
docker compose -f docker-compose.prod.yml build

# 停止旧容器
echo "[2/3] 停止旧容器..."
docker compose -f docker-compose.prod.yml down 2>/dev/null || true

# 启动
echo "[3/3] 启动服务..."
docker compose -f docker-compose.prod.yml up -d

echo ""
echo "=== 部署完成 ==="
echo "查看状态: docker compose -f docker-compose.prod.yml ps"
echo "查看日志: docker compose -f docker-compose.prod.yml logs -f backend"
echo "健康检查: curl http://localhost:3000/api/v1/healthz"
