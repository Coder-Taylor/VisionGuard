# VisionGuard Android 监护端

## 构建

```bash
# 1. 复制配置
cp local.properties.example local.properties
# 编辑 local.properties，填入:
#   - sdk.dir (Android SDK 路径)
#   - AMAP_API_KEY (高德地图 Key)

# 2. 修改服务器地址（如需）
# 编辑 app/src/main/java/.../api/RetrofitClient.kt
# ApiConfig.BASE_URL:
#   本地联调: http://127.0.0.1:3000/  (配合 adb reverse)
#   云服务器: http://47.94.146.53/vg/

# 3. 构建
./gradlew :app:assembleRelease
# APK 输出: app/build/outputs/apk/release/app-release.apk
```

## 预构建 APK

`apk/VisionGuard-v1.5-cloud.apk` 已配置连接云服务器 `http://47.94.146.53/vg/`，可直接安装。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- 高德地图 SDK 10.0.600
- Retrofit + OkHttp + Gson
- ZXing 二维码扫描
- JDK 17, minSdk 35

## 页面结构

18 个页面：Login / Register / ForgotPassword / Home / AlertHistory / AlertDetail / Map / Location / OcrMedicine / DeviceManagement / ElderManagement / ElderDetail / Profile / UserSettings / NotificationList / PositionMedicine
