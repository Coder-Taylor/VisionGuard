# VisionGuard Web 管理后台

VisionGuard 系统 Web 管理后台，用于监护人管理老人、查看告警、定位轨迹、OCR 药品识别等。

## 技术栈

- **框架**: React 19 + TypeScript 6
- **构建**: Vite 8
- **样式**: Tailwind CSS v4
- **路由**: react-router-dom v7
- **HTTP**: axios

## 开发

```bash
npm install
npm run dev
```

开发模式下 Vite proxy 代理 `/vg/api/` 到 `http://47.94.146.53`（云服务器）。

## 构建

```bash
npm run build
```

构建输出到 `dist/` 目录，部署到 Nginx 的 `/vg/app/` 子路径下。

## 部署

```bash
# scp 推送 dist/ 到服务器
scp -r dist/* root@47.94.146.53:/srv/web/public/vg/app/

# Nginx 配置参考：
# location /vg/app/ {
#     alias /srv/web/public/vg/app/;
#     try_files $uri $uri/ /vg/app/index.html;
# }
# location /vg/api/ {
#     proxy_pass http://127.0.0.1:3000;
# }
```

## 版本

v1.5.4
