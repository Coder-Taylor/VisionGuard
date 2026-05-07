package com.example.myapplication.api

/**
 * 后端统一响应格式: {code, message, data}
 * code=0 成功, code>0 错误 (映射 HTTP 状态码)
 */

// ---------- 通用响应 ----------

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?,
)

data class PaginatedData<T>(
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val list: List<T>,
)

// ---------- 认证 ----------

data class LoginReq(val username: String, val password: String)
data class RegisterReq(val username: String, val password: String, val email: String?, val phone: String?)
data class RefreshReq(@com.google.gson.annotations.SerializedName("refresh_token") val refreshToken: String)
data class LogoutReq(@com.google.gson.annotations.SerializedName("refresh_token") val refreshToken: String)
data class FcmTokenReq(val userId: String, val fcmToken: String)

data class AuthData(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Int?,        // 秒
    val userId: String?,
    val displayName: String?,
)

// ---------- 设备认证 ----------

data class ChallengeReq(val deviceId: String)
data class ChallengeData(
    val challengeId: String,
    val nonce: String,
    val timestamp: Long,
)

data class VerifyReq(val deviceId: String, val challengeId: String, val sigin: String)
data class VerifyData(val jwt: String, val deviceId: String)

data class DeviceRegisterReq(val deviceId: String, val deviceModel: String?, val firmwareVersion: String?)
data class DeviceInfoReq(val deviceId: String, val deviceModel: String?, val fwVersion: String?)
data class DeviceLogReq(val deviceId: String, val logType: String, val message: String?)

data class DeviceActivateReq(val deviceId: String, val serialNo: String?, val model: String?, val mac: String?)
data class DeviceActivateData(val deviceSecret: String, val certificate: String?)

data class DeviceAuthReq(val deviceId: String, val deviceSecret: String)
data class DeviceAuthData(val jwt: String, val bindStatus: String?, val elderId: String?)

data class DeviceUpdateReq(val alias: String?, val installLocation: String?)
data class DeviceToggleReq(val reason: String?)
data class DeviceDataReq(val dataType: String, val payload: Map<String, Any?>)

data class HeartbeatData(
    val battery: Int?,
    val rssi: Int?,
    val lat: Double?,
    val lng: Double?,
)

data class DeviceStatusData(
    val deviceId: String,
    val online: Boolean,
    val lastHeartbeat: String?,
    val onlineDurationSec: Long?,
    val battery: Int?,
)

// ===== 兼容旧版 data class（队友 screen 代码引用, 字段名保持原样） =====

// 旧 AlertRecord (字段非 null 兼容屏幕直接使用)
data class AlertRecord(
    val id: String = "",
    val type: String = "",
    val timestamp: Long = 0,
    val detail: String = "",
    val deviceId: String = "",
)

// 旧 OnlineDevice (字段非 null 兼容屏幕直接使用)
data class OnlineDevice(
    val deviceId: String = "",
    val deviceName: String = "",
    val ipAddress: String = "",
)

data class BindDeviceRequest(val deviceId: String)
data class UnbindDeviceRequest(val deviceId: String)

data class LoginRequest(val phone: String, val password: String)
data class RegisterRequest(val phone: String, val password: String, val displayName: String)
data class ResetPasswordRequest(val phone: String, val verifyCode: String, val newPassword: String)
data class FcmTokenRequest(val userId: String, val fcmToken: String)

data class AuthResponse(
    val success: Boolean, val message: String?,
    val token: String?, val userId: String?, val displayName: String?,
)

data class BaseResponse(val success: Boolean, val message: String?)

data class OnlineDevicesResponse(val success: Boolean, val devices: List<OnlineDevice>?)

data class BoundDeviceResponse(val success: Boolean, val message: String?, val device: OnlineDevice?)

// ElderlyProfileRequest = ElderlyProfile (旧屏幕代码引用, 同一类型)
typealias ElderlyProfileRequest = com.example.myapplication.ElderlyProfile

data class ElderlyProfileResponse(val success: Boolean, val message: String?, val data: com.example.myapplication.ElderlyProfile?)

data class AlertsResponse(val success: Boolean, val message: String?, val data: List<AlertRecord>?)
