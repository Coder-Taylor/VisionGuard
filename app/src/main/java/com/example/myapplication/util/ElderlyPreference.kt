package com.example.myapplication.util

import android.content.Context
import com.example.myapplication.ElderlyProfile

object ElderlyPreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_NAME = "elderly_name"
    private const val KEY_AGE = "elderly_age"
    private const val KEY_GENDER = "elderly_gender"
    private const val KEY_PHONE = "elderly_phone"
    private const val KEY_NOTES = "elderly_notes"

    fun save(context: Context, profile: ElderlyProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, profile.name)
            .putInt(KEY_AGE, profile.age)
            .putString(KEY_GENDER, profile.gender)
            .putString(KEY_PHONE, profile.phone)
            .putString(KEY_NOTES, profile.medicalNotes)
            .apply()
    }

    fun load(context: Context): ElderlyProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ElderlyProfile(
            name = prefs.getString(KEY_NAME, "") ?: "",
            age = prefs.getInt(KEY_AGE, 0),
            gender = prefs.getString(KEY_GENDER, "") ?: "",
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            medicalNotes = prefs.getString(KEY_NOTES, "") ?: "",
        )
    }
}
