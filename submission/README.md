# VisionGuard 完整交付包

本文件夹包含 VisionGuard **四端完整源码交付**：后端 + Android + 硬件 + Web 管理后台。

---

## 文件清单

```
submission/
├── README.md                        # 本文件（部署说明）
├── 部署说明.md                       # 中文快速部署指南
│
│   # === 后端 Go 源码 ===
├── cmd/server/main.go               # 后端入口
├── internal/                        # 后端 Go 源码（72 路由 + 16 表 + 8 service）
│   ├── config/                      # .env 配置加载
│   ├── handler/                     # 8 个 HTTP 处理器
│   ├── infra/                       # PostgreSQL + Redis 连接
│   ├── middleware/                  # JWT 认证中间件
│   ├── model/                       # 16 个 GORM 模型
│   └── service/                     # 8 个业务逻辑模块
├── config/                          # 配置包
├── migrations/                      # 数据库迁移脚本
├── go.mod / go.sum                  # Go 依赖声明
├── Dockerfile                       # 多阶段构建（~20MB 镜像）
├── docker-compose.prod.yml          # 生产环境配置（PG + Redis + Backend）
├── docker-compose.yml               # 本地环境配置
├── deploy.sh                        # 一键部署脚本
├── .env.example                     # 环境变量模板
├── test_all.go                      # 21 步核心流程测试
├── test_all_full.go                 # 76 步全路由覆盖测试
├── test_e2e.sh                      # 端到端模拟（硬件→后端→APP）
├── gendevid/                        # 设备 ID 生成工具
├── uploads/                         # 文件上传目录
│
│   # === Android 监护端完整源码 ===
├── android/
│   ├── README.md                    # Android 构建说明
│   ├── build.gradle.kts             # 根构建脚本
│   ├── settings.gradle.kts          # 项目设置
│   ├── gradle.properties            # Gradle 配置
│   ├── gradlew / gradlew.bat        # Gradle Wrapper
│   ├── local.properties.example     # 本地配置模板（AMAP Key）
│   ├── gradle/                      # Gradle Wrapper 文件
│   ├── app/
│   │   ├── build.gradle.kts         # App 构建脚本
│   │   ├── proguard-rules.pro       # 混淆规则
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml
│   │       │   ├── java/.../        # Kotlin 源码（18 页面 + API + 组件）
│   │       │   └── res/             # 资源文件
│   │       ├── test/                # 单元测试
│   │       └── androidTest/         # 仪器测试
│   └── apk/
│       └── VisionGuard-v1.5.4-cloud.apk     # ★ 预构建 APK（已签名，118MB）
│
│   # === Web 管理后台（React + TypeScript） ===
├── web/
│   ├── index.html                    # 入口 HTML
│   ├── package.json                  # 依赖声明
│   ├── vite.config.ts                # Vite 构建配置 + proxy
│   ├── tsconfig.json                 # TypeScript 配置
│   └── src/
│       ├── main.tsx                  # React 入口
│       ├── App.tsx                   # 路由表（17 页）
│       ├── index.css                 # Tailwind @theme 自定义色板
│       ├── api/                      # 8 个 API 模块（对齐后端 81 路由）
│       │   ├── client.ts             # axios + JWT 拦截器 + 401 自动刷新
│       │   ├── auth.ts / elder.ts / alert.ts / device.ts
│       │   ├── ocr.ts / location.ts / notification.ts / medication.ts / user.ts
│       ├── types/index.ts            # TypeScript 类型定义
│       ├── context/AuthContext.tsx    # 登录状态管理
│       ├── components/               # 7 个通用组件
│       │   ├── Layout.tsx / BottomNav.tsx / AppButton.tsx
│       │   ├── StatusTag.tsx / EmptyState.tsx / LoadingSpinner.tsx
│       │   ├── CompactTopBar.tsx / ConfirmDialog.tsx / UnreadBadge.tsx
│       └── pages/                    # 17 个页面
│           ├── LoginPage.tsx / RegisterPage.tsx / ForgotPasswordPage.tsx
│           ├── HomePage.tsx / PositionMedicinePage.tsx
│           ├── AlertHistoryPage.tsx / AlertDetailPage.tsx
│           ├── ProfilePage.tsx / UserSettingsPage.tsx
│           ├── DeviceManagementPage.tsx
│           ├── ElderManagementPage.tsx / ElderDetailPage.tsx
│           ├── NotificationListPage.tsx
│           ├── LocationPage.tsx / MapPage.tsx
│           ├── OcrMedicinePage.tsx / MedicationPlanPage.tsx
│
│   # === 硬件固件完整源码 ===
└── hardware/
    ├── esp32/
    │   ├── esp32sense.ino           # ★ ESP32 主控固件（对齐 v1 API）
    │   └── wordmap.h                # 中文汉字 WAV 语音库映射
    └── k210/
        ├── main.py                  # ★ K210 视觉识别代码
        └── detect.kmodel            # K210 AI 障碍物检测模型（1.5MB）
```

