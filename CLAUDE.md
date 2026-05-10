# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

> **💡 硬件对接**：先读 `docs/硬件对接文档.md`，硬件最新代码在 `hardware/esp32/esp32sense.ino` + `hardware/k210/main.py`。
> **💡 Android 开发**：先读 `docs/业务流程与后端设计.md`（接口+认证+DB），然后看 `docs/Android-UI设计文档.md`（UI 规范+页面结构）。
> **💡 Web 开发**：先读 `submission/web/README.md`（React + TypeScript + Vite + Tailwind CSS），网页版仅存在于 submission/。
> **💡 后端开发**：继续往下读。

## User profile

- **后端新手**，有 Java 基础，Go 刚接触
- 开发环境：Windows 11，GoLand IDE，Go 安装在 `C:\Develop\Go\`
- 比赛中项目（计算机设计大赛），校赛 2026-04-14，省赛待定
- 用中文交流，技术解释给代码+Java 类比

## ⚠️ 重要规则（每次改动必须遵守）

### 文档语言铁律

**所有文档必须用中文写**（README、CLAUDE.md、docs/、变更记录、开发日志、部署指南、TODO、commit message）。

### 版本号规则

格式 `主版本.次版本.修订号`（如 `1.5.0`），适用范围：后端 + Android + Web（硬件独立管理）。

| 位 | 名称 | 何时 +1 | 复位 |
|----|------|---------|------|
| 第一位 | 主版本 | 次版本满 9 后仍需加功能；或重大架构变更 | 次版本→0，修订号→0 |
| 第二位 | 次版本 | 新功能/新模块/新页面上线 | 修订号→0 |
| 第三位 | 修订号 | Bug 修复、文案修正、小调整 | 无 |

> **当前版本**：v1.5.0
>
> **同步铁律**：改版本号必须同时更新：
> 1. `submission/android/app/build.gradle.kts` 和 `app/build.gradle.kts` 的 `versionCode`（+1）和 `versionName`
> 2. `submission/README.md` 和 `README.md` 底部版本行
> 3. APK 文件名 `VisionGuard-vX.Y.Z-cloud.apk` / `VisionGuard-vX.Y.Z-local.apk`
> 4. `docs/变更记录.md` 新增版本条目

### 四端版本架构

| 版本 | 本地文件夹 | 内容 | BASE_URL |
|------|-----------|------|----------|
| **本地测试版** | `app/` + `backend/` | 日常开发调试 | `http://127.0.0.1:3000/` |
| **提交评委版** | `submission/` | 四端完整源码+APK+Web | `http://47.94.146.53/vg/` |
| **云端部署版** | `deploy/` | 纯后端 + Docker（rsync 推送） | `http://47.94.146.53/vg/` |
| **Web 网页版** | `submission/web/` | React 管理后台（仅存 submission） | — |

> **代码流向**：`backend/` 是后端唯一源头 → `./server-deploy.sh` 同步到 `deploy/` → rsync 推送服务器 → Docker 重建。
> **Web 部署**：`submission/web/` → `npm run build` → scp 推送 Nginx（无需 Docker）。

### Android 双版本 BASE_URL 铁律

| 版本 | RetrofitClient.kt 位置 | BASE_URL |
|------|------------------------|----------|
| **本地测试版** | `app/.../RetrofitClient.kt` | `http://127.0.0.1:3000/` |
| **提交评委版** | `submission/android/.../RetrofitClient.kt` | `http://47.94.146.53/vg/` |

> 每次修改 Android 代码必须同时更新两份源码，确认 submission 的 BASE_URL 没被覆盖。默认装云版 APK。

### 同步检查清单

- [ ] 后端改动 → `./server-deploy.sh` 同步 deploy + 推送
- [ ] 后端改动 → 同步 `submission/internal/`
- [ ] Android 改动 → 同步 `submission/android/`，确认 BASE_URL 仍是云地址
- [ ] 硬件改动 → `submission/hardware/` ↔ `hardware/` 双向同步
- [ ] `submission/android/app/build.gradle.kts` minSdk = 31
- [ ] Web 改动 → 仅 `submission/web/`（不需同步到 backend/deploy）

### Web 网页版规则

