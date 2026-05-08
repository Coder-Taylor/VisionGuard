#!/bin/bash
# VisionGuard 服务器部署脚本
# 1. 同步 backend/ → deploy/
# 2. rsync deploy/ → 服务器
# 3. 服务器 Docker 重建
# 用法：./server-deploy.sh

set -e

SERVER="root@47.94.146.53"
SERVER_DIR="/opt/visionguard/deploy"

echo "=== VisionGuard 服务器部署 ==="
echo ""

# 1. 本地同步 backend/ → deploy/
echo "[1/3] 同步 backend/ → deploy/..."
rsync -a --delete \
    --exclude='test_*' \
    --exclude='.env' \
    --exclude='.env.example' \
    --exclude='.gitignore' \
    --exclude='tmp/' \
    backend/ deploy/

# 2. 推送 deploy/ 到服务器
echo "[2/3] 推送 deploy/ → $SERVER:$SERVER_DIR..."
rsync -avz --delete \
    --exclude='.env' \
    --exclude='.env.example' \
    --exclude='tmp/' \
    deploy/ "$SERVER:$SERVER_DIR/"

# 3. 服务器重建 Docker
echo "[3/3] 服务器重建 Docker..."
ssh "$SERVER" "cd $SERVER_DIR && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d --build"

echo ""
echo "=== 部署完成 ==="
echo "验证: curl http://47.94.146.53/vg/api/v1/healthz"
echo "日志: ssh $SERVER 'docker compose -f $SERVER_DIR/docker-compose.prod.yml logs -f backend'"