---

## 一、前置条件

### 服务器要求

| 项目 | 最低配置 |
|------|---------|
| CPU | 2 核 |
| 内存 | 4 GB |
| 磁盘 | 20 GB |
| 系统 | Ubuntu 20.04+ / CentOS 7+ |
| 软件 | Docker 20.10+ + Docker Compose v2 |

### 安装 Docker

```bash
# Ubuntu
curl -fsSL https://get.docker.com | bash
apt install docker-compose-plugin

# 验证
docker --version
docker compose version
```

---

## 二、后端部署（3 步）

### 第 1 步：上传到服务器

在本地终端执行：

```bash
# 方式 A：rsync 推送（推荐）
cd vision-hub
rsync -avz --delete --exclude='.env' submission/ root@47.94.146.53:/opt/visionguard/

# 方式 B：scp 整个文件夹
scp -r submission/* root@47.94.146.53:/opt/visionguard/
```

### 第 2 步：配置环境变量

```bash
ssh root@47.94.146.53
cd /opt/visionguard

# 首次部署：从模板创建 .env
cp .env.example .env

# 确认关键配置（通常无需修改）
#   DB_HOST=postgres      ← Docker Compose 内部用服务名，不要改
#   REDIS_HOST=redis      ← Docker Compose 内部用服务名，不要改
#   JWT_SECRET=...        ← 已预填随机密钥
#   DB_PASSWORD=...       ← 已预填随机密码
```

> `.env.example` 中的 `DB_HOST=postgres` / `REDIS_HOST=redis` 是正确的云服务器配置。
> 这是 Docker Compose 的**内部服务名**，不是 localhost。

### 第 3 步：构建并启动

```bash
cd /opt/visionguard
chmod +x deploy.sh
./deploy.sh
```

deploy.sh 做的事情：
1. 停止旧容器
2. 重新构建镜像
3. 后台启动所有服务（PostgreSQL + Redis + Backend）
4. 等待健康检查

### 验证部署

```bash
# 健康检查
curl http://localhost:3000/api/v1/healthz
# → {"status":"ok"}

# 注册测试用户
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Test2026#","phone":"13800138000"}'

# 登录测试
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Test2026#"}'
```

---

## 三、Android APP

### 直接安装（预构建 APK）

```bash
adb install android/apk/VisionGuard-v1.5.4-cloud.apk
```

### 从源码构建

```bash
cd android/
cp local.properties.example local.properties
# 编辑 local.properties，填入 sdk.dir 和 AMAP_API_KEY

# 修改服务器地址（如需要）
# 编辑 app/src/main/java/.../api/RetrofitClient.kt
#   const val BASE_URL = "http://47.94.146.53:3000/"   // 云服务器
#   const val BASE_URL = "http://127.0.0.1:3000/"       // 本地联调

./gradlew :app:assembleRelease
# APK 输出: app/build/outputs/apk/release/app-release.apk
```

详见 `android/README.md`。

---

## 四、硬件固件烧录

### ESP32 固件

1. 安装 Arduino IDE
2. 安装 ESP32 开发板支持（`https://espressif.github.io/arduino-esp32/package_esp32_index.json`）
3. 用 Arduino IDE 打开 `hardware/esp32/esp32sense.ino`
4. 修改 WiFi 配置（约第 30 行）：
   ```cpp
   const char* ssid = "你的WiFi名";        // 必须英文 SSID，2.4GHz，WPA2
   const char* password = "你的WiFi密码";
   const char* serverUrl = "http://47.94.146.53:3000";
   ```
