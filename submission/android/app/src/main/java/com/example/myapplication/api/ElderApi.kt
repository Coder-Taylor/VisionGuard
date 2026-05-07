package com.example.myapplication.api

import com.example.myapplication.ElderlyProfile
import retrofit2.Response
import retrofit2.http.*

/**
 * 二、老人档案与监护关系 — 15 路由 (UserAuth)
 */
interface ElderApi {

    // ---- 老人档案 CRUD ----

    @POST("api/v1/elder")
    suspend fun createElder(@Body req: CreateElderReq): Response<ApiResponse<ElderData>>

    @GET("api/v1/elder/{elderId}")
    suspend fun getElder(@Path("elderId") elderId: String): Response<ApiResponse<ElderData>>

    @PUT("api/v1/elder/{elderId}")
    suspend fun updateElder(@Path("elderId") elderId: String, @Body req: UpdateElderReq): Response<ApiResponse<Any?>>

    @DELETE("api/v1/elder/{elderId}")
    suspend fun deleteElder(@Path("elderId") elderId: String): Response<ApiResponse<Any?>>

    @POST("api/v1/elder/{elderId}/archive")
    suspend fun archiveElder(@Path("elderId") elderId: String): Response<ApiResponse<Any?>>

    // 绑定设备到老人
    @POST("api/v1/elder/{elderId}/bind")
    suspend fun bindDeviceToElder(@Path("elderId") elderId: String, @Body req: BindElderDeviceReq): Response<ApiResponse<Any?>>

    // ---- 监护关系 ----

    @POST("api/v1/elder/{elderId}/guardian/invite")
    suspend fun inviteGuardian(@Path("elderId") elderId: String, @Body req: InviteGuardianReq): Response<ApiResponse<Any?>>

    @POST("api/v1/elder/{elderId}/guardian/accept")
    suspend fun acceptGuardianInvite(@Path("elderId") elderId: String, @Body req: AcceptInviteReq): Response<ApiResponse<Any?>>

    @DELETE("api/v1/elder/{elderId}/guardian/{userId}")
    suspend fun removeGuardian(@Path("elderId") elderId: String, @Path("userId") userId: String): Response<ApiResponse<Any?>>

    // ---- 主监护人转让 ----

    @POST("api/v1/elder/{elderId}/primary/transfer")
    suspend fun transferPrimary(@Path("elderId") elderId: String, @Body req: TransferReq): Response<ApiResponse<Any?>>

    @POST("api/v1/elder/{elderId}/primary/confirm")
    suspend fun confirmTransfer(@Path("elderId") elderId: String): Response<ApiResponse<Any?>>

    // ---- 紧急联系人 ----

    @POST("api/v1/elder/{elderId}/emergency-contact")
    suspend fun addEmergencyContact(@Path("elderId") elderId: String, @Body req: EmergencyContactReq): Response<ApiResponse<Any?>>

    @DELETE("api/v1/elder/{elderId}/emergency-contact/{contactId}")
    suspend fun deleteEmergencyContact(@Path("elderId") elderId: String, @Path("contactId") contactId: String): Response<ApiResponse<Any?>>

    // ---- 列表与仪表盘 ----

    @GET("api/v1/elders")
    suspend fun listMyElders(): Response<ApiResponse<List<ElderData>>>

    @GET("api/v1/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardData>>

    // ---- 兼容旧屏幕代码 ----

    @Deprecated("使用 createElder/updateElder")
    @POST("api/v1/user/{userId}/elderly-profile")
    suspend fun saveElderlyProfile(@Path("userId") userId: String, @Body profile: ElderlyProfile): retrofit2.Response<BaseResponse>

    @Deprecated("使用 getElder")
    @GET("api/v1/user/{userId}/elderly-profile")
    suspend fun getElderlyProfile(@Path("userId") userId: String): retrofit2.Response<ElderlyProfileResponse>
}

// ---- Request types ----

data class CreateElderReq(
    val name: String,
    val gender: String?,
    val birthDate: String?,
    val bloodType: String?,
    val allergy: String?,
    val medicalHistory: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
)

data class UpdateElderReq(
    val name: String?,
    val gender: String?,
    val birthDate: String?,
    val bloodType: String?,
    val allergy: String?,
    val medicalHistory: String?,
)

data class BindElderDeviceReq(val deviceId: String)

data class InviteGuardianReq(val phone: String?, val email: String?)

data class AcceptInviteReq(val phone: String?, val email: String?)

data class TransferReq(val toUserId: String)

data class EmergencyContactReq(val name: String, val relation: String?, val phone: String)

// ---- Response types ----

data class ElderData(
    val elderId: String?,
    val name: String?,
    val gender: String?,
    val birthDate: String?,
    val bloodType: String?,
    val allergy: String?,
    val medicalHistory: String?,
    val status: String?,
    val createdAt: String?,
    val deviceOnline: Boolean?,
    val deviceId: String?,
    val guardians: List<GuardianInfo>?,
    val emergencyContacts: List<EmergencyContactInfo>?,
)

data class GuardianInfo(val userId: String?, val nickname: String?, val role: String?)
data class EmergencyContactInfo(val contactId: String?, val name: String?, val relation: String?, val phone: String?)

data class DashboardData(
    val elderCount: Int?,
    val alertCount24h: Int?,
    val onlineDeviceCount: Int?,
    val recentAlerts: List<Map<String, Any?>>?,
    val emergencyContacts: List<Map<String, Any?>>?,
)