仅存在于 `submission/web/`（React + TypeScript + Vite + Tailwind CSS），不在 backend/deploy 中。

```bash
cd submission/web
npm install && npm run dev      # 开发（Vite proxy → 47.94.146.53/vg）
npm run build                    # 生产构建 → dist/
# 部署：scp -r dist/* root@47.94.146.53:<部署路径>/
```

### 服务器部署

```bash
./server-deploy.sh    # 一键：backend → deploy → rsync → Docker 重建
```

服务器：阿里云 47.94.146.53（Ubuntu 22.04，2C2G，北京）。Nginx :80 统一入口，`/vg/api/` → :3000 VisionGuard API。

### 代码推送铁律

```bash
git add -A -- ':!submission/android/app/build' ':!submission/android/.gradle'
git commit -m "feat/fix/docs: 描述"
git push gitee master
```

每轮对话结束前若有代码改动必须 commit + push （仓库：https://gitee.com/taylorchengitee/vision-guard），中文 commit message。

### Docker 中国网络铁律

1. **任何 Dockerfile 必须在 `COPY go.mod` 前加 `ENV GOPROXY=https://goproxy.cn,direct`**（国内无法访问 proxy.golang.org，已两次踩坑）。影响的文件：`backend/Dockerfile`、`submission/Dockerfile`。
2. **docker-compose 只包含后端实际依赖**：`postgres:16-alpine` + `redis:7-alpine`。禁止添加未使用的第三方镜像（国内无法拉取 Docker Hub）。影响的文件：4 个 compose 文件保持一致。

---

## 项目概述

VisionGuard — 面向视障与老年群体的胸挂式智能设备系统。三端架构：硬件（ESP32 + K210，本地感知/计算/告警，离线可用）+ 云端（Go Fiber，设备认证/数据中转/OCR/告警推送）+ 监护端（Android APP，18 页面）。核心原则：**硬件主导、本地优先**。

## 仓库布局

```
vision-hub/
├── app/                 # Android APP (Kotlin + Compose, 18 页面)
├── backend/             # Go 云端服务（后端唯一源头）
│   ├── cmd/server/main.go
│   ├── internal/{config,infra,model,handler,service,middleware}/
│   ├── docker-compose.yml / docker-compose.prod.yml / Dockerfile
│   └── .env.example
├── deploy/              # 云端部署版（server-deploy.sh 自动同步）
├── submission/          # 评委提交版（四端源码+APK+Web+Docker）
├── hardware/            # 硬件固件（esp32/esp32sense.ino + k210/main.py）
├── docs/                # 12 份文档
├── server-deploy.sh     # 一键部署脚本
└── CLAUDE.md
```

## 后端开发

### 技术栈与命令

Go 1.23+, Fiber v2, GORM, PostgreSQL 16, Redis 7, JWT HS256, bcrypt, XOR 0x4B challenge-response。

```bash
cd backend
go mod tidy && go build ./...
go run cmd/server/main.go       # 启动（需 PostgreSQL + Redis）
go run test_all_full.go         # 76 步全路由测试
bash test_e2e.sh                # 端到端模拟（curl）
python3 test_ocr.py             # OCR 全链路测试
```

### 架构原则

1. 严格对齐 `docs/业务流程与后端设计.md` 的接口路径、参数、响应格式
2. 心跳 deviceId 从 JWT 取，不信任 body；DeviceInfoRegister 用 `c.IP()`
3. 所有 handler 类型断言用 comma-ok 模式，失败返回 401
4. PasswordHash / DeviceCode `json:"-"` 不序列化
5. Token 翻转：先创建新 RefreshToken 再删除旧的
6. 离线检测：每 10s 扫描 → 90s 无心跳 → 标记 offline + 通知
7. **所有硬件上传接口必须确保 elder_id 正确填充**（同类 bug 已两次：告警 ListAlerts + OCR ListRecords 都因 elder_id 为空导致 Android 查询返回 0 条）

### 业务模块概览（81 路由，17 表，11 模块）

> 完整路由表 + 数据模型详见 `docs/业务流程与后端设计.md`

