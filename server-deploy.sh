#!/bin/bash
# VisionGuard 服务器部署脚本
# 1. 同步 backend/ → deploy/
# 2. 提交到 Git → 推送到 Gitee
# 3. 服务器 git pull → Docker 重建
# 用法：./server-deploy.sh

set -e

SERVER="root@47.94.146.53"
SERVER_DIR="/opt/visionguard/deploy"
REPO_DIR="/opt/visionguard/repo"

echo "=== VisionGuard 服务器部署 ==="
echo ""

# 1. 同步 backend/ → deploy/（排除测试文件）
echo "[1/5] 同步 backend/ → deploy/..."
rsync -avz --delete \
    --exclude='test_*' \
    --exclude='.env' \
    --exclude='.env.example' \
    --exclude='.gitignore' \
    --exclude='tmp/' \
    --exclude='*.md' \
    backend/ deploy/

# 2. 提交 deploy/ 到 Git
echo "[2/5] 提交到 Git..."
git add deploy/
if git diff --cached --quiet; then
    echo "  deploy/ 无变化，跳过 commit"
else
    git commit -m "deploy: 同步 backend 最新代码到部署目录"
fi

# 3. 推送到 Gitee
echo "[3/5] 推送到 Gitee..."
git push gitee master

# 4. 服务器拉取
echo "[4/5] 服务器 git pull..."
ssh "$SERVER" "cd $REPO_DIR && git pull"

# 5. 服务器重建 Docker
echo "[5/5] 服务器重建 Docker..."
ssh "$SERVER" "cd $SERVER_DIR && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d --build"

echo ""
echo "=== 部署完成 ==="
echo "验证: curl http://47.94.146.53:3000/api/v1/healthz"
echo "日志: ssh $SERVER 'docker compose -f $SERVER_DIR/docker-compose.prod.yml logs -f backend'"
