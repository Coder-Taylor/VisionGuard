package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 九、药品识别与智能建议 — 7 路由 (UserAuth)
 */
interface OcrApi {

    @POST("api/v1/ocr/image")
    @retrofit2.http.Headers("Content-Type: application/json")
    suspend fun uploadImageJson(@Body req: OcrUploadReq): Response<ApiResponse<OcrImageData>>

    @POST("api/v1/ocr/recognize")
    suspend fun createOcrTask(@Body req: RecognizeReq): Response<ApiResponse<OcrTaskData>>

    @GET("api/v1/ocr/result/{taskId}")
    suspend fun getOcrResult(@Path("taskId") taskId: String): Response<ApiResponse<OcrResultData>>

    @GET("api/v1/ocr/poll/{taskId}")
    suspend fun pollOcrTask(@Path("taskId") taskId: String): Response<ApiResponse<OcrProgressData>>

    @POST("api/v1/ocr/suggestion")
    suspend fun generateSuggestion(@Body req: SuggestionReq): Response<ApiResponse<SuggestionData>>

    @POST("api/v1/ocr/feedback")
    suspend fun submitFeedback(@Body req: FeedbackReq): Response<ApiResponse<Any?>>

    @GET("api/v1/ocr/records")
    suspend fun getOcrRecords(
        @Query("elderId") elderId: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ApiResponse<PaginatedData<OcrRecordData>>>
}

// ---- Request types ----

data class RecognizeReq(
    val imageId: String,
    val deviceId: String?,
    val elderId: String?,
)

data class SuggestionReq(
    val taskId: String,
    val elderId: String?,
)

data class FeedbackReq(
    val taskId: String,
    val rating: Int?,
    val comment: String?,
    val correctOcrText: String?,
)

// ---- Response types ----

data class OcrUploadReq(
    val elderId: String?,
    val imageCategory: String = "medicine",
    val fileUrl: String,
    val fileSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = "jpeg",
)

data class OcrImageData(
    val imageId: String?,
    val url: String?,
    val taskId: String?,
)

data class OcrTaskData(
    val taskId: String?,
    val status: String?,        // pending / processing / completed / failed
)

data class OcrResultData(
    val taskId: String?,
    val status: String?,
    val ocrText: String?,
    val confidence: Double?,
    val medicineName: String?,
    val medicineSpec: String?,
    val medicineUsage: String?,
    val medicineDosage: String?,
    val medicineContraindications: String?,
    val suggestion: String?,
    val createdAt: String?,
)

data class OcrProgressData(
    val taskId: String?,
    val status: String?,
    val progress: Int?,         // 0-100
    val stageMessage: String?,
)

data class SuggestionData(
    val taskId: String?,
    val suggestion: String?,
    val disclaimer: String?,
)

data class OcrRecordData(
    val taskId: String?,
    val imageId: String?,
    val status: String?,
    val ocrText: String?,
    val medicineName: String?,
    val suggestion: String?,
    val createdAt: String?,
)
