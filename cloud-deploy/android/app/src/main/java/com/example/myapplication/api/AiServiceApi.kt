package com.example.myapplication.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * OpenAI 兼容的 AI 服务 API
 * 支持 Chat、TTS、STT
 */
interface AiServiceApi {

    /**
     * 聊天对话 - OpenAI 兼容格式
     */
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>

    /**
     * 语音合成 TTS - OpenAI 兼容格式
     */
    @POST("audio/speech")
    suspend fun textToSpeech(
        @Header("Authorization") authorization: String,
        @Body request: TtsRequest,
    ): Response<okhttp3.ResponseBody>

    /**
     * 语音识别 STT - OpenAI 兼容格式
     */
    @POST("audio/transcriptions")
    suspend fun speechToText(
        @Header("Authorization") authorization: String,
        @Body request: okhttp3.MultipartBody,
    ): Response<SttResponse>
}

/**
 * 聊天请求
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500,
)

data class Message(
    val role: String,
    val content: String,
)

/**
 * 聊天响应
 */
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<Choice>?,
    val error: ErrorResponse?,
)

data class Choice(
    val message: Message?,
    val finish_reason: String?,
)

/**
 * TTS 请求
 */
data class TtsRequest(
    val model: String,
    val input: String,
    val voice: String,
)

/**
 * STT 响应
 */
data class SttResponse(
    val text: String?,
    val error: ErrorResponse?,
)

data class ErrorResponse(
    val message: String?,
    val type: String?,
    val code: String?,
)
