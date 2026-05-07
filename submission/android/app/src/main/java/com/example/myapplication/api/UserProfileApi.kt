package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.*

interface UserProfileApi {
    @GET("/api/v1/user/profile")
    suspend fun getUserProfile(): Response<ApiResponse<UserProfileData>>

    @PUT("/api/v1/user/profile")
    suspend fun updateUserProfile(@Body req: UpdateProfileReq): Response<ApiResponse<Any?>>
}

data class UpdateProfileReq(val displayName: String, val phone: String? = null)

data class UserProfileData(
    val userId: Int?,
    val username: String?,
    val displayName: String?,
    val email: String?,
    val phone: String?,
    val status: String?,
    val createdAt: String?,
)
