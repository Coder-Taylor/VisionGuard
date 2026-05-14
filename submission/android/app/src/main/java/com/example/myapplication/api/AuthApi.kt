package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 一、认证服务 — 9 路由
 * 包含：设备 challenge-response 认证 + 用户注册/登录/Token刷新/登出
 */
interface AuthApi {

    // 设备请求挑战 (XOR 0x4B, Redis TTL 5min)
    @POST("api/v1/device/challenge")
    suspend fun deviceChallenge(@Body req: ChallengeReq): Response<ApiResponse<ChallengeData>>

    // 设备提交签名验证，返回设备 JWT (24h)
    @POST("api/v1/device/verify")
    suspend fun deviceVerify(@Body req: VerifyReq): Response<ApiResponse<VerifyData>>

    // 设备首次接入注册（记录 IP，注意：ip 字段由服务端 c.IP() 填入）
    @POST("api/v1/device/register")
    suspend fun deviceRegister(@Body req: DeviceRegisterReq): Response<ApiResponse<Any?>>

    // 记录设备基础信息（型号/固件版本）
    @POST("api/v1/device/info")
    suspend fun deviceInfo(@Body req: DeviceInfoReq): Response<ApiResponse<Any?>>

    // 记录设备认证事件日志
    @POST("api/v1/device/log")
    suspend fun deviceLog(@Body req: DeviceLogReq): Response<ApiResponse<Any?>>

    // 用户注册（bcrypt + 8位最小密码 + 重复检查）
    @POST("api/v1/auth/register")
    suspend fun register(@Body req: RegisterReq): Response<ApiResponse<AuthData>>

    // 登录（JWT 1h + refresh 30d, 8次失败锁账户）
    // 后端返回 flat JSON: {access_token, refresh_token, expires_in}，非 ApiResponse 包裹
    @POST("api/v1/auth/login")
    suspend fun login(@Body req: LoginReq): Response<LoginRawResponse>

    // 刷新 access_token（翻转安全：先创建新后删除旧）
    // 后端返回 flat JSON: {access_token, refresh_token, expires_in}，非 ApiResponse 包裹
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body req: RefreshReq): Response<LoginRawResponse>

    // 登出（删除 refresh_token）
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body req: LogoutReq): Response<ApiResponse<Any?>>

    companion object {
        // 保留兼容队友旧路径
        const val PATH_RESET_PASSWORD = "api/v1/auth/reset-password"
    }

    @POST(PATH_RESET_PASSWORD)
    suspend fun resetPassword(@Body req: ResetPasswordReq): Response<ApiResponse<Any?>>

    // 修改密码
    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body req: ChangePasswordReq): Response<ApiResponse<Any?>>

    // ---- 兼容旧屏幕代码 ----

    @Deprecated("使用 login(LoginReq) 代替")
    @POST("api/v1/auth/login")
    suspend fun loginOld(@Body req: LoginRequest): retrofit2.Response<AuthResponse>

    @Deprecated("使用 register(RegisterReq) 代替")
    @POST("api/v1/auth/register")
    suspend fun registerOld(@Body req: RegisterRequest): retrofit2.Response<AuthResponse>

    @Deprecated("使用 resetPassword(ResetPasswordReq) 代替")
    @POST("api/v1/auth/reset-password")
    suspend fun resetPasswordOld(@Body req: ResetPasswordRequest): retrofit2.Response<BaseResponse>

    // FCM token 上报
    @POST("api/v1/auth/fcm-token")
    suspend fun uploadFcmToken(@Body req: FcmTokenRequest): Response<ApiResponse<Any?>>
}

data class ResetPasswordReq(val phone: String, val password: String, val newPassword: String)

data class ChangePasswordReq(val oldPassword: String, val newPassword: String)

// 后端登录/刷新返回的 flat JSON（非 ApiResponse 包裹）
data class LoginRawResponse(
    val access_token: String?,
    val refresh_token: String?,
    val expires_in: Int?,
    val display_name: String?,
    val phone: String?,
)
