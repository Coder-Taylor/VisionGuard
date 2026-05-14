package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.example.myapplication.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VisionHubService : Service() {
    // CoroutineScope = Java ExecutorService 替代品: 管理协程(轻量线程)生命周期, SupervisorJob 使子协程异常不传播
    // Dispatchers.IO = Java ThreadPoolExecutor(CachedThreadPool), 专门处理 IO 操作
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var lastImageFrameAtMillis = 0L

    @Volatile
    private var fallDetectionEngine = FallDetectionEngine()
    private val localVisionAnalyzer = LocalVisionAnalyzer()
    private val latencyMonitor = DeviceLatencyMonitor(scope = serviceScope)

    // 已推送过的告警 ID，避免重复通知
    private val knownAlertIds = mutableSetOf<String>()
    // 上次轮询的 pending 告警数
    @Volatile
    private var lastPendingCount = 0
    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        NotificationHelper.createAlertChannel(this)
        VisionDataHub.updateConnectionState(ConnectionState.STARTING)
        VisionDataHub.updateFallAlertState(FallAlertState.IDLE)
        VisionDataHub.updateLocalVisionState(LocalVisionState.IDLE)

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            if (!isHeld) {
                acquire()
            }
        }
        observeFallConfig()
        observeRecognitionState()
        startAlertPolling()
        latencyMonitor.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildServiceNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                FOREGROUND_SERVICE_TYPES,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        latencyMonitor.stop()
        serviceScope.cancel()
        wakeLock?.let { currentWakeLock ->
            if (currentWakeLock.isHeld) {
                currentWakeLock.release()
            }
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    // launch = Java new Thread(runnable).start(): 启动协程(轻量线程)执行挂起函数
    // collect = Java while(true) { queue.take() }: 持续消费 Flow 中的事件, 永不返回
    private fun observeFallConfig() {
        serviceScope.launch {
            VisionDataHub.fallConfig.collect { newConfig ->
                fallDetectionEngine = FallDetectionEngine(config = newConfig)
            }
        }
    }

    private fun observeSensorPackets() {
        serviceScope.launch {
            VisionDataHub.sensorPackets.collect { packet ->
                val outcome = fallDetectionEngine.process(packet)
                val fallAlertState = when (outcome.state) {
                    FallDetectionState.IDLE -> FallAlertState.IDLE
                    FallDetectionState.DETECTING -> FallAlertState.DETECTING
                    FallDetectionState.FALL_CONFIRMED -> FallAlertState.FALL_CONFIRMED
                    FallDetectionState.COOLDOWN -> FallAlertState.EMERGENCY_CALLING
                }
                VisionDataHub.updateFallAlertState(fallAlertState)
                if (outcome.shouldTriggerEmergency) {
                    val handler = EmergencyCallHandler(config = VisionDataHub.emergencyContact.value)
                    val didStartCall = handler.triggerEmergencyCall(this@VisionHubService)
                    val nextState = if (didStartCall) {
                        FallAlertState.EMERGENCY_CALLING
                    } else {
                        FallAlertState.FALL_CONFIRMED
                    }
                    VisionDataHub.updateFallAlertState(nextState)
                }
            }
        }
    }

    private fun observeImageFrames() {
        serviceScope.launch {
            VisionDataHub.imageFrames.collect { frame ->
                if (!VisionDataHub.obstacleEnabled.value) return@collect
                lastImageFrameAtMillis = SystemClock.elapsedRealtime()
                VisionDataHub.updateLocalVisionState(LocalVisionState.PROCESSING)
                val result = localVisionAnalyzer.analyze(this@VisionHubService, frame)
                VisionDataHub.updateLocalVisionState(result)
            }
        }
    }

    // delay = Java Thread.sleep(), 但不阻塞线程 (挂起函数, 让出 CPU)
    // collectLatest = 新事件到达时自动取消上次未完成处理, 只处理最新值
    private fun observeRecognitionState() {
        serviceScope.launch {
            VisionDataHub.connectionState.collectLatest { state ->
                if (state != ConnectionState.CONNECTED) {
                    lastImageFrameAtMillis = 0L
                    VisionDataHub.updateLocalVisionState(LocalVisionState.waitingForNewFrame())
                }
            }
        }

        serviceScope.launch {
            VisionDataHub.obstacleEnabled.collectLatest { enabled ->
                if (!enabled) {
                    lastImageFrameAtMillis = 0L
                    VisionDataHub.updateLocalVisionState(LocalVisionState.waitingForNewFrame())
                    return@collectLatest
                }

                while (isActive && VisionDataHub.obstacleEnabled.value) {
                    delay(NO_NEW_FRAME_TIMEOUT_MILLIS)
                    if (VisionDataHub.connectionState.value != ConnectionState.CONNECTED) {
                        lastImageFrameAtMillis = 0L
                        VisionDataHub.updateLocalVisionState(LocalVisionState.waitingForNewFrame())
                        continue
                    }
                    val lastFrameAtMillis = lastImageFrameAtMillis
                    if (lastFrameAtMillis == 0L) {
                        VisionDataHub.updateLocalVisionState(LocalVisionState.waitingForNewFrame())
                        continue
                    }
                    val frameAgeMillis = SystemClock.elapsedRealtime() - lastFrameAtMillis
                    if (frameAgeMillis < NO_NEW_FRAME_TIMEOUT_MILLIS) {
                        continue
                    }
                    val currentState = VisionDataHub.localVisionState.value
                    if (!shouldSwitchToWaitingForNewFrame(currentState)) {
                        continue
                    }
                    VisionDataHub.updateLocalVisionState(currentState.waitingForNextFrame())
                }
            }
        }
    }

    // ======================== 后台轮询云端告警 ========================
    // 每 30s 拉取 pending 告警，新告警弹出系统通知（类似微信消息推送）
    private fun startAlertPolling() {
        serviceScope.launch {
            // 首次等待 10s，让 APP 完成登录和首页数据加载
            delay(10_000L)
            while (isActive) {
                try {
                    val resp = RetrofitClient.alertApi.listAlerts(status = "pending", pageSize = 10)
                    if (resp.isSuccessful) {
                        val alerts = resp.body()?.data?.list ?: emptyList()
                        val currentCount = alerts.size

                        // 只处理首次加载后新增的告警
                        if (lastPendingCount > 0 || knownAlertIds.isNotEmpty()) {
                            for (alert in alerts) {
                                val id = alert.alertId ?: continue
                                if (id !in knownAlertIds) {
                                    knownAlertIds.add(id)
                                    val title = alert.alertType?.let { mapAlertTypeName(it) } ?: "新告警"
                                    val body = alert.description ?: "设备 ${alert.deviceId ?: ""} 触发告警"
                                    NotificationHelper.showAlertNotification(
                                        this@VisionHubService, title, body
                                    )
                                }
                            }
                        } else {
                            // 首次加载：记录已有告警，不弹通知
                            alerts.forEach { it.alertId?.let { id -> knownAlertIds.add(id) } }
                        }
                        lastPendingCount = currentCount
                    }
                } catch (_: Exception) {
                    // 网络异常静默忽略，下轮重试
                }
                delay(30_000L)
            }
        }
    }

    private fun mapAlertTypeName(type: String): String = when (type) {
        "fall" -> "摔倒告警"
        "obstacle" -> "避障危险"
        "sos" -> "紧急呼叫"
        "heart_rate_abnormal" -> "心率异常"
        "low_battery" -> "低电量"
        "device_offline" -> "设备离线"
        "geofence" -> "电子围栏"
        else -> type
    }

    private fun shouldSwitchToWaitingForNewFrame(state: LocalVisionState): Boolean {
        return when (state.issue) {
            LocalVisionIssue.MODEL_PIPELINE -> false
            else -> state.status != LocalVisionStatus.PROCESSING
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "com.example.myapplication:VisionHubWakeLock"
        private const val FOREGROUND_SERVICE_TYPES =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        private const val NO_NEW_FRAME_TIMEOUT_MILLIS = 3_000L
    }
}
