package com.example.myapplication.util

import android.content.Context
import com.example.myapplication.AiServiceConfig

/**
 * AI 服务配置 SharedPreferences 存储
 */
object AiServicePreference {
    private const val PREFS_NAME = "visionhub_prefs"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_CHAT_MODEL = "ai_chat_model"
    private const val KEY_TTS_MODEL = "ai_tts_model"
    private const val KEY_TTS_VOICE = "ai_tts_voice"
    private const val KEY_STT_MODEL = "ai_stt_model"

    fun save(context: Context, config: AiServiceConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_CHAT_MODEL, config.chatModel)
            .putString(KEY_TTS_MODEL, config.ttsModel)
            .putString(KEY_TTS_VOICE, config.ttsVoice)
            .putString(KEY_STT_MODEL, config.sttModel)
            .apply()
    }

    fun load(context: Context): AiServiceConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AiServiceConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, AiServiceConfig.DEFAULT.baseUrl) ?: AiServiceConfig.DEFAULT.baseUrl,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            chatModel = prefs.getString(KEY_CHAT_MODEL, AiServiceConfig.DEFAULT.chatModel) ?: AiServiceConfig.DEFAULT.chatModel,
            ttsModel = prefs.getString(KEY_TTS_MODEL, AiServiceConfig.DEFAULT.ttsModel) ?: AiServiceConfig.DEFAULT.ttsModel,
            ttsVoice = prefs.getString(KEY_TTS_VOICE, AiServiceConfig.DEFAULT.ttsVoice) ?: AiServiceConfig.DEFAULT.ttsVoice,
            sttModel = prefs.getString(KEY_STT_MODEL, AiServiceConfig.DEFAULT.sttModel) ?: AiServiceConfig.DEFAULT.sttModel,
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_API_KEY)
            .remove(KEY_CHAT_MODEL)
            .remove(KEY_TTS_MODEL)
            .remove(KEY_TTS_VOICE)
            .remove(KEY_STT_MODEL)
            .apply()
    }
}
