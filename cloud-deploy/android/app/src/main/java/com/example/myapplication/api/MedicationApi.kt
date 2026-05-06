package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 十一、用药计划与管理 — 4 路由 (UserAuth)
 */
interface MedicationApi {

    @POST("api/v1/medication/plan")
    suspend fun createPlan(@Body req: CreatePlanReq): Response<ApiResponse<MedicationPlanData>>

    @GET("api/v1/medication/plans/{elderId}")
    suspend fun listPlans(@Path("elderId") elderId: String): Response<ApiResponse<PlanListData>>

    @PUT("api/v1/medication/plan/{planId}")
    suspend fun updatePlan(
        @Path("planId") planId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>,
    ): Response<ApiResponse<Any?>>

    @DELETE("api/v1/medication/plan/{planId}")
    suspend fun deletePlan(@Path("planId") planId: String): Response<ApiResponse<Any?>>
}

// ---- Request types ----

data class CreatePlanReq(
    val elderId: String,
    val drugName: String,
    val dosage: String,
    val frequency: String,
    val schedule: String,       // JSON: ["08:00","12:00","18:00"]
    val startDate: String,      // 2026-05-06
    val endDate: String?,
    val notes: String?,
)

// ---- Response types ----

data class PlanListData(
    val list: List<MedicationPlanData>?,
)

data class MedicationPlanData(
    val planId: String?,
    val elderId: String?,
    val drugName: String?,
    val dosage: String?,
    val frequency: String?,
    val schedule: String?,
    val startDate: String?,
    val endDate: String?,
    val notes: String?,
    val status: String?,
    val createdAt: String?,
)
