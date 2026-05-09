#!/bin/bash
# VisionGuard 服务器部署脚本（Windows 兼容版）
# 1. 同步 backend/ → deploy/（cp 本地同步）
# 2. scp deploy/ → 服务器
# 3. 服务器 Docker 重建
# 4. 清理服务器源码（保留 Dockerfile/compose/.env/uploads）
# 用法：bash server-deploy.sh

set -e

SERVER="root@47.94.146.53"
SERVER_DIR="/opt/visionguard/deploy"

echo "=== VisionGuard 服务器部署 ==="
echo ""

# 1. 本地同步 backend/ → deploy/
echo "[1/4] 同步 backend/ → deploy/..."
for dir in cmd internal config migrations gendevid; do
    if [ -d "backend/$dir" ]; then
        rm -rf "deploy/$dir"
        cp -r "backend/$dir" "deploy/$dir"
        echo "  cp -r backend/$dir → deploy/$dir"
    fi
done
cp backend/go.mod backend/go.sum deploy/ 2>/dev/null || true
cp backend/Dockerfile backend/docker-compose.yml backend/docker-compose.prod.yml deploy/ 2>/dev/null || true
echo "  本地同步完成"

# 2. 推送 deploy/ 到服务器
echo "[2/4] 推送 deploy/ → $SERVER:$SERVER_DIR..."
ssh "$SERVER" "mkdir -p $SERVER_DIR"
scp -r deploy/cmd deploy/internal deploy/config deploy/migrations deploy/gendevid \
       deploy/go.mod deploy/go.sum \
       deploy/Dockerfile deploy/docker-compose.yml deploy/docker-compose.prod.yml \
       "$SERVER:$SERVER_DIR/"
echo "  scp 推送完成"

# 3. 服务器重建 Docker
echo "[3/4] 服务器重建 Docker..."
ssh "$SERVER" "cd $SERVER_DIR && docker compose -f docker-compose.prod.yml up -d --build"
echo "  Docker 重建完成"

# 4. 清理服务器源码（保留 Docker 配置 + 上传文件）
echo "[4/4] 清理服务器源码..."
ssh "$SERVER" "cd $SERVER_DIR && rm -rf cmd internal config migrations gendevid go.mod go.sum"
echo "  源码已清理（保留 Dockerfile compose .env uploads deploy.sh）"

echo ""
echo "=== 部署完成 ==="
echo "验证: curl http://47.94.146.53/vg/api/v1/healthz"
echo "日志: ssh $SERVER 'docker compose -f $SERVER_DIR/docker-compose.prod.yml logs -f backend'"