5. 选择开发板 `ESP32 Dev Module`，端口选择对应 COM 口
6. 点击上传

### K210 固件

1. 安装 MaixPy IDE 或使用 kflash 工具
2. 将 `hardware/k210/main.py` + `detect.kmodel` 烧录到 K210
3. K210 与 ESP32 通过串口连接（UART2，RX=44，TX=43，波特率 115200）

### 硬件 API 对接流程

设备上电后自动执行以下流程：

| 步骤 | API | 说明 |
|------|-----|------|
| 1 | `POST /api/v1/device/activate` | 设备激活，获取 deviceId + deviceSecret |
| 2 | `POST /api/v1/device/register` | 设备注册，记录 IP |
| 3 | `POST /api/v1/device/challenge` | 请求 challenge 码（XOR 0x4B） |
| 4 | `POST /api/v1/device/verify` | 提交签名，获取 JWT（24h 有效期） |
| 5 | `POST /api/v1/device/heartbeat` | 心跳上报（每 30s，带 JWT） |
| 6 | `POST /api/v1/alert` | 告警上报（fall/obstacle/sos 等） |

---

## 五、全链路验证

部署完成后，按以下步骤验证三端联通：

```bash
# 1. 后端健康检查
curl http://47.94.146.53:3000/api/v1/healthz

# 2. 硬件注册设备
# 给 ESP32 上电，观察串口日志，应看到 activate→register→challenge→verify 全部 200

# 3. Android APP
# 打开 APP → 注册账号 → 登录 → 创建设备绑定 → 首页应看到设备状态
```

---

## 六、运维命令

```bash
# 查看日志
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f postgres
docker compose -f docker-compose.prod.yml logs -f redis

# 查看容器状态
docker compose -f docker-compose.prod.yml ps

# 重启后端
docker compose -f docker-compose.prod.yml restart backend

# 停止所有服务
docker compose -f docker-compose.prod.yml down

# 更新代码后重建
docker compose -f docker-compose.prod.yml up -d --build

# 查看数据库
docker exec -it visionguard-postgres-1 psql -U visionhub -d visionhub
```

---

## 七、常见问题

### Q: 后端启动失败？
```bash
# 查看详细日志
docker compose -f docker-compose.prod.yml logs backend

# 常见原因：端口被占用
lsof -i :3000
kill -9 <PID>
```

### Q: Android APP 连不上服务器？
- 检查手机是否能访问外网
- 浏览器打开 `http://47.94.146.53:3000/api/v1/healthz` 看是否返回 `{"status":"ok"}`
- 检查服务器防火墙是否开放 3000 端口

### Q: 硬件连不上后端？
- 确认 WiFi 名称是英文（ESP32 不支持中文 SSID）
- 确认是 2.4GHz（不支持 5GHz）
- 确认加密方式是 WPA2-Personal
- 确认 serverUrl 是 `http://47.94.146.53:3000`

### Q: Docker 容器内无法连接外部？
```bash
# 检查 Docker 网络
docker network ls
docker network inspect visionguard_default
```

---

## 八、环境对照

| 配置项 | 本地开发 | 云服务器 |
|--------|---------|---------|
| 后端地址 | `http://localhost:3000` | `http://47.94.146.53:3000` |
| DB_HOST | `localhost` | `postgres`（Docker 服务名） |
| REDIS_HOST | `localhost` | `redis`（Docker 服务名） |
| Android BASE_URL | `127.0.0.1:3000` | `47.94.146.53:3000` |
| 硬件 serverUrl | 电脑 IP | `47.94.146.53:3000` |
| 部署方式 | 原生安装 PG + Redis | Docker Compose |
| DB_USER | `postgres` | `visionhub` |

---

## 版本信息

- **版本**：v1.5.4
- **日期**：2026-05-11
- **后端**：Go 1.23 + Fiber v2 + GORM + PostgreSQL 16 + Redis 7
- **Android**：Kotlin + Jetpack Compose + Material 3 + 高德地图 SDK 10.0.600
- **Web**：React 18 + TypeScript + Vite 5 + Tailwind CSS 4 + React Router v7
- **硬件**：ESP32 (Arduino) + K210 (MicroPython)
- **路由**：81 条 HTTP 接口（含用药计划管理）
- **数据表**：17 张
