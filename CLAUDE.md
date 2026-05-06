# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

> **💡 如果你的任务是硬件对接**：请先读 `docs/硬件对接文档.md`，然后参考 `hardware/README.md` 了解硬件架构。硬件团队最新代码在 `hardware/esp32/esp32sense.ino` + `hardware/k210/main.py`。
> 
> **💡 如果你的任务是 Android 开发**：请先读 `docs/业务流程与后端设计.md`（全部接口+认证+DB），然后看 `docs/Android-UI设计文档.md`（UI 设计规范+页面结构）。
>
> **💡 如果你的任务是后端开发**：继续往下读。

## User profile

- **后端新手**，有 Java 基础，Go 刚接触
- 开发环境：Windows 11，GoLand IDE，Go 安装在 `C:\Develop\Go\`
- 比赛中项目（计算机设计大赛），校赛 2026-04-14，省赛待定
- 用中文交流，技术解释给代码+Java 类比

## ⚠️ 重要规则（每次改动必须遵守）

### 双版本 BASE_URL 铁律

项目存在**两份源码**，BASE_URL **绝对不能混淆**：

| 版本 | 路径 | BASE_URL |
|------|------|----------|
| **本地开发版** | `app/`（项目根目录） | `http://127.0.0.1:3000/` |
| **云版/分发版** | `cloud-deploy/android/` | `http://47.94.146.53:3000/` |

> **规则**：
> 1. 每次修改 Android 代码，**必须同时更新两份源码**
> 2. 本地版 `RetrofitClient.kt` 永远用 `127.0.0.1:3000`，云版永远用 `47.94.146.53:3000`
> 3. sync 文件时**先确认 cloud-deploy 的 BASE_URL 没被覆盖**，如果被覆盖了立即改回来
> 4. 构建分发 APK 必须从 `cloud-deploy/android/` 构建，构建本地测试 APK 从根目录 `app/` 构建
> 5. **默认装云版**：没有特殊说明时，安装到手机的 APK 必须是云版（`cloud-deploy/android/` 构建），BASE_URL 指向 `47.94.146.53:3000`

### 同步检查清单

每次改动后同步到 cloud-deploy 时，**必须验证**：
- [ ] `cloud-deploy/android/.../RetrofitClient.kt` BASE_URL 仍是云地址
- [ ] `cloud-deploy/android/app/build.gradle.kts` minSdk = 31
- [ ] 新增文件已在 cloud-deploy 中存在
- [ ] 后端 Go 文件已同步（如果改了后端）

### Gitee 推送铁律

**代码仓库**：https://gitee.com/taylorchengitee/vision-guard

**每次代码修改完成后必须执行**：

```bash
# 1. 提交
git add -A -- ':!cloud-deploy/android/app/build' ':!cloud-deploy/android/.gradle'
git commit -m "feat/fix: 描述"

# 2. 推送（同步到 GitHub + Gitee）
git push origin master
git push gitee master
```

> 规则：
> 1. 每轮对话结束前，若有代码改动，必须 commit + push 到 Gitee
> 2. git add 时排除 cloud-deploy 的 build 产物（Windows 文件名过长限制）
> 3. 提交信息用中文，格式：`feat: xxx` / `fix: xxx` / `docs: xxx`

### Docker 中国网络铁律

**规则 1：任何 Dockerfile 必须在 `COPY go.mod` 前加 `ENV GOPROXY=https://goproxy.cn,direct`**。

> **原因**：国内服务器（阿里云等）无法访问 `proxy.golang.org`，go mod download 必定超时。已两次踩坑（2026-05-05、2026-05-06）。
>
> **正确写法**：
> ```dockerfile
> FROM golang:1.23-alpine AS builder
> WORKDIR /app
> ENV GOPROXY=https://goproxy.cn,direct
> COPY go.mod go.sum ./
> RUN go mod download
> ```
>
> **影响的文件**：`backend/Dockerfile`、`cloud-deploy/Dockerfile`（两个都要改，保持一致）

**规则 2：docker-compose 只包含后端实际依赖的服务。禁止添加未使用的第三方镜像**。

> **原因**：国内服务器无法访问 Docker Hub（`docker.io`、`docker.redpanda.com` 等），任何非 alpine 官方镜像拉取必定超时。
>
> **已移除**：Redpanda（Kafka 替代品，808MB）— 后端代码不使用，预留实时推送用。实时推送功能上线后再考虑国内可拉取的替代方案。
>
> **当前可用服务（均为轻量 alpine 版）**：
> - `postgres:16-alpine` ✅
> - `redis:7-alpine` ✅
>
> **影响的文件**：`cloud-deploy/docker-compose.yml`、`cloud-deploy/docker-compose.prod.yml`、`backend/docker-compose.yml`、`backend/docker-compose.prod.yml`（四个都要保持一致）

---

## Session state (2026-05-06 傍晚)

### 项目信息

- **项目名**：VisionGuard
- **生产服务器**：`http://47.94.146.53:3000/`
- **本地开发**：`http://localhost:3000/`
- **端口**：统一 3000
- **测试账号**（云服务器）：
  - 用户名 `VisionGuard` / 手机号 `13322701148` / 密码 `VisionGuard2026#`
  - 用户名 `ocrtest` / 密码 `test123456`（后端开发测试用）
- **豆包 API Key**：`ark-632ca022-46e7-4e0d-ae10-fc2cd9e1a2fa-21961`（已写入 .env）
- **硬件 WiFi**：SSID `wuiPhone 16`，密码 `12345ssDLH`
- **Gitee 仓库**：https://gitee.com/taylorchengitee/vision-guard
- **硬件团队最新代码**：`hardware/esp32/esp32sense.ino`（已对齐 v1 API）+ `hardware/k210/main.py`

### 已完成的工作

