package com.example.myapplication

import android.os.SystemClock
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceLatencyMonitor(
    private val scope: CoroutineScope,
    private val dataHub: VisionDataHub = VisionDataHub,
) {
    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob != null) {
            return
        }

        monitorJob = scope.launch {
            dataHub.remoteDeviceIp.collectLatest { deviceIp ->
                if (deviceIp == null) {
                    dataHub.updateNetworkLatencyMs(null)
                    return@collectLatest
                }
                while (true) {
                    dataHub.updateNetworkLatencyMs(measureLatency(deviceIp))
                    delay(CHECK_INTERVAL_MILLIS)
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        dataHub.updateNetworkLatencyMs(null)
    }

    private fun measureLatency(deviceIp: String): Int? {
        val address = runCatching { InetAddress.getByName(deviceIp) }.getOrNull() ?: return null
        val startedAt = SystemClock.elapsedRealtime()
        val reachable = runCatching { address.isReachable(REACHABLE_TIMEOUT_MILLIS) }.getOrDefault(false)
        return if (reachable) {
            (SystemClock.elapsedRealtime() - startedAt).toInt()
        } else {
            null
        }
    }

    private companion object {
        const val REACHABLE_TIMEOUT_MILLIS = 1_000
        const val CHECK_INTERVAL_MILLIS = 3_000L
    }
}
