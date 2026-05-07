package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 八、定位与设备状态展示 (7 路由) + 六、设备数据接收与存储 (2 路由)
 */
interface LocationApi {

    // ---- 定位 ----

    @GET("api/v1/location/latest")
    suspend fun getLatestLocation(
        @Query("deviceId") deviceId: String? = null,
        @Query("elderId") elderId: String? = null,
    ): Response<ApiResponse<LocationData>>

    @GET("api/v1/location/trajectory")
    suspend fun getTrajectory(
        @Query("deviceId") deviceId: String? = null,
        @Query("elderId") elderId: String? = null,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 500,
    ): Response<ApiResponse<PaginatedData<LocationData>>>

    @GET("api/v1/location/alert-markers")
    suspend fun getAlertMarkers(
        @Query("elderId") elderId: String? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("alertType") alertType: String? = null,
    ): Response<ApiResponse<List<AlertMarkerData>>>

    @GET("api/v1/device/{deviceId}/running")
    suspend fun getDeviceRunningData(@Path("deviceId") deviceId: String): Response<ApiResponse<RunningData>>

    // ---- 电子围栏 ----

    @POST("api/v1/geofence")
    suspend fun createGeofence(@Body req: CreateGeofenceReq): Response<ApiResponse<Any?>>

    @GET("api/v1/geofences")
    suspend fun listGeofences(@Query("elderId") elderId: String? = null): Response<ApiResponse<List<GeofenceData>>>

    @DELETE("api/v1/geofence/{fenceId}")
    suspend fun deleteGeofence(@Path("fenceId") fenceId: String): Response<ApiResponse<Any?>>

    // ---- 健康数据 ----

    @POST("api/v1/data/health")
    suspend fun uploadHealthData(@Body req: HealthDataReq): Response<ApiResponse<Any?>>

    @GET("api/v1/data/health")
    suspend fun getHealthData(
        @Query("deviceId") deviceId: String? = null,
        @Query("elderId") elderId: String? = null,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): Response<ApiResponse<PaginatedData<HealthRecord>>>
}

// ---- Request types ----

data class CreateGeofenceReq(
    val elderId: String,
    val fenceName: String,
    val fenceType: String,       // circle / polygon
    val centerLat: Double?,
    val centerLng: Double?,
    val radius: Int?,            // 米
    val vertices: List<List<Double>>?,
)

data class HealthDataReq(
    val deviceId: String?,
    val elderId: String?,
    val type: String,            // heart_rate / blood_pressure / steps / spo2
    val value: Double,
    val unit: String?,
)

// ---- Response types ----

data class LocationData(
    val dataId: String?,
    val deviceId: String?,
    val elderId: String?,
    val lat: Double?,
    val lng: Double?,
    val accuracy: Double?,
    val speed: Double?,
    val heading: Double?,
    val createdAt: String?,
)

data class AlertMarkerData(
    val alertId: String?,
    val alertType: String?,
    val lat: Double?,
    val lng: Double?,
    val createdAt: String?,
)

data class RunningData(
    val battery: Int?,
    val rssi: Int?,
    val storageUsed: Long?,
    val uptime: Long?,
    val cpuTemp: Double?,
)

data class GeofenceData(
    val fenceId: String?,
    val elderId: String?,
    val fenceName: String?,
    val fenceType: String?,
    val centerLat: Double?,
    val centerLng: Double?,
    val radius: Int?,
    val vertices: List<List<Double>>?,
    val enabled: Boolean?,
    val createdAt: String?,
)

data class HealthRecord(
    val dataId: String?,
    val deviceId: String?,
    val elderId: String?,
    val type: String?,
    val value: Double?,
    val unit: String?,
    val createdAt: String?,
)
