# 今日 TODO — 2026-05-09

## Step 1: 项目脚手架 ✅
- [x] Vite + React + TS 项目创建
- [x] 安装依赖 (axios, react-router-dom, tailwindcss, @tailwindcss/vite)
- [x] Tailwind 自定义色板（对齐 AppColors.kt）
- [x] Vite proxy 配置
- [x] 后端 CORS 中间件（backend/ + submission/）

## Step 2: 认证体系 ✅
- [x] TypeScript 类型定义
- [x] axios 客户端 + JWT 拦截器 + 自动刷新
- [x] AuthContext（登录状态 + token 持久化）
- [x] LoginPage / RegisterPage / ForgotPasswordPage

## Step 3: 核心框架 ✅
- [x] Layout + BottomNav（4 Tab）
- [x] App.tsx 完整路由表（17 页）
- [x] 通用组件：AppButton / StatusTag / EmptyState / LoadingSpinner / CompactTopBar / ConfirmDialog / UnreadBadge

## Step 4: 页面开发 ✅
- [x] 全部 API 文件（elder/alert/notification/device/ocr/location/medication/user）
- [x] HomePage（仪表盘 + 统计卡片 + 告警列表 + 一键忽视）
- [x] AlertHistoryPage（分页列表 + 类型标签 + 状态标签）
- [x] AlertDetailPage（详情 + 时间线 + 确认/解决/关闭）
- [x] ProfilePage（用户信息 + 菜单 + 退出登录）
- [x] PositionMedicinePage（定位+用药卡片入口）
- [x] 其余子页面占位符（待后续完善）

## Step 5: 收尾 🔄
- [ ] CLAUDE.md 写入网页版规则
- [ ] 构建验证 ✅
- [ ] 提交 + 推送 Gitee

---

今日目标达成：脚手架+认证+核心框架+核心页面 ✅
明日继续：完善其余页面（定位/地图/OCR/用药计划/设备管理/老人管理/通知/设置）
