package com.example.myapplication.auth

object AuthTokenHolder {
    @Volatile var token: String? = null
    @Volatile var refreshToken: String? = null
    @Volatile var userId: String = ""
    @Volatile var displayName: String = ""
    @Volatile var phone: String = ""
    var onForceLogout: (() -> Unit)? = null
}
