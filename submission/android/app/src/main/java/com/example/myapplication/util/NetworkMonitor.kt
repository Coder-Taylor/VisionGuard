package com.example.myapplication.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

object NetworkMonitor {
    private const val TAG = "VisionGuard-Network"
    @Volatile private var appContext: Context? = null
    @Volatile private var lastKnownOnline = true

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        lastKnownOnline = checkNow(ctx)

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lastKnownOnline = true
                Log.d(TAG, "Network available")
            }
            override fun onLost(network: Network) {
                lastKnownOnline = false
                Log.d(TAG, "Network lost — device offline")
            }
        })
        Log.d(TAG, "NetworkMonitor initialized, online=$lastKnownOnline")
    }

    fun isOnline(): Boolean {
        val ctx = appContext
        if (ctx != null) {
            lastKnownOnline = checkNow(ctx)
        }
        return lastKnownOnline
    }

    private fun checkNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