| 谁做的 | 做了什么 |
|--------|---------|
| AI | 基于 `docs/UI11.DOCX` 重写 Android UI 层：全局设计规范（#165DFF 蓝 + 16dp）+ 4 Tab 导航 + 告警总览首页 + 个人中心 + 5 轮自审 |
| AI | Android 全局组件封装：AppButton(主/次/危险) + AppConfirmDialog + EmptyState + UnreadBadge(呼吸动效) + StatusTag |
| AI | 6 个页面重写/新增：HomeScreen(安全状态+新告警卡片+历史列表) + AlertDetailScreen(时间线) + ProfileScreen + DeviceManagementScreen + UserSettingsScreen + AlertHistoryListScreen |
| AI | 11 项 UI 修复：密码可见切换 + 详细错误提示(ErrorHelper) + 登录响应格式对齐后端(flat JSON) + 闪退修复(rememberSaveable→remember) + 首页重构(设备状态仪表盘) + 子页面隐藏底部导航 + DOCX配色应用(CardBackground/SoftPink/SoftGreen/SoftOrange) + 动态昵称(AuthTokenHolder) + 假演示设备 + 手机号动态显示 + 密码验证表单 |
| AI | PositionMedicineScreen 美化：图标圆形容器 + 功能标签 + CardBackground 配色 |
| AI | 图标设计提示词（中英双语，含设计要点表） |
| AI | API 字段对齐后端：alert_type 7 种 / alert_level 4 级 / timeline 字段 at-action-by |
| AI | 兼容性加固：useLegacyPackaging 16KB 对齐 + JDK 25 + Gradle 9.1.0 + AGP 9.0.1 构建通过 |
| AI | `docs/业务设计(1).md` 从零重写后端：74 路由 + 16 表 + 8 service + 8 handler |
| AI | 10 大业务模块全覆盖：认证/老人档案/设备注册/心跳/绑定/数据存储/告警/定位/OCR/通知 |
| AI | XOR 0x4B challenge-response 认证 + bcrypt 密码哈希 + JWT HS256（设备24h/用户1h） |
| AI | 4 轮代码审查修复 40+ bug（登录失败计数/密码复杂度/Token 翻转/离线检测/绑定权限等） |
| AI | `docs/代码审查清单.md` — 5 阶段 61 检查点手动 Review 清单 |
| AI | `backend/test_all_full.go` — 76 步全路由覆盖测试脚本，全部 PASS |
| AI | `docs/硬件对接文档.md` — 硬件 ESP32 对接指南（含本地测试指南+curl完整脚本+常见问题） |
| AI | `docs/业务流程与后端设计.md` — 给学长/Android 的完整交付文档（74路由+业务流+DB+安全+部署） |
| AI | docs/ 下 5 个文档统一改为中文命名 |
| AI | README.md — 15 章完整项目文档（含目录跳转、测试指南、本地/云端区分） |
| AI | `.env.example` JWT_SECRET + DB_PASSWORD 改为随机生成值 |
| AI | 全局项目改名 Vision Hub → VisionGuard，端口统一 3000 |
| 你 | PostgreSQL 16 + Redis 本地直装（端口 5432 / 6379） |
| 你 | backend/.env 创建，go mod tidy 完成，后端启动成功 |
| AI | **后端**：User 模型新增 DisplayName；LoginResponse 返回 display_name + phone；登录支持用户名或手机号；ChangePassword + GetProfile + UpdateProfile 接口 + handler |
| AI | **Android 手机号登录**：LoginScreen/RegisterScreen 提取后端 display_name + phone 并持久化；4-param 回调链适配 |
| AI | **Android 修改密码**：UserSettingsScreen 接 POST /api/v1/auth/change-password 真实 API |
| AI | **Android 编辑昵称**：ProfileScreen 新增 AlertDialog + PUT /api/v1/user/profile |
| AI | **Android 设备绑定增强**：DeviceManagementScreen 新增"从相册选择二维码"（ZXing + gallery picker） |
| AI | **Android 老人管理**：新增 ElderManagementScreen（老人列表+创建档案弹窗），入口在个人中心 |
| AI | **Android APK 分发**：assembleRelease 签名打包 → VisionGuard-v1.0.apk（103MB），位于项目根目录 |
| AI | **Android 修改密码增强**：密码可见切换(小眼睛) + 最小8位(对齐后端) + 后端错误消息透传 + 手机号换绑 |
| AI | **Android 老人档案增强**：DatePickerDialog(阳历/农历切换+农历→公历转换) + 血型下拉选择 + 删除档案(确认弹窗) |
| AI | **Android 设备绑定增强**：三种方式统一入口(扫码/相册/手动) + 设备解绑按钮 + 后端返回deviceId |
| AI | **DisplayName 空字符串修复**：后端新字段空字符串→ `takeIf { isNotBlank() }` 兜底用户名 |
| AI | **后端 UpdateProfile 扩展**：支持更新 phone 字段 + elder list 返回 deviceId |
| AI | **Android 老人详情**：ElderDetailScreen（查看+编辑+监护人列表+设备状态） |
| AI | **Android 消息通知**：NotificationListScreen（通知列表+全部已读） |
| AI | **Android 告警操作**：AlertDetailScreen 确认/解决/关闭按钮 + 状态流转 |
| AI | **Android 定位页面**：LocationScreen（最新位置+24h轨迹，接 GET /api/v1/location/*） |
| AI | **Android 用药识别页**：OcrMedicineScreen（OCR 记录列表，接 GET /api/v1/ocr/records） |
| AI | **Android 定位/用药卡片可点击**：PositionMedicineScreen 两张卡片加导航回调 |
| AI | **Android 高德地图集成**：MapScreen（TextureMapView+Compose互操作）+ 设备位置标记（蓝点）+ 告警标记（红点）+ 24h轨迹（虚线）+ 老人选择器 + 图例 |
| AI | **修复监护人列表**：GuardianInfo.displayName → nickname（与后端 JSON 字段对齐） |
| AI | **通知栏修复**：VisionHubService 通知去"智能胸牌已连接"+ PendingIntent 回应用 |
| AI | **农历算法重写**：修正 solar↔lunar 双向转换（基于 1900-01-31 正月初一基准）+ 天干地支 |
| AI | **下拉框修复**：ExposedDropdownMenuBox→透明 Box overlay 模式 |
| AI | **Z​Xing 扫码竖屏**：CaptureActivity portrait + 蓝色扫描线主题 |
| AI | **高德地图 Key 配置**：申请并配置 AMAP_API_KEY=d8fe...，local.properties 已更新 |
| 硬件团队 | **新固件**：`hardware/esp32/esp32sense.ino` 已对齐 v1 API（activate→register→challenge→verify→heartbeat→alert）+ `k210/main.py` + `detect.kmodel` |
| AI | **Android 网络连通修复 (2026-05-05)**：发现 Android 热点模式下手机自身 APP 无法访问热点下挂设备（wlan2 接口流量走 rmnet 蜂窝网而非热点），ping 10.26.43.176 100% 丢包。通过 `adb reverse tcp:3000 tcp:3000` 反向隧道解决，Android BASE_URL 临时改为 `127.0.0.1:3000` |
| 硬件同学 | **ESP32 WiFi 兼容性修复 (2026-05-05)**：中文 SSID「荣耀400」→ 「Honor400」（ESP32 编码问题），确认必须 WPA2-Personal + 2.4GHz。固件 `hardware/esp32/esp32sense.ino` 已更新（含详细串口调试日志） |
| AI | **Android Token 自动续期 (2026-05-05)**：401 修复 — `AuthTokenHolder` 新增 refreshToken，`AuthPreference` 增加 refresh 存取，`RetrofitClient` 增加 `TokenAuthenticator`（OkHttp Authenticator 拦截 401→refresh API→新 token→重试），`RefreshReq`/`LogoutReq` 加 `@SerializedName("refresh_token")`，`AuthApi.refresh()` 返回类型修正 |
| AI | **绑定流程修复 (2026-05-05)**：canBind 允许 offline 设备（不限于 registered/online）；MVP 自动确认绑定（跳过 ESP32 未实现的 confirm 步骤）；`BindInitiateResp` 增加 elderId/boundAt 字段；Android `DeviceManagementScreen` 增加 refreshTrigger 机制（绑定成功后刷新真实数据） |
| AI | **首页仪表盘修复 (2026-05-05)**：Dashboard API 增加 `elderCount`/`onlineDeviceCount`/`alertCount24h` 汇总字段（之前只返回 total+elders 列表，导致 Android 首页显示 0） |
| AI | **ESP32 固件 WiFi 断连 bug 定位 (2026-05-05)**：WiFi 断后 `currentStatus` 卡在 DEVICE_ONLINE，`[心跳]` 不检查 `wifiOnline` 导致虚假输出，且无法自动重连。需硬件同学修复 loop 中 WiFi 状态检测逻辑 |
| AI | **老人详情设备状态修复 (2026-05-05)**：`ElderDetailResp` 新增 `deviceOnline`/`deviceId`（查 bindings 表）+ `GuardianInfo` 新增 `phone`（查 users 表） |
| AI | **首页状态修复 (2026-05-05)**：`hasDevice` 改为只看 `onlineDeviceCount`，不再因有老人无设备就显示"设备运行正常" |
| AI | **告警接口 ESP32 兼容 (2026-05-05)**：`AlertCreateReq` 兼容 ESP32 固件嵌套 `location:{lat,lng}` + `sensorData` object 格式。回退方法：删 `Location *struct` / `SensorData interface{}` / CreateAlert 兼容块即可恢复标准格式 |
| AI | **全局下拉刷新 (2026-05-06)**：7 页面统一添加 pull-to-refresh — HomeScreen / AlertHistoryList / NotificationList / ElderManagement / DeviceManagement / Location / OcrMedicine，含 400ms 最小动画时长 + PullRefreshIndicator，依赖 `androidx.compose.material:material` |
| AI | **告警列表空数据修复 (2026-05-06)**：根因 3 个 — ① 后端 `ListAlerts` 不传 `elderId` 时 `WHERE elder_id = ''` 永远返回空（修复：按用户 elders 过滤）；② 后端响应字段 `records`→`list`（Android 期望 `list`）；③ `AlertSummary` 缺少 `deviceId`/`elderId`/`description`。同时修复 Android `@Query("type")`→`@Query("alertType")` |
| AI | **定位不显示修复 (2026-05-06)**：后端 `GetLatestLocation` 返回嵌套 `{location:{lat,lng}}` → 改为扁平 `{lat,lng,createdAt,...}`；`GetTrajectory` 返回 `{totalPoints,trajectory}` → 改为 `{total,page,pageSize,list}` 匹配 Android `PaginatedData<LocationData>` |
| AI | **注册 bug 修复 (2026-05-06)**：`Register()` 的 `WHERE username=? OR email=? OR phone=?` 中 `email=''` 匹配所有邮箱为空的已有用户，导致无法注册新号。修复：仅非空字段才加 OR 条件 |
| AI | **README 文档同学板块 (2026-05-06)**：新增 §四"如果你是文档同学" — 阅读路线、项目三句话、关键数字、技术栈速览表、三端通信流程图、文档撰写常用参考 |
| AI | **HomeScreen 告警操作修复 (2026-05-06)**：① `isAlert` 改为 `pendingAlerts.isNotEmpty()`（忽视后状态栏变绿）；② 忽视时同步从 `recentAlerts` 移除 + 递减 `alertCount24h`；③ 新增"一键忽视"按钮（`pendingAlerts.size > 1` 时显示，批量确认全部待处理告警） |
| AI | **AlertDetailScreen 导航修复 (2026-05-06)**：确认/解决/关闭告警后自动调用 `onBack()` 返回上一页，不再停留在详情页 |
| AI | **定位 elderId 查询修复 (2026-05-06)**：后端 `GetLatestLocation`/`GetTrajectory`/`GetRunningData` 支持仅传 `elderId`（无 `deviceId`）时自动从 bindings 表解析 `deviceId`，解决 Android 只传 `elderId` 导致 Redis key 查找失败的问题 |
| AI | **忽视 API 字段名修复 (2026-05-06)**：根因 `UpdateAlertStatusReq(status)` → 改 `action` 字段对齐后端 `json:"action"`；后端 switch-case 同时接受 `confirm/confirmed` 等；新增独立 `dismissScope` 防止切 Tab 取消协程；`rememberSaveable` 跨页面记住已忽视 ID |
| AI | **地图初始化修复 (2026-05-06)**：AMap 3D SDK 10.x 新增 `MapsInitializer.updatePrivacyShow/Agree` 隐私合规初始化；图例从右下移到左上避免挡住缩放按钮；开启 `isZoomGesturesEnabled` |
| AI | **硬件代码更新 (2026-05-06)**：硬件团队最新 ESP32 固件 `hardware1test.zip` → 已整合到 `hardware/esp32/esp32sense.ino` + `wordmap.h` + `k210/detect.kmodel` + `k210/main.py`，SN_TEST_003→SN_TEST_005 |
| AI | **cloud-deploy 完整交付包 (2026-05-06)**：重组织 cloud-deploy 为三端完整交付目录 — ① 后端 Go 源码（internal/cmd/config/migrations + Docker）② Android 完整源码（18 页面 Kotlin + Compose + Gradle）③ 硬件固件源码（ESP32 + K210）④ 预构建云版 APK（android/apk/VisionGuard-v1.4-cloud.apk，已签名，118MB，BASE_URL 指向 47.94.146.53:3000）。本地版 APK 在项目根目录。含中英文 README。
| AI | **CompactTopBar 标题栏修复 (2026-05-06)**：7 页面 TopAppBar 从 M3 默认 64dp → CompactTopBar 48dp（Location/DeviceManagement/ElderManagement/ElderDetail/NotificationList/OcrMedicine/UserSettings），统一 PrimaryBlue 背景 + ArrowBack + 可选 actions
| AI | **NetworkMonitor 离线检测 (2026-05-06)**：新增 `NetworkMonitor` object，`ConnectivityManager.NetworkCallback` 实时追踪网络状态，`isOnline()` 供全局查询。`MainActivity.onCreate()` 调用 `NetworkMonitor.init(this)`
| AI | **ErrorHelper 离线提示 (2026-05-06)**：`ErrorHelper.userMessage()` 优先查 `NetworkMonitor.isOnline()`，设备无网络时返回"当前处于离线状态，请检查手机网络"，无需 Context 参数
| AI | **通知中心修复 (2026-05-06)**：后端 notification service 响应字段 `messages`→`list`（对齐 Android PaginatedData）；MsgItem 新增 `priority` 字段；Android NotificationApi `@Query("read")`→`@Query("readStatus")`
| AI | **ProfileScreen 未读角标 (2026-05-06)**：ProfileScreen 消息通知入口新增 `UnreadBadge`（呼吸动效），LaunchedEffect 启动时获取未读计数
| AI | **OCR 后端二进制上传 (2026-05-06)**：`handler/ocr.go` UploadImage 支持双模式 — multipart/form-data（硬件 JPEG 二进制，按 deviceId 分目录存储）+ application/json（Android base64 data URL）；`main.go` 新增 `app.Static("/uploads", "./uploads")` 静态文件服务
| AI | **cloud-deploy 全量同步 + APK 构建 (2026-05-06)**：将 CompactTopBar/NetworkMonitor/ErrorHelper/通知修复/OCR multipart 等全部改动同步至 cloud-deploy；构建 VisionGuard-v1.4-cloud.apk（118MB）并 ADB 安装到手机
| AI | **CompactTopBar 文字裁剪修复 (2026-05-06)**：M3 TopAppBar 强制 48dp 导致文字被内部 padding 遮挡 → 改用自定义 Row（48dp + PrimaryBlue 背景 + IconButton + Text weight(1f)），去掉 TopAppBar/TopAppBarDefaults/ExperimentalMaterial3Api 依赖
| AI | **用药计划后端 (2026-05-06)**：新增 `model.MedicationPlan`（药品/剂量/频次/JSON schedule/起止日期/状态）+ `MedicationService` CRUD + `MedicationHandler` 6 路由（监护人创建/列表/更新/删除 + 硬件轮询 + 豆包识别）+ `GET /api/v1/device/:deviceId/pending-messages` 硬件轮询（±3min 用药提醒 + 5min 内 OCR 结果）；`app.Static` 静态文件服务
| AI | **豆包 API 占位 (2026-05-06)**：`config.go` 新增 `DOUBAO_API_KEY`/`DOUBAO_API_URL`（默认 ark.cn-beijing.volces.com）；`DoubaoService.RecognizeMedicine` 占位（注释包含真实调用格式）；`MockRecognizeMedicine` 基于 OCR 文字关键词模拟识别；`.env.example` 新增豆包配置项
| AI | **硬件 OCR 接口完善 (2026-05-06)**：新增 `GET /api/v1/ocr/result/latest`（deviceAuth，硬件轮询最新识别结果，按 deviceId 查最近 completed 记录）；新增 `POST /api/v1/device/ocr/image`（deviceAuth，硬件 JPEG 上传备选路由）；`OcrService.GetLatestResult` 查询方法
| AI | **后端路由扩展 (2026-05-06)**：74→81 条路由（+9 OCR +7 用药 - 去重），十一大业务模块（新增"用药计划与管理"），16→17 表（+MedicationPlan）
| AI | **豆包 API 正式接入 (2026-05-06)**：OC​R 管线完全替换为豆包 — UploadImage 后异步调用 DoubaoService.RecognizeMedicine（doubao-seed-1.6-vision）；OcrService 依赖 DoubaoService；移除 mockOCR；豆包 Prompt 标准化输出（drugName/specification/indication/usage/warnings/riskLevel/confidence）；.env 填入真实 API Key `ark-632c...`
| AI | **OCR 响应字段对齐 Android (2026-05-06)**：后端 ListRecords 返回 `list`（对齐 PaginatedData）+ 新增 taskId/ocrText 字段；GetOcrResult 返回 medicineName/dosage/contraindications 等标准化豆包字段
| AI | **并发写入保护 (2026-05-06)**：新增 `internal/infra/lock.go` Redis 分布式锁（SET NX EX + Lua 脚本安全释放）+ `WithLock` 辅助函数
| AI | **OC​R 管线完整重写 (2026-05-06)**：上传→豆包异步识别→存储结果→硬件/APP轮询 全链路打通；进度消息改为中文豆包阶段；cloud-deploy 全量同步 + 云版 APK 重建
| AI | **ESP32 WiFi/服务器更新 (2026-05-06)**：固件 WiFi SSID → `wuiPhone 16`、密码 → `12345ssDLH`、BASE_URL → `http://47.94.146.53:3000`（云服务器）；cloud-deploy/hardware 同步
| AI | **用药计划闹钟式时间选择 (2026-05-06)**：替换逗号分隔文本输入为 TimePicker 时间片（Chip + 添加/删除按钮）；M3 DatePickerDialog 替代系统 DatePickerDialog（与老人生日同款）
| AI | **全局渐变背景 (2026-05-06)**：17 页面柔和毛玻璃渐变（上 浅米黄泛粉 #FFF5F0 → 下 纯白）；AppColors.kt 新增 Modifier.gradientBackground() 扩展函数；Scaffold containerColor 改为 Color.Transparent 透出渐变
| AI | **OCR 硬件轮询 GET 401 修复 (2026-05-06)**：根因 Fiber 路由匹配顺序 — `/api/v1/ocr/result/:taskId`（参数路由, userAuth）注册在 `/api/v1/ocr/result/latest`（精确路由, deviceAuth）之前，Fiber 将 `latest` 当 `:taskId` 值匹配到 userAuth 中间件 → 设备 JWT 通不过用户认证 → 401。修复：精确路由移到参数路由前。同步修复 cloud-deploy。
| AI | **drugName UTF-8 截断修复 (2026-05-06)**：豆包返回中文 drugName 时 byte-based `drugName[:20]` 切到多字节字符中间 → DB UTF-8 编码错误。改为 rune-based `[]rune(drugName)[:20]`。同步修复 cloud-deploy。
| AI | **云端重新部署 (2026-05-06)**：旧容器名 `visionguard-*`，新 `cloud-deploy-*`，端口冲突 + volume 迁移（pgdata→visionguard_pgdata）。部署命令：`cd cloud-deploy && docker compose -f docker-compose.prod.yml up -d --build`。

### 当前状态 (2026-05-06 深夜)

- 后端全部功能编译通过（`go build ./...` 成功）
- 总路由数：81 条（十一大业务模块，17 表 AutoMigrate）
- `test_all_full.go` 76 步全路由测试全部 PASS（涵盖全部 旧77 路由 + healthz）
- `test_e2e.sh` 端到端模拟全部 PASS（curl 本地模拟硬件→后端→APP 全链路）
- **Android 真机关键功能已验证**：创建老人 ✅ 修改密码 ✅ 换绑手机号 ✅ 设备绑定 ✅
- **首页仪表盘**：设备在线数/老人数/24h告警数 ✅
- **设备绑定全链路**：搜索→绑定→自动确认→首页显示 ✅（MVP 不需要硬件确认）
- **Android 核心功能全部完成（18 个页面）**：
  - 认证：LoginScreen / RegisterScreen / ForgotPasswordScreen（手机号登录+密码可见切换）
  - 首页：HomeScreen（仪表盘+新告警卡片+最近告警列表）
  - 告警：AlertHistoryListScreen（分页历史）+ AlertDetailScreen（详情+确认/解决/关闭+时间线）
  - 定位用药：PositionMedicineScreen（卡片入口）→ LocationScreen（最新位置+24h轨迹）→ MapScreen（高德地图+标记+轨迹）+ OcrMedicineScreen（OCR记录）
  - 设备：DeviceManagementScreen（扫码/相册/手动三种绑定+解绑）
  - 老人：ElderManagementScreen（列表+创建+农历生日）+ ElderDetailScreen（详情+编辑+监护人列表+删除）
  - 个人：ProfileScreen（编辑昵称+4个功能入口）+ UserSettingsScreen（修改密码+手机号换绑）
  - 通知：NotificationListScreen（消息列表+全部已读）
- Android 构建：`./gradlew :app:assembleRelease` ✅（已签名）
- Release APK：`VisionGuard-v1.4.apk`（118MB），项目根目录（本地版，127.0.0.1:3000）；`cloud-deploy/android/apk/VisionGuard-v1.4-cloud.apk`（118MB，云版，47.94.146.53:3000）
- cloud-deploy = 完整三端交付包：后端源码 + Android 完整源码 + 预构建 APK + 硬件固件
- 云服务器：Dockerfile 国内需加 `ENV GOPROXY=https://goproxy.cn,direct` 解决 go mod download 超时
- 高德地图 SDK 10.0.600 已集成，API Key 已配置（`d8fe...`，见 local.properties）
- 全局设计规范已对齐 UI11.DOCX：#165DFF 主色 + 16dp 统一圆角
- API 字段已对齐后端：告警类型 7 种 / 等级 4 级 / 时间线 at-action-by / GuardianInfo.nickname
- **硬件团队固件已对齐 v1 API**：ESP32 `activate→register→challenge→verify→heartbeat→alert` + OCR image/result
- **Android 旧代码保留**：4 旧 screen（DeviceScreen/HistoryScreen/ObstacleScreen/RecognitionScreen）+ 旧 engine 保留，全流程验证通过后再清理
- API 字段已对齐后端：告警类型 7 种 / 等级 4 级 / 时间线 at-action-by / GuardianInfo.nickname
- 项目名已统一为 VisionGuard，端口统一为 3000
- 生产服务器：`http://47.94.146.53:3000/`
- **OCR 管线状态**：豆包 API 已接入（ep-20260506095629-bgl8v），上传→异步识别→DB 存储→硬件/APP 轮询全链路已通；测试脚本 `test_ocr.py`（Python + curl）可端到端验证；硬件轮询 GET 401 已修复（Fiber 路由顺序）
- **云端部署**：`docker compose -f docker-compose.prod.yml up -d --build`（在 `cloud-deploy/` 目录执行，需 `.env`）；数据卷 `visionguard_pgdata`；旧容器名 `visionguard-*` 已迁移至 `cloud-deploy-*`

### Android 路由接入状态

后端 77 路由中，面向 Android 监护端的约 60 条用户路由，其中：

- **已接入 UI（~30 条）**：认证/老人CRUD/设备绑定/告警全流程/通知/定位/OCR记录/仪表盘
- **API 已定义、无独立 UI（~12 条）**：电子围栏 CRUD（3条）/ 告警地图标记 / 设备运行数据 / 健康数据查询 / OCR 拍照识别 / 监护人邀请/接受/转让 / 推送规则配置 — 属于锦上添花功能，API 客户端已定义好，后续可快速加 UI
- **硬件专有（~10 条）**：device challenge/verify/register/heartbeat 等 — Android 不需要

### 测试命令

```bash
cd backend
go run cmd/server/main.go       # 启动后端（需先确保 PostgreSQL + Redis 在跑）
go run test_all.go              # 21 步核心流程测试
go run test_all_full.go         # 76 步全路由覆盖测试
bash test_e2e.sh                # 端到端模拟（硬件→后端→APP，纯 curl）
bash test_get_401.sh            # GET 401 诊断（POST vs GET 同 JWT + deviceAuth 路由测试）
python3 test_ocr.py             # OCR 全链路（设备认证→上传图片→豆包识别→轮询结果）
```

### 云端部署

```bash
# 服务器上（/opt/visionguard/repo/）
cd cloud-deploy
# 首次部署需创建 .env（可用 docker inspect visionguard-backend-1 提取旧容器环境变量）
docker compose -f docker-compose.prod.yml up -d --build

# 查看日志
docker logs cloud-deploy-backend-1 --tail 50

# 注意：数据卷名为 visionguard_pgdata（从旧 visionguard 项目迁移）
```

### Android 真机联调

**痛点**：手机开热点时，手机自身 APP 流量走蜂窝网而非热点接口，无法访问热点下的电脑后端（ping 不通）。

**诊断三板斧**：
```bash
# 1. 检查手机网络接口
adb shell "ip addr show" | grep -E "inet |state"

# 2. 检查手机能否 ping 通电脑
adb shell "ping -c 2 10.26.43.176"

# 3. 检查电脑 IP 是否在手机热点网段
ipconfig | grep "IPv4"
```

**修复方式**：
```bash
# ADB 反向隧道（手机 127.0.0.1:3000 → 电脑 localhost:3000）
adb reverse tcp:3000 tcp:3000

# Android 端 BASE_URL 改为
http://127.0.0.1:3000/

# 每次拔插 USB 后需重做 reverse
```

### 端口/环境对照

| | 本地开发 | 云服务器 |
|------|------|------|
| 地址 | `http://localhost:3000` | `http://47.94.146.53:3000` |
| 端口 | 3000 | 3000 |
| DB_HOST | `localhost` | `postgres`（Docker 服务名） |
| REDIS_HOST | `localhost` | `redis`（Docker 服务名） |

### 文档索引（docs/，共 12 份）

| 文件 | 给谁 | 内容 |
|------|------|------|
| `部署指南.md` | 全团队 | ★ 生产部署：后端Docker+硬件改IP+Android改BASE_URL+全链路验证 |
| `硬件对接文档.md` | 硬件团队 | 4步认证+XOR C++代码+本地测试+curl脚本+排查 |
| `业务流程与后端设计.md` | 学长/Android | 77路由+16表+业务流+安全+部署 |
| `产品需求说明书.md` | 全团队 | 完整产品需求 |
| `安卓说明文档.md` | 全团队 | 三端业务边界定义+P0/P1功能模块+串口协议 |
| `Android-UI设计文档.md` | Android | UI11.DOCX 设计规范：4 Tab+色值字体圆角+11页面结构+后端接口对照 |
| `api.md` | 硬件团队（存档） | 旧版 6 接口文档（端口 8888），供参考 |
| `业务设计 (1).md` | 学长原始 | 原始设计文档（存档） |
| `代码审查清单.md` | 后端 | 5阶段61检查点 Review |
| `数据流模拟.md` | 全团队 | 端到端数据流 |
| `开发日志.md` | 后端 | 完整开发记录 |
| `变更记录.md` | 全团队 | 版本变更 |

## Project overview

VisionGuard — 面向视障与老年群体的胸挂式智能设备系统。三端架构：
- **硬件**：ESP32 + K210，本地感知/计算/告警，离线可用
- **云端**（`backend/`）：Go Fiber HTTP 服务，设备认证、数据中转、OCR、告警推送
- **监护端**（`app/`）：Android APP，设备绑定、告警展示、老人信息配置

核心原则：**硬件主导、本地优先**。所有安全功能（避障/摔倒）在 ESP32 本地完成，云端只做中转与增强。

## Repository layout

```
vision-hub/
├── app/                    # Android APP (Kotlin + Compose)
├── backend/                # Go 云端服务
│   ├── cmd/server/main.go              # 入口（16 AutoMigrate + 8 service + 8 handler + ~74 路由）
│   ├── internal/
│   │   ├── config/config.go            # .env 配置加载（DEVICE_XOR_KEY=0x4B）
│   │   ├── infra/                      # PostgreSQL + Redis 连接
│   │   ├── model/                      # 16 个 GORM 模型（全部含 json tag camelCase）
│   │   │   ├── user.go                 # User, RefreshToken
│   │   │   ├── elder.go                # Elder, EmergencyContact, Guardianship, Invitation, Transfer
│   │   │   ├── device.go               # Device, Binding
│   │   │   ├── alert.go                # Alert（7 类 + 去重 + 时间线）
│   │   │   ├── notification.go         # Notification（P0-P3 优先级 + Channel）
│   │   │   ├── ocr.go                  # OcrRecord（OCR+LLM 全流程字段）
│   │   │   ├── location.go             # Location, Geofence, HealthData
│   │   │   └── auth_log.go             # AuthLog（设备认证事件日志）
│   │   ├── handler/                    # 8 个 HTTP 处理器
│   │   │   ├── auth.go                 # challenge/verify/register/login/refresh/logout
│   │   │   ├── device.go               # 激活/认证/心跳/状态/固件/批量
│   │   │   ├── elder.go                # 老人CRUD/监护人邀请转让/仪表盘
│   │   │   ├── binding.go              # 搜索/发起绑定/确认/约束校验/解绑/换绑
│   │   │   ├── alert.go                # 类型列表/创建/状态管理/历史/详情/统计
│   │   │   ├── notification.go         # 消息列表/已读/推送规则/推送状态
│   │   │   ├── ocr.go                  # 上传/OCR识别/结果/轮询/建议/反馈
│   │   │   └── location.go             # 最新位置/轨迹/运行数据/围栏/健康数据
│   │   ├── service/                    # 业务逻辑
│   │   │   ├── auth.go                 # XOR 0x4B, challenge-response, JWT(设备+用户), bcrypt, 登录锁定
│   │   │   ├── device.go               # 激活/注册/心跳(Redis TTL 180s)/离线检测(90s阈值+通知+日志)
│   │   │   ├── elder.go                # 档案CRUD/监护人邀请接受(身份验证)/转让(角色互换)/归档
│   │   │   ├── binding.go              # 设备搜索/发起(监护人权限+老人活跃检查)/确认(5min超时)/解绑(主监护人)
│   │   │   ├── alert.go                # 7种类型/创建(去重窗口)/通知生成(info仅推主监护人)/统计(按类型/等级/状态)
│   │   │   ├── notification.go         # 消息列表/标记已读(归属校验)/推送目标(等级→渠道映射)
│   │   │   ├── ocr.go                  # 上传记录/OCR任务(异步mock)/LLM建议生成/识别反馈
│   │   │   └── location.go             # 位置(Redis→DB)/轨迹/围栏CRUD/告警地图标记/健康数据
│   │   └── middleware/auth.go          # UserAuth + DeviceAuth（JWT 校验，c.Locals 传用户/设备ID）
│   ├── docker-compose.yml / docker-compose.prod.yml / Dockerfile / deploy.sh
│   ├── test_all.go                     # 21 步全流程功能测试
│   └── .env.example
├── cloud-deploy/           # ★ 云服务器部署包（后端代码 + Docker 配置）
├── hardware/               # 硬件固件（最新：esp32/esp32sense.ino + k210/main.py）
├── docs/                   # 规格文档 + 交付文档
│   ├── 硬件对接文档.md        # 硬件 ESP32 对接指南（替代旧 api.md）
│   ├── 产品需求说明书.md      # 完整产品需求
│   ├── 业务设计(1).md        # 后端业务设计文档（10 模块，当前实现依据）
│   ├── 业务流程与后端设计.md  # 后端设计蓝图（给学长的交付文档）
│   ├── 数据流模拟.md       # 端到端数据流模拟
│   ├── 开发日志.md       # 开发日志
│   └── 变更记录.md        # 变更记录
└── CLAUDE.md               # 本文件
```

## Backend development

### Stack
- Go 1.23+, Fiber v2, GORM, PostgreSQL 16, Redis 7
- JWT HS256, bcrypt (DefaultCost), XOR 0x4B challenge-response

### Commands
```bash
cd backend
go mod tidy
go build ./...                            # 编译检查
go run cmd/server/main.go                 # 启动（需 PostgreSQL + Redis 在跑）
go run test_all.go                        # 21 步全流程测试
```

### Architecture principles

1. **严格对齐业务设计文档**：所有接口路径、参数、响应格式按 `docs/业务设计(1).md` 实现
2. **IP 反查设备**：`/upload_image`、`/get_ocr_text`、`/upload_audio` 硬件不传 deviceId → 后端根据客户端 IP 反查（device_register 时记录 IP）
3. **心跳 deviceId 从 JWT 取**：不信任请求体中的 deviceId，防止设备伪造
4. **DeviceInfoRegister 用 c.IP()**：不信任客户端传来的 IP 字段
5. **所有 handler 类型断言用 comma-ok 模式**：失败返回 401，不 panic
6. **PasswordHash / DeviceCode json:"-"**：敏感字段不序列化到响应
7. **离线检测带通知+日志**：每 10s 扫描→90s 无心跳→标记 offline + auth_log + notification 推送监护人
8. **Token 翻转安全**：先创建新 RefreshToken 再删除旧 token，防崩溃丢失

### 10 大业务模块与接口

#### 一、认证服务（8 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| POST | `/api/v1/device/challenge` | 无 | 请求 challenge（XOR 0x4B，Redis TTL 5min） |
| POST | `/api/v1/device/verify` | 无 | 提交签名验证，返回设备 JWT（24h） |
| POST | `/api/v1/device/register` | 无 | 设备首次接入注册（记录 IP） |
| POST | `/api/v1/device/info` | 无 | 记录设备基础信息（型号/固件版本） |
| POST | `/api/v1/device/log` | 无 | 记录设备认证事件日志 |
| POST | `/api/v1/auth/register` | 无 | 用户注册（bcrypt + 8位密码 + 重复检查） |
| POST | `/api/v1/auth/login` | 无 | 登录（JWT 1h + refresh_token 30d，8次失败锁账户） |
| POST | `/api/v1/auth/refresh` | 无 | 刷新 access_token（翻转机制） |
| POST | `/api/v1/auth/logout` | 无 | 登出（删除 refresh_token） |

#### 二、老人档案与监护关系（15 路由，UserAuth）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/elder` | 创建老人档案（创建者自动设为主监护人） |
| GET | `/api/v1/elder/:elderId` | 查询档案详情（角色过滤：主监护人看全部，普通看有限信息） |
| PUT | `/api/v1/elder/:elderId` | 更新档案（主监护人专权） |
| DELETE | `/api/v1/elder/:elderId` | 删除档案（主监护人，事务：解绑所有设备） |
| POST | `/api/v1/elder/:elderId/archive` | 归档封存（主监护人，自动解绑） |
| POST | `/api/v1/elder/:elderId/guardian/invite` | 邀请协作监护人（主监护人，48h 过期） |
| POST | `/api/v1/elder/:elderId/guardian/accept` | 接受邀请（需匹配手机/邮箱 + 双重检查） |
| DELETE | `/api/v1/elder/:elderId/guardian/:userId` | 移除监护人（主监护人可移除他人，普通只能移除自己） |
| POST | `/api/v1/elder/:elderId/primary/transfer` | 发起主监护人转让（from 主 → to 普通） |
| POST | `/api/v1/elder/:elderId/primary/confirm` | 确认转让（被转让人操作，角色互换 + createdBy 更新） |
| POST | `/api/v1/elder/:elderId/emergency-contact` | 添加紧急联系人（主监护人） |
| DELETE | `/api/v1/elder/:elderId/emergency-contact/:contactId` | 删除紧急联系人 |
| POST | `/api/v1/elder/:elderId/bind` | 绑定设备到老人 |
| GET | `/api/v1/elders` | 查询"我监护的老人"列表（含设备在线状态） |
| GET | `/api/v1/dashboard` | 监护人仪表盘（含24h告警计数 + 紧急联系人） |

#### 三、设备接入与安全注册（8 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| POST | `/api/v1/device/activate` | 无 | 设备激活注册（发放 deviceSecret + certificate） |
| POST | `/api/v1/device/auth` | 无 | 设备认证获取 Token（返回绑定状态+elderId） |
| PUT | `/api/v1/device/:deviceId` | DeviceAuth | 更新设备信息（别名/安装位置） |
| POST | `/api/v1/device/:deviceId/toggle` | DeviceAuth | 设备禁用/启用 |
| GET | `/api/v1/device/:deviceId/firmware` | DeviceAuth | 固件版本查询（stub） |
| POST | `/api/v1/device/:deviceId/data` | DeviceAuth | 设备数据上报 |

#### 四、设备心跳与在线状态管理（5 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| POST | `/api/v1/device/heartbeat` | DeviceAuth | 心跳上报（30s间隔，Redis TTL 180s，定位存储） |
| GET | `/api/v1/device/status/:deviceId` | DeviceAuth | 在线状态查询（Redis 优先，连续在线时长） |
| GET | `/api/v1/device/:deviceId/last-online` | DeviceAuth | 最后在线时间查询 |
| POST | `/api/v1/devices/batch-status` | UserAuth | 批量设备状态查询 |
| — | 离线检测 goroutine | 内部 | 每 10s 扫描→90s 无心跳→标记 offline + 通知 |

#### 五、设备绑定与解绑（7 路由，UserAuth）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/device/:deviceId/search` | 搜索设备（判断 canBind） |
| POST | `/api/v1/binding/initiate` | 发起绑定（监护人权限+老人活跃+设备可绑+防重复 pending） |
| POST | `/api/v1/binding/confirm` | 确认绑定（设备端调用，5min 超时，事务更新） |
| POST | `/api/v1/binding/check` | 唯一绑定约束校验 |
| POST | `/api/v1/binding/unbind` | 解绑（主监护人专权） |
| POST | `/api/v1/binding/rebind` | 换绑（主监护人+目标监护人均需权限） |
| GET | `/api/v1/device/:deviceId/binding` | 查询设备绑定关系（含历史） |

#### 六、设备数据接收与存储（2 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| POST | `/api/v1/data/health` | 无 | 健康数据接收（心跳/血压/步数/血氧，自动关联绑定） |
| GET | `/api/v1/data/health` | UserAuth | 历史健康数据查询（分页+过滤） |

#### 七、告警事件管理（8 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| GET | `/api/v1/alert/types` | 无 | 告警类型列表（7 种：fall/obstacle/sos/heart_rate/low_battery/offline/geofence） |
| POST | `/api/v1/alert` | 无 | 上报告警（去重窗口：fall 120s/heart_rate 300s/其他 60s） |
| GET | `/api/v1/alerts` | UserAuth | 告警历史查询（分页+过滤） |
| GET | `/api/v1/alert/statistics` | UserAuth | 告警统计（按天/周/月，按类型/等级/状态） |
| GET | `/api/v1/alert/level-config` | UserAuth | 告警等级配置 |
| GET | `/api/v1/alert/:alertId` | UserAuth | 告警详情（含老人/设备信息 + 时间线） |
| PUT | `/api/v1/alert/:alertId/status` | UserAuth | 更新告警状态（confirm/resolve/close） |
| POST | `/api/v1/alert/:alertId/resolve` | UserAuth | 解决告警（记录 resolution） |

#### 八、定位与设备状态展示（7 路由）

| 方法 | 路径 | 认证 | 说明 |
|------|------|:--:|------|
| GET | `/api/v1/location/latest` | UserAuth | 最新位置（Redis 缓存→DB 回退） |
| GET | `/api/v1/location/trajectory` | UserAuth | 历史轨迹（时间段查询） |
| GET | `/api/v1/location/alert-markers` | UserAuth | 告警地图标记（时间段+类型过滤） |
| GET | `/api/v1/device/:deviceId/running` | UserAuth | 设备运行数据展示 |
| POST | `/api/v1/geofence` | UserAuth | 创建电子围栏（圆形/多边形） |
| GET | `/api/v1/geofences` | UserAuth | 围栏列表 |
| DELETE | `/api/v1/geofence/:fenceId` | UserAuth | 删除围栏 |

#### 九、药品识别与智能建议（7 路由，UserAuth）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ocr/image` | 图片上传记录（药品/说明书分类） |
| POST | `/api/v1/ocr/recognize` | 创建 OCR 识别任务（异步 mock，3s 完成） |
| GET | `/api/v1/ocr/result/:taskId` | 查询 OCR 结果（含药品匹配详情） |
| GET | `/api/v1/ocr/poll/:taskId` | 任务状态轮询（进度+阶段消息） |
| POST | `/api/v1/ocr/suggestion` | 生成 LLM 用药建议（异步 mock，5s 完成） |
| POST | `/api/v1/ocr/feedback` | 记录识别反馈 |
| GET | `/api/v1/ocr/records` | 历史识别记录查询（分页） |

#### 十、消息推送与通知（8 路由，UserAuth）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/notifications` | 消息列表（分页+类型过滤+未读/已读过滤） |
| PUT | `/api/v1/notifications/read` | 标记已读（校验 user_id 归属） |
| PUT | `/api/v1/notifications/read-all` | 全部已读 |
| GET | `/api/v1/notification/push-rules` | 推送规则配置（alert/device/medicine 三级映射） |
| POST | `/api/v1/notification/push-targets` | 获取推送目标（根据等级选择监护人+渠道） |
| POST | `/api/v1/notification/push` | 发送推送（stub） |
| GET | `/api/v1/notification/status/:messageId` | 推送状态查询 |
| GET | `/api/v1/notification/priority-config` | 优先级配置（P0-P3 + 渠道映射） |

### Database schema (16 tables)

| 表名 | 关键字段 | 说明 |
|------|---------|------|
| `users` | username(UK), password_hash(json:"-"), email, phone, status(active/locked/disabled) | 监护人账号 |
| `refresh_tokens` | user_id, token_hash(UK, json:"-"), expires_at | JWT 刷新令牌 |
| `elders` | elder_id(UK), name, gender, blood_type, allergy, medical_history, status(active/archived), created_by | 老人档案 |
| `emergency_contacts` | elder_id, name, relation, phone | 紧急联系人（FK→elders） |
| `guardianships` | elder_id, user_id, role(primary/normal) | 监护关系（多对多） |
| `invitations` | invite_id(UK), elder_id, inviter_id, invitee(phone/email), status, expires_at(48h) | 监护邀请 |
| `transfers` | transfer_id(UK), elder_id, from_user_id, to_user_id, status | 主监护人转让 |
| `devices` | device_id(UK), device_code(json:"-"), serial_no, model, mac, status(registered/online/offline/disabled), bind_status, ip, battery, rssi, lat/lng | 设备主档案 |
| `bindings` | bind_id(UK), device_id, elder_id, bound_by, status(pending_device_confirm/bound/unbound), bound_at/expires_at | 设备绑定 |
| `alerts` | alert_id(UK), device_id, elder_id, alert_type, alert_level, status(pending/confirmed/resolved/closed), duplicate_count, resolution, timeline fields | 告警事件 |
| `notifications` | message_id(UK), user_id, elder_id, type, title, body, channel(app/sms/voice_call), priority(P0-P3), read, delivery_status | 推送通知 |
| `ocr_records` | task_id(UK), image_id, elder_id, device_id, status(stages), ocr_text, confidence, medicine fields, suggestion fields, feedback | OCR识别记录 |
| `auth_logs` | device_id, log_type(auth_success/auth_fail/register/offline/reconnect/online), message | 设备认证日志 |
| `locations` | data_id(UK), device_id, elder_id, lat, lng, accuracy, speed, heading | 定位数据 |
| `geofences` | fence_id(UK), elder_id, fence_name, fence_type(circle/polygon), center_lat/lng, radius, vertices(JSON), enabled | 电子围栏 |
| `health_data` | data_id(UK), device_id, elder_id, type(heart_rate/blood_pressure/steps/spo2), value, unit, bound | 健康数据 |

### Key business flows

1. **设备认证流程**：POST /activate（激活）→ POST /register（接入注册，记录IP）→ POST /challenge（请求挑战）→ POST /verify（XOR 0x4B 签名验证）→ 获取 JWT（24h）
2. **设备绑定流程**：APP 登录→搜索设备→发起绑定（监护人+老人+设备状态校验）→设备确认（5min超时）→绑定完成
3. **摔倒告警全链路**：硬件检测→POST /alert（去重窗口）→存 alert + 查绑定→找监护人→创建 notification→APP 轮询发现
4. **药品识别流程**：上传图片→创建 OCR 任务→异步识别（mock 3s）→LLM 建议生成（mock 5s）→APP 轮询结果
5. **离线检测**：每 10s 扫描→90s 无心跳→标记 offline→auth_log + notification→设备恢复心跳→重连日志
6. **主监护人转让**：发起（主监护人→普通监护人）→确认（被转让人）→角色互换 + createdBy 更新

### Security checklist

- [x] XOR 0x4B challenge-response（每字符 ^ 0x4B, hex 编码）
- [x] bcrypt 密码哈希（DefaultCost, 8 字符最小长度）
- [x] JWT HS256（设备 24h / 用户 1h，type 字段区分）
- [x] RefreshToken 翻转机制（先创建新再删除旧，bcrypt hash 存储）
- [x] 登录失败计数（8 次/30min → 账户锁定）
- [x] 心跳 deviceId 从 JWT 取（不从 body）
- [x] DeviceInfoRegister 用 c.IP()（不信任客户端 IP 字段）
- [x] Notification MarkRead 校验 user_id 归属
- [x] PasswordHash / DeviceCode json:"-"（敏感字段不返回）
- [x] Challenge Redis TTL 5 分钟自动过期
- [x] 监护人邀请身份验证（手机/邮箱匹配）
- [x] 主监护人转让双向确认
- [x] 绑定唯一约束校验 + 并发防重
- [x] 类型断言 comma-ok 模式
- [x] 所有 JSON 响应 camelCase

### Known limitations

- 实时推送为轮询模式：APP 需定时 GET /notifications，非 FCM/WebSocket
- OCR/LLM 为 mock 异步（3s/5s 延时），需对接真实服务
- 固件升级检测为 stub（始终返回无新版本）
- 推送发送（sms/voice_call）为 stub，需对接第三方推送服务
- 轨迹数据无压缩（未实现 Douglas-Peucker 简化算法）
- 无单元测试

## Spec documents

开发依据文档（都在 `docs/`）：
- **硬件对接文档.md**：硬件 ESP32 对接指南 — 新认证流程、XOR 签名 C++ 代码、新旧接口对照、curl 测试
- **产品需求说明书.md**：完整需求 — 检查功能是否遗漏的第一参考
- **业务设计 (1).md**：**学长发的原始设计文档** — 10 大业务模块的完整规格，当前实现的依据

## Android development

Kotlin + Jetpack Compose + Material 3, JDK 17, minSdk 35.
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Key components: VisionHubService (foreground), VisionTcpServer (:8080), FallDetectionEngine, VisionDataHub (Flow), MainActivity (Compose UI).

## Hardware

硬件目录结构（2026-05-06 更新）：

```
hardware/
├── README.md                     # 硬件架构说明（旧，部分内容已过时）
├── esp32/
│   ├── esp32sense.ino            # ★ 硬件团队最新 ESP32 固件（对齐 v1 API）
│   └── wordmap.h                 # 中文汉字 WAV 语音库映射表
├── k210/
│   ├── main.py                   # ★ K210 Python 代码（拍照+方位检测+串口传输）
│   └── detect.kmodel             # K210 AI 障碍物检测模型（1.5MB）
├── sketch_apr23a.ino             # 旧 ESP32 固件（旧 API，存档参考）
├── evasion.py                    # 旧避障逻辑（存档参考）
└── docs/ / scripts/ / src/       # 旧 FastAPI 测试服务器（存档参考）
```

**硬件最新固件**（`esp32/esp32sense.ino`）已对齐后端 v1 API：

| 步骤 | API | 说明 |
|------|-----|------|
| 1 | POST `/api/v1/device/activate` | 设备激活，获取 deviceId + deviceSecret |
| 2 | POST `/api/v1/device/register` | 设备注册 |
| 3 | POST `/api/v1/device/challenge` | 请求 challenge |
| 4 | POST `/api/v1/device/verify` | XOR 0x4B 签名验证，获取 JWT |
| 5 | POST `/api/v1/device/heartbeat` | 心跳上报（30s 间隔，带 JWT） |
| 6 | POST `/api/v1/alert` | 告警上报（fall / obstacle） |
| 7 | POST `/api/v1/ocr/image` | 药品图片上传 |
| 8 | GET `/api/v1/ocr/result/latest` | 获取 OCR 结果 |

K210 主控 ESP32 串口（`k210/main.py`）：
- 波特率 115200，UART2（RX=44, TX=43）
- `$TAKE_PHOTO!` → K210 拍照→JPEG 压缩→分块回传
- `$LEFT!` / `$MID!` / `$RIGHT!` → 障碍物方位
- `$IMG_START:长度!` + 二进制数据 + `$IMG_END!` → 图片传输协议
