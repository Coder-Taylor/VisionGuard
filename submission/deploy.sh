#!/bin/bash
# VisionGuard 服务器部署脚本
# 功能：将 submission/ 目录的后端文件推送到云服务器并重建 Docker
# 用法：./deploy.sh
# 前提：已配置 SSH 免密登录 root@47.94.146.53
# 注意：Windows 用户请使用 Git Bash 或 WSL 运行此脚本

set -e

SERVER="root@47.94.146.53"
SERVER_DIR="/opt/visionguard/deploy"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== VisionGuard 云端部署 ==="
echo "服务器: $SERVER"
echo "部署目录: $SERVER_DIR"
echo ""

# 1. 准备服务器目录
echo "[1/4] 准备服务器目录..."
ssh "$SERVER" "mkdir -p $SERVER_DIR"

# 2. 只推送后端文件（排除 Android/硬件/测试/文档）
echo "[2/4] 推送后端文件到服务器..."
# 使用 scp 递归复制（Windows 兼容，无需安装 rsync）
rsync_available=false
if command -v rsync &>/dev/null; then
    rsync_available=true
fi

if [ "$rsync_available" = true ]; then
    rsync -avz --delete \
        --exclude='android/' \
        --exclude='hardware/' \
        --exclude='*.md' \
        --exclude='test_*' \
        --exclude='gendevid/' \
        --exclude='.git' \
        --exclude='.gitignore' \
        --exclude='.env.example' \
        --exclude='node_modules/' \
        --exclude='web/' \
        "$SCRIPT_DIR/" "$SERVER:$SERVER_DIR/"
else
    # fallback: 使用 scp（不支持 --delete）
    echo "rsync 未安装，使用 scp（Windows 兼容模式）..."
    ssh "$SERVER" "rm -rf $SERVER_DIR/*"
    scp -r "$SCRIPT_DIR/cmd" "$SCRIPT_DIR/internal" "$SCRIPT_DIR/config" \
        "$SCRIPT_DIR/go.mod" "$SCRIPT_DIR/go.sum" "$SCRIPT_DIR/Dockerfile" \
        "$SCRIPT_DIR/docker-compose.yml" "$SCRIPT_DIR/docker-compose.prod.yml" \
        "$SERVER:$SERVER_DIR/"
fi

# 3. 复制 .env（如果服务器上没有）
echo "[3/4] 检查 .env..."
ssh "$SERVER" "[ -f $SERVER_DIR/.env ] || cp $SERVER_DIR/.env.example $SERVER_DIR/.env"

# 4. 构建并重启
echo "[4/4] 重建 Docker 容器..."
ssh "$SERVER" "cd $SERVER_DIR && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d --build"

echo ""
echo "=== 部署完成 ==="
echo "健康检查: curl http://47.94.146.53/vg/api/v1/healthz"
echo "查看日志: ssh $SERVER 'docker compose -f $SERVER_DIR/docker-compose.prod.yml logs -f backend'"
