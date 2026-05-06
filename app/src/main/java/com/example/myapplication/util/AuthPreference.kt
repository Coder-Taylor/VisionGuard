package com.example.myapplication.util

import android.content.Context

object AuthPreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PHONE = "phone"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    fun saveJwt(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    fun loadJwt(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JWT_TOKEN, null)

    fun saveRefreshToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun loadRefreshToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REFRESH_TOKEN, null)

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_JWT_TOKEN).remove(KEY_REFRESH_TOKEN)
            .remove(KEY_DISPLAY_NAME).remove(KEY_PHONE).apply()
    }

    fun clearJwt(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_JWT_TOKEN).remove(KEY_DISPLAY_NAME).remove(KEY_PHONE).apply()
    }

    fun saveUserInfo(context: Context, displayName: String, phone: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISPLAY_NAME, displayName).putString(KEY_PHONE, phone).apply()
    }

    fun loadDisplayName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_NAME, "") ?: ""

    fun loadPhone(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, "") ?: ""

    fun saveFcmToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun loadFcmToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
}
