package com.example.myapplication.util

import android.util.Log
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHelper {
    private const val TAG = "VisionGuard"

    fun userMessage(e: Exception, context: String = ""): String {
        Log.e(TAG, "$context: ${e.javaClass.simpleName} — ${e.message}", e)

        val deviceOffline = !NetworkMonitor.isOnline()

        return when (e) {
            is ConnectException ->
                if (deviceOffline) "当前处于离线状态，请检查手机网络"
                else "无法连接服务器，服务器可能暂时不可用，请稍后重试"

            is SocketTimeoutException ->
                if (deviceOffline) "当前处于离线状态，请检查手机网络"
                else "连接超时，请检查网络后重试"

            is UnknownHostException ->
                if (deviceOffline) "当前处于离线状态，请检查手机网络"
                else "服务器地址解析失败，请检查网络配置"

            is java.io.IOException ->
                if (deviceOffline) "当前处于离线状态，请检查手机网络"
                else "网络异常（${e.javaClass.simpleName}），请检查网络"

            else -> "请求失败（${e.javaClass.simpleName}），请稍后重试"
        }
    }
}
