package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 三、设备接入 (8 路由) + 四、心跳管理 (5 路由) + 五、绑定管理 (7 路由)
 * 共 20 路由，按认证方式分 DeviceAuth / UserAuth / 公开
 */
interface DeviceApi {

    // ===== 设备接入 (公开) =====

    @POST("api/v1/device/activate")
    suspend fun activate(@Body req: DeviceActivateReq): Response<ApiResponse<DeviceActivateData>>

    @POST("api/v1/device/auth")
    suspend fun auth(@Body req: DeviceAuthReq): Response<ApiResponse<DeviceAuthData>>

    // ===== 设备管理 (DeviceAuth) =====

    @PUT("api/v1/device/{deviceId}")
    suspend fun updateDevice(@Path("deviceId") deviceId: String, @Body req: DeviceUpdateReq): Response<ApiResponse<Any?>>

    @POST("api/v1/device/{deviceId}/toggle")
    suspend fun toggleDevice(@Path("deviceId") deviceId: String, @Body req: DeviceToggleReq): Response<ApiResponse<Any?>>

    @GET("api/v1/device/{deviceId}/firmware")
    suspend fun checkFirmware(@Path("deviceId") deviceId: String): Response<ApiResponse<Any?>>

    @POST("api/v1/device/{deviceId}/data")
    suspend fun reportDeviceData(@Path("deviceId") deviceId: String, @Body req: DeviceDataReq): Response<ApiResponse<Any?>>

    // ===== 心跳管理 =====

    @POST("api/v1/device/heartbeat")
    suspend fun heartbeat(@Body req: HeartbeatData): Response<ApiResponse<Any?>>

    @GET("api/v1/device/status/{deviceId}")
    suspend fun getDeviceStatus(@Path("deviceId") deviceId: String): Response<ApiResponse<DeviceStatusData>>

    @GET("api/v1/device/{deviceId}/last-online")
    suspend fun getLastOnline(@Path("deviceId") deviceId: String): Response<ApiResponse<Map<String, Any?>>>

    @POST("api/v1/devices/batch-status")
    suspend fun batchDeviceStatus(@Body req: BatchStatusReq): Response<ApiResponse<List<DeviceStatusData>>>

    // ===== 设备搜索与绑定 =====

    @GET("api/v1/device/{deviceId}/search")
    suspend fun searchDevice(@Path("deviceId") deviceId: String): Response<ApiResponse<SearchDeviceData>>

    @POST("api/v1/binding/initiate")
    suspend fun initiateBinding(@Body req: InitiateBindingReq): Response<ApiResponse<Any?>>

    @POST("api/v1/binding/confirm")
    suspend fun confirmBinding(@Body req: ConfirmBindingReq): Response<ApiResponse<Any?>>

    @POST("api/v1/binding/check")
    suspend fun checkBinding(@Body req: CheckBindingReq): Response<ApiResponse<Any?>>

    @POST("api/v1/binding/unbind")
    suspend fun unbindDevice(@Body req: UnbindReq): Response<ApiResponse<Any?>>

    @POST("api/v1/binding/rebind")
    suspend fun rebindDevice(@Body req: RebindReq): Response<ApiResponse<Any?>>

    @GET("api/v1/device/{deviceId}/binding")
    suspend fun getDeviceBinding(@Path("deviceId") deviceId: String): Response<ApiResponse<Any?>>

    // ===== 兼容旧屏幕代码 =====

    @Deprecated("使用 searchDevice + binding 系列")
    @GET("api/v1/devices/online")
    suspend fun listOnlineDevices(): retrofit2.Response<OnlineDevicesResponse>

    @Deprecated("使用 initiateBinding")
    @POST("api/v1/user/{userId}/devices/bind")
    suspend fun bindDevice(@Path("userId") userId: String, @Body req: BindDeviceRequest): retrofit2.Response<BaseResponse>

    @Deprecated("使用 unbindDevice")
    @POST("api/v1/user/{userId}/devices/unbind")
    suspend fun unbindDevice(@Path("userId") userId: String, @Body req: UnbindDeviceRequest): retrofit2.Response<BaseResponse>

    @Deprecated("使用 getDeviceBinding")
    @GET("api/v1/user/{userId}/devices/bound")
    suspend fun getBoundDevice(@Path("userId") userId: String): retrofit2.Response<BoundDeviceResponse>
}

// ---- Request/Response types ----

data class BatchStatusReq(val deviceIds: List<String>)
data class InitiateBindingReq(val deviceId: String, val elderId: String)
data class ConfirmBindingReq(val deviceId: String, val bindId: String?)
data class CheckBindingReq(val deviceId: String, val elderId: String?)
data class UnbindReq(val deviceId: String, val elderId: String?)
data class RebindReq(val deviceId: String, val fromElderId: String?, val toElderId: String)

data class SearchDeviceData(val canBind: Boolean, val bindStatus: String?, val elderId: String?)

data class BindDeviceLegacyReq(val deviceId: String)
data class UnbindDeviceLegacyReq(val deviceId: String)

data class OnlineDeviceData(
    val deviceId: String?,
    val alias: String?,
    val model: String?,
    val online: Boolean?,
    val bindStatus: String?,
    val battery: Int?,
    val lastHeartbeat: String?,
)
