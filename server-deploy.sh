#!/bin/bash
# VisionGuard 服务器部署脚本
# 从 backend/ 直接推送到云服务器并重建 Docker
# 用法：./server-deploy.sh

set -e

SERVER="root@47.94.146.53"
SERVER_DIR="/opt/visionguard/deploy"

echo "=== VisionGuard 服务器部署 ==="
echo "源头: backend/"
echo "目标: $SERVER:$SERVER_DIR"
echo ""

# 1. 推送后端源码（排除测试/文档/无关文件）
echo "[1/3] 推送后端源码..."
rsync -avz --delete \
    --exclude='test_*' \
    --exclude='.env.example' \
    --exclude='.gitignore' \
    --exclude='*.md' \
    --exclude='tmp/' \
    backend/ "$SERVER:$SERVER_DIR/"

# 2. 确保 .env 存在
echo "[2/3] 检查 .env..."
ssh "$SERVER" "[ -f $SERVER_DIR/.env ] || cp $SERVER_DIR/.env.example $SERVER_DIR/.env 2>/dev/null || echo 'WARN: .env not found, create it manually'"

# 3. 重建 Docker
echo "[3/3] 重建 Docker..."
ssh "$SERVER" "cd $SERVER_DIR && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d --build"

echo ""
echo "=== 部署完成 ==="
echo "验证: curl http://47.94.146.53:3000/api/v1/healthz"
echo "日志: ssh $SERVER 'docker compose -f $SERVER_DIR/docker-compose.prod.yml logs -f backend'"
