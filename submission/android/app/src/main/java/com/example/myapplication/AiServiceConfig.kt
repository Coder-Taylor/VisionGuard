package com.example.myapplication

/**
 * OpenAI 兼容的 AI 服务配置
 * 同时支持文本对话 (Chat) 和语音 (TTS/STT)
 */
data class AiServiceConfig(
    val baseUrl: String = "https://api.openai.com/v1/",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val ttsModel: String = "tts-1",
    val ttsVoice: String = "alloy",
    val sttModel: String = "whisper-1",
) {
    companion object {
        val DEFAULT = AiServiceConfig()

        // 预设配置：OpenAI 官方
        val OPENAI = AiServiceConfig(
            baseUrl = "https://api.openai.com/v1/",
            chatModel = "gpt-4o-mini",
            ttsModel = "tts-1",
            ttsVoice = "alloy",
            sttModel = "whisper-1",
        )

        // 预设配置：Azure OpenAI
        val AZURE = AiServiceConfig(
            baseUrl = "https://your-resource.openai.azure.com/openai/deployments/",
            chatModel = "gpt-4",
            ttsModel = "tts",
            ttsVoice = "zh-CN-XiaoxiaoNeural",
            sttModel = "whisper",
        )

        // 预设配置：阿里云 DashScope
        val DASHSCOPE = AiServiceConfig(
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
            chatModel = "qwen-turbo",
            ttsModel = "cosyvoice-v1",
            ttsVoice = "longxiaochun",
            sttModel = "paraformer-realtime-v1",
        )

        // 预设配置：DeepSeek
        val DEEPSEEK = AiServiceConfig(
            baseUrl = "https://api.deepseek.com/v1/",
            chatModel = "deepseek-chat",
            ttsModel = "",
            ttsVoice = "",
            sttModel = "",
        )
    }

    /**
     * 是否配置了有效的 API Key
     */
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    /**
     * 获取用于显示的简化状态
     */
    fun getDisplayStatus(): String = when {
        !isConfigured() -> "未配置"
        baseUrl.contains("openai.com") -> "OpenAI"
        baseUrl.contains("azure") -> "Azure"
        baseUrl.contains("dashscope") -> "阿里云"
        baseUrl.contains("deepseek") -> "DeepSeek"
        else -> "自定义"
    }
}
