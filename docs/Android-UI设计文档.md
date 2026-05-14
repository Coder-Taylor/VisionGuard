# VisionGuard Android 端 UI 设计文档

> **来源**：`UI11.DOCX` 视障&老人智能穿戴监护人 APP UI 设计定稿清单。
> **原则**：严格匹配 DOCX，无额外页面、无多余功能、无冗余模块。

---

## 目录

- [底部 Tab 总览](#底部-tab-总览)
- [全局设计规范](#全局设计规范)
- [主页面一：首页（告警总览）](#主页面一首页告警总览)
- [主页面二：个人中心（我的）](#主页面二个人中心我的)
- [全局交互规则](#全局交互规则)
- [附录 A：后端接口对照表](#附录-a后端接口对照表)
- [附录 B：可行性分析](#附录-b可行性分析)

---

## 底部 Tab 总览

> 4 个 Tab，与后端功能直接对应。

| Tab | 图标 | 功能 | 说明 |
|-----|------|------|------|
| Tab 1 | 首页图标 | **首页（告警总览）** | 本页详细设计 |
| Tab 2 | 时钟图标 | **定位 / 用药入口** | 对应系统其他核心模块（DOCX 未展开设计） |
| Tab 3 | 列表图标 | **告警历史入口** | 可复用首页告警列表逻辑 |
| Tab 4 | 人形图标 | **个人中心（我的）** | 本页详细设计 |

**Tab 选中态**：图标+文字变主色 `#165DFF`，未选中为灰色 `#909399`。文字 12sp，居中在图标下方。

---

## 全局设计规范

> 所有页面统一遵守，封装为 Compose Theme。

### 色值

| 用途 | 色值 | 变量名 |
|------|------|--------|
| 主色（柔和安心蓝） | `#165DFF` | `PrimaryBlue` |
| 正常/在线 | `#00B42A` | `SuccessGreen` |
| 告警/危险 | `#F53F3F` | `AlertRed` |
| 功能/提醒 | `#FF7D00` | `WarningOrange` |
| 离线/灰色 | `#909399` | `OfflineGray` |
| 卡片底色 | `#F5F7FA` | `CardBackground` |
| 浅蓝底色 | `#E8F3FF` | `LightBlue` |
| 柔粉 | `#F5E8E8` | `SoftPink` |
| 柔绿 | `#E6F4EA` | `SoftGreen` |
| 柔橙 | `#FFF0E6` | `SoftOrange` |
| 正文黑 | `#333333` | `TextPrimary` |
| 辅助灰 | `#666666` | `TextSecondary` |

### 圆角

| 元素类型 | 圆角 |
|----------|------|
| 卡片、按钮、弹窗 | **16dp** |
| 图标、标签、输入框 | **12dp** |
| 状态标签 | **8dp** |

### 字体

| 层级 | 大小 | 字重 | 颜色 | 用途 |
|------|------|------|------|------|
| 大号强调 | 20sp | Bold | `#333333` | 告警状态、弹窗标题 |
| 页面标题 | 18sp | Bold | `#333333` | 板块标题 |
| 卡片标题/按钮 | 16sp | Bold | `#333333` / 白色 | 按钮文字、设备名 |
| 正文 | 14sp | Normal | `#333333` / `#666666` | 内容文字 |
| 辅助文字 | 12sp | Normal | `#909399` | 时间、编号、Tab 标注 |
| 标签文字 | 10sp | Normal | — | 状态标签 |

### 卡片

- 白底 + 轻微阴影（`elevation = 2dp`）+ 极淡渐变
- 局部轻毛玻璃：半透明白色 + 10% 模糊
- 16dp 统一圆角，无生硬直角

### 按钮

| 属性 | 主按钮 | 次要按钮 |
|------|--------|----------|
| 形状 | `RoundedCornerShape(16.dp)` | 同 |
| 最小高度 | 48dp | 36dp |
| 填充色 | `#165DFF` | `#F5F7FA` |
| 文字 | 16sp 白色 Bold | 16sp `#666666` |
| 按压反馈 | 颜色加深 10%（`alpha = 0.9`） + 震动 10ms | 同 |
| 禁用态 | `alpha = 0.4` | 同 |

---

## 主页面一：首页（告警总览）

> Tab 1。APP 第一屏。仅保留告警相关核心功能。

### 开发者任务清单

- [x] 安全状态文字（正常/告警两态 + 呼吸动效），对接 `GET /api/v1/dashboard`
- [x] 新发告警卡片（条件渲染：有未读 pending 告警时显示），对接 `GET /api/v1/alerts?status=pending`
- [x] 告警历史列表（LazyColumn + 下拉分页），对接 `GET /api/v1/alerts?page=&size=`
- [x] 告警详情子页面（从卡片/列表跳入），对接 `GET /api/v1/alerts/{alertId}`
- [x] 「忽视」按钮标记已读，对接 `PUT /api/v1/alert/{alertId}/status`（后端路由）

### 页面结构

```
┌──────────────────────────────┐
│      顶部：全局安全状态        │  ← 固定不滚动
├──────────────────────────────┤
│      中部：新发告警卡片        │  ← 条件渲染（有 pending 告警时显示）
├──────────────────────────────┤
│                              │
│      下方：告警历史列表        │  ← LazyColumn + 下拉分页
│                              │
└──────────────────────────────┘
```

### 组件一：安全状态文字

| 属性 | 正常态 | 告警态 |
|------|--------|--------|
| 文字 | "正常" | "告警" |
| 颜色 | `#00B42A` | `#F53F3F` |
| 字体 | 20sp Bold 居中 | 20sp Bold 居中 |
| 背景 | 无 | 状态色 10% 透明度高亮块 |
| 动效 | 无 | 呼吸闪烁（`alpha 80%↔100%`，2s 循环） |
| 判定逻辑 | `GET /api/v1/dashboard` → `alertCount24h == 0` | `alertCount24h > 0` |

### 组件二：新发告警卡片

> 条件渲染：`GET /api/v1/alerts?status=pending` 有结果时才显示。

| 属性 | 规格 |
|------|------|
| 卡片容器 | 16dp 圆角，白底，`elevation = 4dp`，阴影 `rgba(255,125,0,0.2)` |
| 标题 | "新发告警"，20sp Bold，居中 |
| 显示字段 | 告警类型 / 告警时间 / 位置 / 设备 ID |
| 字段样式 | 14sp `#666666`，居中排版 |
| 「查看详情」按钮 | 16dp 圆角，`#165DFF` 填充，16sp 白色 Bold → 跳转告警详情页 |
| 「忽视」按钮 | 16dp 圆角，`#F5F7FA` 底，16sp `#666666` → 调 `PUT /alerts/{id}/read`，关闭卡片 |

### 组件三：告警历史列表

| 属性 | 规格 |
|------|------|
| 板块标题 | "告警历史"，18sp Bold，`#333333`，左对齐 |
| 单条卡片 | 12dp 圆角白底，`padding = 12dp`，`elevation = 1dp`，间距 8dp |
| 时间 | 12sp `#909399`（格式 `HH:mm`） |
| 告警类型 | 14sp Bold |
| 位置 | 12sp `#666666` |
| 未读标记 | 右下胶囊：`#F53F3F` 底 + 白色数字，16dp 圆角，最小宽 20dp 高 16dp，呼吸动效 |
| 点击 | 跳转告警详情页 |
| 分页 | 下拉触发 `GET /api/v1/alerts?page=N&size=10` |

### 组件四：告警详情页（子页面）

> 从新发告警卡片或历史列表项跳入。

| 显示字段 | 来源 |
|----------|------|
| 告警类型 | `alert_type` |
| 告警等级 | `alert_level` |
| 告警时间 | `created_at` |
| 位置 | 关联 `location` |
| 设备 ID | `device_id` |
| 处理状态 | `status`（pending / confirmed / resolved / closed） |
| 处理时间线 | `timeline` |

### 首页交互流程

```
用户打开 APP
  │
  ▼
GET /dashboard ──→ 渲染安全状态
  │
  ▼
GET /alerts?status=pending ──→ 有结果? ──是──→ 渲染新发告警卡片
  │                                              │ 点击「查看详情」
  否                                             ▼
  │                                         告警详情页
  ▼
GET /alerts?page=1&size=10 ──→ 渲染历史列表
  │
  ▼
下拉 → page++ 追加数据
```

### 首页涉及的后端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/dashboard` | GET | 安全状态 |
| `/api/v1/alerts` | GET | 告警列表（`?page=&size=&status=&type=`） |
| `/api/v1/alerts/{alertId}` | GET | 告警详情 |
| `/api/v1/alerts/{alertId}/read` | PUT | 标记已读 |

---

## 主页面二：个人中心（我的）

> Tab 4。底部最右侧人形图标。去掉了原方案复杂模块，只保留：设备绑定、用户设置、退出登录。

### 开发者任务清单

- [x] 个人信息区（头像 + 昵称 + 在线标签）
- [x] 功能列表（3 项：设备绑定与管理 / 用户设置 / 退出登录）
- [x] 设备管理子页面（绑定列表 + 扫码绑定 + 手动输入 + 解绑 — 扫码/手动为 TODO）
- [x] 用户设置子页面（手机号展示 + 修改密码表单 — 提交为 TODO，后端无接口）
- [x] 退出登录流程（二次确认 → `POST /auth/logout`）
- [x] 对接 `POST /api/v1/auth/logout`
- [ ] 对接 `GET /api/v1/device/{deviceId}/search`（接口存在，UI 调用待补）
- [ ] 对接 `POST /api/v1/binding/initiate`（接口存在，UI 调用待补）
- [ ] 对接 `POST /api/v1/binding/unbind`（接口存在，UI 调用待补）

### 页面结构

```
┌──────────────────────────────┐
│    个人信息区                  │
│    [头像 64dp] 昵称 [在线]     │  ← 点击跳个人资料编辑
├──────────────────────────────┤
│    功能列表（3 项）            │
│  ┌ 设备绑定与管理        →  ┐ │
│  ├ 用户设置              →  ┤ │
│  ├ 退出登录（红色）       →  ┤ │
│  └──────────────────────────┘ │
└──────────────────────────────┘
```

### 组件一：个人信息区

| 属性 | 规格 |
|------|------|
| 头像 | 64dp 圆形，轻毛玻璃边框（半透明白 + 10% 模糊），极淡描边 |
| 昵称 | 20sp Bold，`#333333`，居中 |
| 在线标签 | 昵称右侧，12dp 圆角，在线 `#00B42A` / 离线 `#909399`，12sp |
| 点击 | 按压轻微缩放 → 跳转个人资料编辑页 |

### 组件二：功能列表

> 列表项统一样式：图标 + 文字，16dp 圆角，无分割线，`padding = 16dp`，白底轻微阴影。按压变暗 + 轻微震动。

---

**① 设备绑定与管理**

| 属性 | 规格 |
|------|------|
| 图标 | 设备类简约线性图标 |
| 文字 | 18sp，`#333333` |
| 点击 | 跳转设备管理子页面 |

**子页面 — 已绑定设备列表**（LazyColumn）：

| 属性 | 规格 |
|------|------|
| 设备卡片 | 16dp 圆角白底，轻毛玻璃边框，`padding = 16dp` |
| 设备名 | 16sp Bold |
| 设备状态 | 在线 `#00B42A` / 离线 `#909399`，标签式 |
| 设备 ID | 12sp `#909399` |
| 信号图标 | 14sp |
| 解绑按钮 | 卡片右侧，`#FF7D00` 文字 14sp → 点击 → 二次确认 → `POST /binding/unbind` |

**子页面 — 绑定新设备**（页面底部）：

| 按钮 | 样式 |
|------|------|
| 「扫码绑定」 | 16dp 圆角，`#165DFF` 填充，全屏宽，48dp 高，16sp 白色 Bold |
| 「手动输入设备ID」 | 16dp 圆角，`#F5F7FA` 底，全屏宽，48dp 高，16sp `#666666` |

点击后唤起扫码/输入框，调 `POST /api/v1/binding/initiate`。

---

**② 用户设置**

| 属性 | 规格 |
|------|------|
| 图标 | 设置类简约线性图标 |
| 文字 | 18sp，`#333333` |
| 点击 | 跳转账号设置子页面 |

**子页面内容**：

| 区域 | 说明 |
|------|------|
| 手机号显示 | 16sp `#333333`，下方标注"已绑定" 12sp 灰色 |
| 账号状态 | `#00B42A` 标签 |
| 修改密码按钮 | 12dp 圆角 `#E8F3FF` 底，14sp `#165DFF` |
| 修改密码表单 | 原密码 → 新密码 → 确认新密码，二次确认提交 |

---

**③ 退出登录**

| 属性 | 规格 |
|------|------|
| 图标 | 退出类简约线性图标 |
| 文字 | 18sp，`#F53F3F` |
| 点击 | 弹二次确认弹窗"确定退出当前账号？" |
| 确认按钮 | `#F53F3F` 填充，12dp 圆角，白色文字 |
| 取消按钮 | `#F5F7FA` 底，`#666666` 文字 |
| 确认后 | 调 `POST /api/v1/auth/logout` → 清除本地 Token → 跳转登录页 |

### 个人中心涉及的后端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/auth/logout` | POST | 退出登录 |
| `/api/v1/device/{deviceId}/search` | GET | 搜索设备 |
| `/api/v1/binding/initiate` | POST | 发起绑定 |
| `/api/v1/binding/unbind` | POST | 解绑 |

---

## 全局交互规则

> 封装为公共 Composable，各页面直接调用。

### 下拉刷新

> 所有列表页统一下拉刷新，含 400ms 最小动画时长。

| 属性 | 规格 |
|------|------|
| 组件 | `PullRefreshIndicator` + `pullRefresh` + `rememberPullRefreshState` |
| 依赖 | `androidx.compose.material:material` |
| 最小动画时长 | 400ms（`kotlinx.coroutines.delay(400)`） |
| 触发方式 | 列表顶部下拉 |
| 覆盖页面 | Home / AlertHistoryList / NotificationList / ElderManagement / DeviceManagement / Location / OcrMedicine |

### 按钮点击反馈

- 按压变暗：颜色加深 10%（`alpha = 0.9`）
- 震动反馈：`HapticFeedbackType.LongPress`，10ms

### 二次确认弹窗

> 所有删除、解绑、退出操作必须二次确认。

| 属性 | 规格 |
|------|------|
| 容器 | 16dp 圆角，轻毛玻璃半透明白底（20% 模糊），`padding = 20dp` |
| 确认按钮 | 状态色填充（红/橙），12dp 圆角，白色文字 |
| 取消按钮 | `#F5F7FA` 底色，`#666666` 文字 |
| 按钮间距 | 12dp |

| 场景 | 文案 |
|------|------|
| 解绑设备 | "确定解绑该设备？解绑后将无法接收数据" |
| 退出登录 | "确定退出当前账号？" |

### 无网络 / 空白状态

| 属性 | 规格 |
|------|------|
| 图标 | 低饱和配色，居中 |
| 文字 | 18sp Bold，居中 |
| 按钮 | "重试"，16dp 圆角，`PrimaryBlue` 填充 |

### 未读标记

| 属性 | 规格 |
|------|------|
| 形状 | 胶囊型 / 圆形 |
| 颜色 | `#F53F3F` 浅红底 + 白色文字/红点 |
| 动效 | 呼吸（`alpha 80%↔100%`，2s 循环），无闪烁 |

| 场景 | 尺寸 |
|------|------|
| 卡片右上角 | 最小宽 20dp，高 16dp，10sp 数字 |
| Tab 栏 | 圆形直径 18dp，仅红点无数字 |
| 消息列表 | 12sp "新消息"，12dp 圆角 |

---

## 附录 A：后端接口对照表

| 页面 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 首页 | `/api/v1/dashboard` | GET | 安全状态（`alertCount24h`） |
| 首页 | `/api/v1/alerts` | GET | 告警列表分页 |
| 首页 | `/api/v1/alerts/{alertId}` | GET | 告警详情 |
| 首页 | `/api/v1/alerts/{alertId}/read` | PUT | 标记已读 |
| 个人中心 | `/api/v1/auth/logout` | POST | 退出登录 |
| 个人中心 | `/api/v1/device/{deviceId}/search` | GET | 搜索设备 |
| 个人中心 | `/api/v1/binding/initiate` | POST | 发起绑定 |
| 个人中心 | `/api/v1/binding/unbind` | POST | 解绑 |

---

## 附录 B：可行性分析

> 逐项对照后端 + 硬件，标注实现状态。

### ✅ 可完全实现

| 页面 | 功能 | 后端接口 |
|------|------|:--:|
| 首页 | 安全状态文字（正常/告警 + 呼吸动效） | ✅ `dashboard` |
| 首页 | 新发告警卡片（条件渲染） | ✅ `alerts?status=pending` |
| 首页 | 告警历史列表（分页 LazyColumn） | ✅ `alerts` |
| 首页 | 告警详情子页面 | ✅ `alerts/{alertId}` |
| 首页 | 标记已读 | ✅ `alerts/{alertId}/read` |
| 个人中心 | 个人信息展示（昵称/头像/在线状态） | ✅ 本地 Token + SharedPreferences |
| 个人中心 | 设备列表展示 | ✅ `device/{id}/search` |
| 个人中心 | 发起绑定 | ✅ `binding/initiate` |
| 个人中心 | 解绑设备 | ✅ `binding/unbind` |
| 个人中心 | 退出登录 | ✅ `auth/logout` |

### ⚠️ 受限可实现（需外部依赖/权限）

| 功能 | 限制原因 | 解决方案 |
|------|----------|----------|
| 扫码绑定 | 需 CameraX + 相机权限 | 添加 `camera-camera2` 依赖，`AndroidManifest` 声明权限 |
| 未读标记数量 | 需轮询 `/alerts` 或 `/notifications` 计算 | APP 定时刷新或首次加载缓存未读计数 |

### ❌ 当前不能实现（后端无接口）

| DOCX 描述的功能 | 缺失接口 | 影响 |
|------|----------|------|
| **修改密码**（用户设置子页面） | 无 `POST /api/v1/auth/change-password` | 表单可做 UI，但无法提交到后端 |

### 建议

1. **首页（告警总览）优先开发** — 全部 6 项功能可直接对接后端
2. **个人中心其次** — 除修改密码外其余全部可对接
3. **修改密码**建议在 `backend/internal/handler/auth.go` 新增一个 `ChangePassword` handler，路由 `POST /api/v1/auth/change-password`，入参 `{oldPassword, newPassword}`，bcrypt 校验旧密码后更新

## 附录 C：实现状态（2026-05-05）

| 功能 | 状态 | 说明 |
|------|:--:|------|
| 全局设计规范（色值/圆角/字体/卡片/按钮） | ✅ | AppColors.kt + Theme.kt + Type.kt 全部对齐 DOCX |
| DOCX 配色全面应用 | ✅ | CardBackground 页底色 + White 卡片 + SoftPink/SoftGreen/SoftOrange 状态色 |
| 底部 4 Tab 导航 | ✅ | VisionHubDestination 枚举 4 项，子页面自动隐藏 |
| 首页 — 设备连接状态卡片 | ✅ | GET /dashboard → onlineDeviceCount + elderCount + alertCount24h |
| 首页 — 新发告警卡片（条件渲染） | ✅ | GET /alerts?status=pending，SoftPink 底色 |
| 首页 — 最近告警列表 | ✅ | GET /alerts?page=1&size=3（不重复底部告警历史 Tab） |
| 首页 — 告警详情子页面 | ✅ | GET /alerts/{alertId} + 时间线 |
| 首页 — 「忽视」标记已读 | ✅ | PUT /alert/{alertId}/status |
| 个人中心 — 个人信息区 | ✅ | 头像+动态昵称(AuthTokenHolder)+手机号+在线标签 |
| 个人中心 — 设备绑定与管理子页面 | ✅ | 扫码(ZXing ScanContract)+从相册选图+手动输入ID，三种方式绑定设备 |
| 个人中心 — 用户设置子页面 | ✅ | 手机号(AuthTokenHolder)+修改密码接 POST /auth/change-password 真实 API |
| 个人中心 — 退出登录 | ✅ | 二次确认弹窗+POST /auth/logout |
| 个人中心 — 我的老人子页面 | ✅ | 新增 ElderManagementScreen：老人列表+创建档案弹窗（POST /elder） |
| 个人中心 — 编辑资料 | ✅ | 昵称修改 AlertDialog → PUT /user/profile → 更新 AuthTokenHolder + 持久化 |
| 定位用药页 — 美化版 | ✅ | 圆形容器图标+功能标签+CardBackground 配色 |
| 告警历史页 | ✅ | CardBackground 配色+LazyColumn+分页 |
| 全局组件 — AppButton | ✅ | 主按钮/次要按钮/危险按钮 |
| 全局组件 — AppConfirmDialog | ✅ | 16dp 圆角+确认/取消 |
| 全局组件 — EmptyState | ✅ | 图标+文字+重试按钮 |
| 全局组件 — UnreadBadge | ✅ | 胶囊型+呼吸动效 |
| 登录/注册 — 密码可见切换 | ✅ | 小眼睛 IconButton + ErrorHelper 详细错误提示 |
| 登录/注册 — 手机号登录 | ✅ | 后端 WHERE username OR phone，LoginResponse 返回 display_name + phone |
| 登录响应格式对齐后端 | ✅ | LoginRawResponse flat JSON + errorBody 解析 |
| 注册自动登录 | ✅ | RegisterScreen 调用 register → login → 获取 token |
| 闪退修复 | ✅ | SubScreen 从 rememberSaveable 改为 remember |
| 16KB 内存对齐兼容性 | ✅ | useLegacyPackaging = true |
| Release APK 签名打包 | ✅ | visionhub-release2.keystore + assembleRelease → VisionGuard-v1.0.apk（103MB） |
