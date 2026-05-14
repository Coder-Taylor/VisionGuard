package com.example.myapplication.util

import android.content.Context
import com.example.myapplication.EmergencyContactConfig

internal object ContactPreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_EMERGENCY_NUMBER = "emergency_number"

    fun save(context: Context, number: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMERGENCY_NUMBER, number.trim())
            .apply()
    }

    fun load(context: Context): EmergencyContactConfig {
        val number = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMERGENCY_NUMBER, EmergencyContactConfig.DEFAULT.emergencyNumber)
            ?: EmergencyContactConfig.DEFAULT.emergencyNumber
        return EmergencyContactConfig(emergencyNumber = number)
    }
}
