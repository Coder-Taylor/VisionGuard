package com.example.myapplication.util

import android.content.Context

internal object VolumePreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_VOLUME = "tts_volume"
    private const val DEFAULT_VOLUME = 0.66f

    fun save(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOLUME, value.coerceIn(0f, 1f))
            .apply()
    }

    fun load(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_VOLUME, DEFAULT_VOLUME)
}
