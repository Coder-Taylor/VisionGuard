package com.example.myapplication

/**
 * 用户档案信息
 */
data class UserProfile(
    val displayName: String = "",
    val userId: String = "",
    val avatarInitial: String = "",
)

/**
 * 用户档案 API 响应
 */
data class UserProfileResponse(
    val success: Boolean = false,
    val data: UserProfileData? = null,
    val message: String? = null,
)

data class UserProfileData(
    val displayName: String = "",
    val userId: String = "",
    val avatarInitial: String = "",
)

/**
 * 更新用户档案请求
 */
data class UpdateProfileRequest(
    val displayName: String,
    val avatarInitial: String,
    val emergencyPhone: String? = null,
)
