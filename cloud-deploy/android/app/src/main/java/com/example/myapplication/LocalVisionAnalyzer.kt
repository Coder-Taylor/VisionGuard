package com.example.myapplication

import android.content.Context

class LocalVisionAnalyzer {
    suspend fun analyze(context: Context, jpegBytes: ByteArray): LocalVisionState {
        return YoloInferenceManager.analyze(context, jpegBytes)
    }
}
