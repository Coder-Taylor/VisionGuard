package com.example.myapplication.util

import android.content.Context
import com.example.myapplication.FallDetectionConfig

internal object SensitivityPreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_FREE_FALL = "fall_free_fall_threshold"
    private const val KEY_IMPACT = "fall_impact_threshold"
    private const val KEY_WINDOW = "fall_impact_window_ms"
    private const val KEY_COOLDOWN = "fall_cooldown_ms"

    fun save(context: Context, config: FallDetectionConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FREE_FALL, config.freeFallThreshold.toFloat())
            .putFloat(KEY_IMPACT, config.impactThreshold.toFloat())
            .putLong(KEY_WINDOW, config.impactWindowMillis)
            .putLong(KEY_COOLDOWN, config.cooldownMillis)
            .apply()
    }

    fun load(context: Context): FallDetectionConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val default = FallDetectionConfig.DEFAULT
        return FallDetectionConfig(
            freeFallThreshold = prefs.getFloat(KEY_FREE_FALL, default.freeFallThreshold.toFloat()).toDouble(),
            impactThreshold = prefs.getFloat(KEY_IMPACT, default.impactThreshold.toFloat()).toDouble(),
            impactWindowMillis = prefs.getLong(KEY_WINDOW, default.impactWindowMillis),
            cooldownMillis = prefs.getLong(KEY_COOLDOWN, default.cooldownMillis),
        )
    }

    val LOW = FallDetectionConfig(
        freeFallThreshold = 3.0,
        impactThreshold = 22.0,
        impactWindowMillis = 800L,
        cooldownMillis = 15_000L,
    )

    val MEDIUM = FallDetectionConfig.DEFAULT

    val HIGH = FallDetectionConfig(
        freeFallThreshold = 2.0,
        impactThreshold = 14.0,
        impactWindowMillis = 1_200L,
        cooldownMillis = 8_000L,
    )
}
