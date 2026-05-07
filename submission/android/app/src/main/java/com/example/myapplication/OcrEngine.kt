package com.example.myapplication

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * OCR 识别结果
 */
data class OcrResult(
    val text: String,
    val confidence: Float?,
)

/**
 * 视觉分析结果（包含 YOLO + OCR）
 */
data class VisionAnalysisResult(
    // YOLO 检测结果
    val hasMedicineBox: Boolean,
    val medicineBoxLocation: String?, // 相对位置描述
    val medicineBoxAreaRatio: Float?, // 面积比例，用于判断远近

    // OCR 结果
    val ocrText: String?,
    val hasOcrContent: Boolean,
)

/**
 * OCR 文字识别引擎
 */
internal object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * 对 Bitmap 进行 OCR 识别
     * @return 识别到的文本，如果识别失败或无文字则返回 null
     */
    suspend fun recognize(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)

        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isNotEmpty()) {
                        val avgConfidence = visionText.textBlocks
                            .flatMap { it.lines }
                            .mapNotNull { it.confidence }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.toFloat()
                        continuation.resume(OcrResult(text = text, confidence = avgConfidence))
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }
}
