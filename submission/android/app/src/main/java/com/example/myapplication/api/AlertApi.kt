package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 七、告警事件管理 — 8 路由
 */
interface AlertApi {

    @GET("api/v1/alert/types")
    suspend fun getAlertTypes(): Response<ApiResponse<List<AlertTypeData>>>

    @POST("api/v1/alert")
    suspend fun createAlert(@Body req: CreateAlertReq): Response<ApiResponse<AlertData>>

    @GET("api/v1/alerts")
    suspend fun listAlerts(
        @Query("elderId") elderId: String? = null,
        @Query("alertType") alertType: String? = null,
        @Query("level") level: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ApiResponse<PaginatedData<AlertData>>>

    @GET("api/v1/alert/statistics")
    suspend fun getAlertStatistics(
        @Query("elderId") elderId: String? = null,
        @Query("period") period: String = "day",
    ): Response<ApiResponse<AlertStatisticsData>>

    @GET("api/v1/alert/level-config")
    suspend fun getLevelConfig(): Response<ApiResponse<Map<String, Any?>>>

    @GET("api/v1/alert/{alertId}")
    suspend fun getAlertDetail(@Path("alertId") alertId: String): Response<ApiResponse<AlertDetailData>>

    @PUT("api/v1/alert/{alertId}/status")
    suspend fun updateAlertStatus(@Path("alertId") alertId: String, @Body req: UpdateAlertStatusReq): Response<ApiResponse<Any?>>

    @POST("api/v1/alert/{alertId}/resolve")
    suspend fun resolveAlert(@Path("alertId") alertId: String, @Body req: ResolveAlertReq): Response<ApiResponse<Any?>>

    // 兼容旧屏幕代码 (use listAlerts instead)
    @Deprecated("使用 listAlerts")
    @GET("api/v1/alerts")
    suspend fun getAlerts(
        @Query("userId") userId: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): retrofit2.Response<AlertsResponse>
}

// ---- Request types ----

data class CreateAlertReq(
    val deviceId: String,
    val alertType: String,       // fall / obstacle / sos / heart_rate / low_battery / offline / geofence
    val alertLevel: String?,
    val angleX: Double?,
    val angleY: Double?,
    val lidarDist: Int?,
    val lat: Double?,
    val lng: Double?,
    val heartRate: Int?,
    val message: String?,
)

data class UpdateAlertStatusReq(val action: String)  // confirm / resolve / close（对齐后端 json:"action"）
data class ResolveAlertReq(val resolution: String)

// ---- Response types ----

data class AlertTypeData(
    val type: String,
    val name: String?,
    val description: String?,
    val defaultLevel: String?,
    val dedupWindowSec: Int?,
)

data class AlertData(
    val alertId: String?,
    val deviceId: String?,
    val elderId: String?,
    val alertType: String?,
    val alertLevel: String?,
    val status: String?,
    val duplicateCount: Int?,
    val description: String?,
    val createdAt: String?,
)

data class AlertDetailData(
    val alertId: String?,
    val deviceId: String?,
    val elderId: String?,
    val alertType: String?,
    val alertLevel: String?,
    val status: String?,
    val duplicateCount: Int?,
    val resolution: String?,
    val createdAt: String?,
    val description: String?,
    val confirmedAt: String?,
    val resolvedAt: String?,
    val closedAt: String?,
    val timeline: List<Map<String, Any?>>?,
    val device: Map<String, Any?>?,
    val elder: Map<String, Any?>?,
)

data class AlertStatisticsData(
    val total: Int?,
    val byType: Map<String, Int>?,
    val byLevel: Map<String, Int>?,
    val byStatus: Map<String, Int>?,
)