| 模块 | 路由数 | 核心功能 |
|------|--------|---------|
| 认证服务 | 8 | challenge/verify/register/login/refresh/logout |
| 老人档案与监护 | 15 | CRUD/监护人邀请转让/仪表盘 |
| 设备接入与安全 | 6 | 激活/认证/固件/数据上报 |
| 设备心跳与在线 | 5 | 心跳(30s)/状态/离线检测 |
| 设备绑定与解绑 | 7 | 搜索/发起/确认/解绑/换绑 |
| 设备数据接收 | 2 | 健康数据接收+查询 |
| 告警事件管理 | 8 | 7种类型/去重/状态流转/统计 |
| 定位与围栏 | 7 | 最新位置/轨迹/围栏CRUD |
| 药品识别(OCR) | 7 | 上传/豆包识别/轮询/建议 |
| 消息推送 | 8 | 通知列表/已读/推送规则 |
| 用药计划 | 新增 | 药品/剂量/频次/提醒 |

### 关键业务流程

1. **设备认证**：activate → register → challenge → verify（XOR 0x4B）→ JWT 24h
2. **设备绑定**：搜索 → 发起绑定 → 设备确认（5min 超时）
3. **告警全链路**：硬件检测 → POST /alert（去重窗口）→ 通知监护人 → APP 轮询
4. **OCR 流程**：上传图片 → 豆包异步识别 → 硬件/APP 轮询结果
5. **离线检测**：10s 扫描 → 90s 无心跳 → offline + 通知

### 安全清单

- [x] XOR 0x4B challenge-response + bcrypt + JWT HS256（设备 24h / 用户 1h）
- [x] RefreshToken 翻转 + 登录失败锁定（8次/30min）
- [x] 心跳 deviceId 从 JWT 取，IP 用 c.IP()，敏感字段 json:"-"
- [x] 监护人邀请身份验证 + 主监护人转让双向确认 + 绑定唯一约束

### 已知局限

- 实时推送为轮询模式（非 FCM/WebSocket）
- 固件升级/推送发送为 stub，无单元测试

---

## Android 开发

Kotlin + Jetpack Compose + Material 3, minSdk 35, 18 页面。

```bash
cd app
./gradlew assembleDebug
./gradlew assembleRelease    # 签名 APK（本地版 → apk/；云版 → submission/android/apk/）
```

### 真机联调

手机热点模式下 APP 无法访问热点下设备 → ADB 反向隧道：

```bash
adb reverse tcp:3000 tcp:3000    # 每次拔插 USB 需重做
```

---

## 硬件

固件位于 `hardware/`（ESP32 `esp32sense.ino` + K210 `main.py` + `detect.kmodel`），已对齐后端 v1 API（activate→register→challenge→verify→heartbeat→alert + OCR 上传/轮询）。ESP32 与 K210 通过 UART2（115200）串口通信。详细对接指南见 `docs/硬件对接文档.md`。

---

## 端口/环境对照

| | 本地开发 | 云服务器 |
|------|------|------|
| 用户/APP 访问 | `http://localhost:3000` | `http://47.94.146.53/vg/` |
| 后端实际端口 | 3000 | 3000（仅 127.0.0.1，Nginx 代理） |
| DB_HOST | `localhost` | `postgres`（Docker 服务名） |
| REDIS_HOST | `localhost` | `redis`（Docker 服务名） |

---

## 文档索引（docs/，共 12 份）

| 文件 | 给谁 | 内容 |
|------|------|------|
| `硬件对接文档.md` | 硬件团队 | ESP32 对接指南 + curl 脚本 + 排查 |
| `业务流程与后端设计.md` | 学长/Android | 81路由+17表+业务流+安全+部署 |
| `产品需求说明书.md` | 全团队 | 完整产品需求 |
| `Android-UI设计文档.md` | Android | 设计规范+页面结构+接口对照 |
| `安卓说明文档.md` | 全团队 | 三端业务边界+P0/P1 功能模块 |
| `部署指南.md` | 全团队 | 生产部署全流程 |
| `代码审查清单.md` | 后端 | 5阶段61检查点 Review |
| `开发日志.md` | 后端 | 完整开发记录 |
| `变更记录.md` | 全团队 | 版本变更 |
| `数据流模拟.md` | 全团队 | 端到端数据流 |
| `api.md` | 硬件（存档） | 旧版接口文档 |
| `业务设计 (1).md` | 学长原始 | 原始设计文档（存档） |
