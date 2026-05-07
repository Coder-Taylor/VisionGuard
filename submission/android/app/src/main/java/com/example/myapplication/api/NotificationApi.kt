package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 十、消息推送与通知 — 8 路由 (UserAuth)
 */
interface NotificationApi {

    @GET("api/v1/notifications")
    suspend fun listNotifications(
        @Query("type") type: String? = null,
        @Query("readStatus") read: String? = null,   // unread / read
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ApiResponse<PaginatedData<NotificationData>>>

    @PUT("api/v1/notifications/read")
    suspend fun markRead(@Body req: MarkReadReq): Response<ApiResponse<Any?>>

    @PUT("api/v1/notifications/read-all")
    suspend fun markAllRead(): Response<ApiResponse<Any?>>

    @GET("api/v1/notification/push-rules")
    suspend fun getPushRules(): Response<ApiResponse<Map<String, Any?>>>

    @POST("api/v1/notification/push-targets")
    suspend fun getPushTargets(@Body req: PushTargetsReq): Response<ApiResponse<PushTargetsData>>

    @POST("api/v1/notification/push")
    suspend fun sendPush(@Body req: SendPushReq): Response<ApiResponse<Any?>>

    @GET("api/v1/notification/status/{messageId}")
    suspend fun getPushStatus(@Path("messageId") messageId: String): Response<ApiResponse<PushStatusData>>

    @GET("api/v1/notification/priority-config")
    suspend fun getPriorityConfig(): Response<ApiResponse<Map<String, Any?>>>
}

// ---- Request types ----

data class MarkReadReq(val messageIds: List<String>)
data class PushTargetsReq(val alertLevel: String, val elderId: String?, val deviceId: String?)
data class SendPushReq(
    val userId: String?,
    val title: String,
    val body: String,
    val channel: String? = "app",  // app / sms / voice_call
    val priority: String? = "P1",
    val data: Map<String, Any?>? = null,
)

// ---- Response types ----

data class NotificationData(
    val messageId: String?,
    val type: String?,
    val title: String?,
    val body: String?,
    val channel: String?,
    val priority: String?,
    val read: Boolean?,
    val deliveryStatus: String?,
    val createdAt: String?,
)

data class PushTargetsData(
    val targets: List<PushTarget>?,
)

data class PushTarget(
    val userId: String?,
    val channel: String?,       // app / sms / voice_call
    val phone: String?,
    val priority: String?,      // P0-P3
)

data class PushStatusData(
    val messageId: String?,
    val status: String?,        // pending / sent / delivered / failed
    val channel: String?,
    val sentAt: String?,
)
