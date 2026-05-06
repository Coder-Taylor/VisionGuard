package com.example.myapplication

import com.example.myapplication.api.FcmTokenRequest
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.auth.AuthTokenHolder
import com.example.myapplication.util.AuthPreference
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VisionHubFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AuthPreference.saveFcmToken(applicationContext, token)
        val userId = AuthTokenHolder.userId
        if (userId.isNotBlank() && AuthTokenHolder.token != null) {
            scope.launch {
                try {
                    RetrofitClient.authApi.uploadFcmToken(FcmTokenRequest(userId, token))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "告警通知"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        NotificationHelper.showAlertNotification(applicationContext, title, body)
    }
}
