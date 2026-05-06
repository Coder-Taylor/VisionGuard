package com.example.myapplication.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.myapplication.BuildConfig
import com.example.myapplication.auth.AuthTokenHolder
import com.example.myapplication.util.AuthPreference
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// object = Java 单例: 相当于 private 构造 + static final INSTANCE
object ApiConfig {
    // 真机测试: 电脑连手机热点时, 手机通过电脑 IP 访问后端
    // 模拟器: 改回 "http://10.0.2.2:3000/"
    // 注意: 所有 API 路径均已包含 api/v1/ 前缀，BASE_URL 不要重复加
    const val BASE_URL = "http://47.94.146.53:3000/"
}

private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = AuthTokenHolder.token
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

private class TokenAuthenticator : Authenticator {
    private val lock = Any()
    private var isRefreshing = false

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry auth endpoints
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
            return null
        }

        synchronized(lock) {
            val refreshToken = AuthTokenHolder.refreshToken
            if (refreshToken.isNullOrBlank()) return null

            // If another thread already refreshed the token, retry with current token
            val currentToken = AuthTokenHolder.token
            val previousAuth = response.request.header("Authorization")
            if (currentToken != null && previousAuth != "Bearer $currentToken") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // Prevent concurrent refresh calls
            if (isRefreshing) return null
            isRefreshing = true

            try {
                val refreshClient = OkHttpClient()
                val jsonBody = """{"refresh_token":"$refreshToken"}"""
                val refreshRequest = Request.Builder()
                    .url("${ApiConfig.BASE_URL}api/v1/auth/refresh")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val refreshResponse = refreshClient.newCall(refreshRequest).execute()

                if (refreshResponse.isSuccessful) {
                    val bodyStr = refreshResponse.body?.string() ?: run { isRefreshing = false; return null }
                    val map = try {
                        Gson().fromJson(bodyStr, Map::class.java)
                    } catch (_: Exception) {
                        isRefreshing = false; return null
                    }
                    val newAccessToken = map["access_token"] as? String ?: ""
                    val newRefreshToken = map["refresh_token"] as? String ?: ""

                    if (newAccessToken.isBlank()) { isRefreshing = false; return null }

                    AuthTokenHolder.token = newAccessToken
                    if (newRefreshToken.isNotBlank()) {
                        AuthTokenHolder.refreshToken = newRefreshToken
                    }

                    // Persist to SharedPreferences
                    try {
                        val ctx = AppContext.instance
                        AuthPreference.saveJwt(ctx, newAccessToken)
                        if (newRefreshToken.isNotBlank()) {
                            AuthPreference.saveRefreshToken(ctx, newRefreshToken)
                        }
                    } catch (_: Exception) {}

                    isRefreshing = false
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                }
            } catch (_: Exception) {}

            isRefreshing = false
        }

        // Refresh failed, clear tokens and force logout
        AuthTokenHolder.token = null
        AuthTokenHolder.refreshToken = null
        Handler(Looper.getMainLooper()).post {
            AuthTokenHolder.onForceLogout?.invoke()
        }
        return null
    }
}

// Application context holder for Authenticator to persist tokens
object AppContext {
    lateinit var instance: Context
}

// object = Java 单例 (private 构造 + static INSTANCE)
object RetrofitClient {

    // by lazy = Java 双重检查锁定(DCL)懒加载: 首次访问时初始化, 线程安全, 只执行一次
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        builder.addInterceptor(AuthInterceptor())
        builder.authenticator(TokenAuthenticator())
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }
        builder.build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 11 大业务模块 API（严格对齐业务流程与后端设计.md 81 路由）
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val elderApi: ElderApi by lazy { retrofit.create(ElderApi::class.java) }
    val deviceApi: DeviceApi by lazy { retrofit.create(DeviceApi::class.java) }
    val alertApi: AlertApi by lazy { retrofit.create(AlertApi::class.java) }
    val locationApi: LocationApi by lazy { retrofit.create(LocationApi::class.java) }
    val ocrApi: OcrApi by lazy { retrofit.create(OcrApi::class.java) }
    val notificationApi: NotificationApi by lazy { retrofit.create(NotificationApi::class.java) }
    val medicationApi: MedicationApi by lazy { retrofit.create(MedicationApi::class.java) }

    // 兼容旧名（队友代码可能引用）
    val userProfileApi: UserProfileApi by lazy { retrofit.create(UserProfileApi::class.java) }
    val elderlyApi: ElderApi by lazy { retrofit.create(ElderApi::class.java) }  // 旧名→新 ElderApi

    fun createAiServiceApi(baseUrl: String): AiServiceApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(AiServiceApi::class.java)
    }
}
