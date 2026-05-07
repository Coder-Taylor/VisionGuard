package com.example.myapplication

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

class EmergencyCallHandler(
    private val config: EmergencyContactConfig = EmergencyContactConfig.DEFAULT,
) {
    fun triggerEmergencyCall(context: Context): Boolean {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${config.emergencyNumber}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(callIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
