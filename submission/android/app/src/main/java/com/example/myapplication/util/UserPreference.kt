package com.example.myapplication.util

import android.content.Context
import com.example.myapplication.UserProfile

/**
 * 用户档案 SharedPreferences 存储
 */
object UserPreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_AVATAR_INITIAL = "avatar_initial"

    fun save(context: Context, profile: UserProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, profile.userId)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_AVATAR_INITIAL, profile.avatarInitial)
            .apply()
    }

    fun load(context: Context): UserProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        val avatarInitial = prefs.getString(KEY_AVATAR_INITIAL, "") ?: ""
        return UserProfile(
            userId = userId,
            displayName = displayName,
            avatarInitial = avatarInitial,
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_AVATAR_INITIAL)
            .apply()
    }

    /**
     * 生成默认用户 ID（如果没有的话）
     */
    fun generateUserId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        return "WS-${timestamp % 10000}-$random"
    }
}
