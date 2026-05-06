package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val MEDICINE_BOX_LABEL = "Medicine_Box"
internal const val TEXT_REGION_LABEL = "Text_Region"

private const val MODEL_ASSET_NAME = "yolo11n.onnx"
private const val MODEL_SIZE = 640
private const val RGB_CHANNEL_COUNT = 3
private const val BOX_PARAMETER_COUNT = 4
private const val COLOR_MASK = 0xFF
private const val OBJECTNESS_VALUE_COUNT = 1
private const val MIN_CONFIDENCE = 0.35f
private const val NORMALIZATION_DIVISOR = 255f
private val DEFAULT_CLASS_LABELS = listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL)
private val REQUIRED_CLASS_LABELS = DEFAULT_CLASS_LABELS.toSet()
private val MODEL_NAMES_METADATA_KEYS = listOf("names", "classes", "class_names")

internal data class YoloDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class CropBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal object YoloInferenceManager {
    private val sessionMutex = Mutex()

    @Volatile
    private var sessionHolder: SessionHolder? = null

    suspend fun analyze(context: Context, jpegBytes: ByteArray): LocalVisionState {
        return withContext(Dispatchers.Default) {
            if (!hasValidJpegMarkers(jpegBytes)) {
                return@withContext inputFrameError("输入图像无法解码")
            }

            runCatching {
                val sourceBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    ?: return@withContext inputFrameError("输入图像无法解码")

                val holder = session(context.applicationContext)
                val classLabelsIssue = modelClassLabelsIssue(holder.classLabels)
                if (classLabelsIssue != null) {
                    return@withContext modelPipelineError(classLabelsIssue)
                }
                val supportsBusinessLabels = supportsRequiredClassLabels(holder.classLabels)

                val yoloDeferred = async {
                    runYoloDetection(holder, sourceBitmap)
                }
                val ocrDeferred = async {
                    OcrEngine.recognize(sourceBitmap)
                }

                val yoloResult = yoloDeferred.await()
                val ocrResult = ocrDeferred.await()

                if (!supportsBusinessLabels) {
                    return@withContext LocalVisionState(
                        status = LocalVisionStatus.FRAME_ANALYZED,
                        summary = summarizeDetections(yoloResult.detections),
                        result = VisionAnalysisResult(
                            hasMedicineBox = false,
                            medicineBoxLocation = null,
                            medicineBoxAreaRatio = null,
                            ocrText = ocrResult?.text,
                            hasOcrContent = ocrResult?.text?.isNotBlank() == true,
                        ),
                    )
                }

                val result = VisionAnalysisResult(
                    hasMedicineBox = yoloResult.medicineBox != null,
                    medicineBoxLocation = yoloResult.medicineBox?.let {
                        describeRelativeLocation(it, sourceBitmap.width, sourceBitmap.height)
                    },
                    medicineBoxAreaRatio = yoloResult.medicineBox?.let {
                        calculateAreaRatio(it, sourceBitmap.width, sourceBitmap.height)
                    },
                    ocrText = ocrResult?.text,
                    hasOcrContent = ocrResult?.text?.isNotBlank() == true,
                )

                LocalVisionState(
                    status = LocalVisionStatus.FRAME_ANALYZED,
                    summary = buildVisionSummary(result),
                    result = result,
                )
            }.getOrElse { error ->
                modelPipelineError(error.message ?: "本地视觉推理失败")
            }
        }
    }

    private data class YoloResult(
        val detections: List<YoloDetection>,
        val medicineBox: YoloDetection?,
        val textRegion: YoloDetection?,
    )

    private suspend fun runYoloDetection(
        holder: SessionHolder,
        sourceBitmap: Bitmap,
    ): YoloResult {
        val preparedFrame = prepareInput(sourceBitmap)
        val detections = runInference(holder, preparedFrame.tensorData)
        val medicineBox = selectHighestConfidenceDetection(detections, MEDICINE_BOX_LABEL)
        val textRegion = selectHighestConfidenceDetection(detections, TEXT_REGION_LABEL)
        return YoloResult(
            detections = detections,
            medicineBox = medicineBox,
            textRegion = textRegion,
        )
    }

    private fun calculateAreaRatio(
        detection: YoloDetection,
        frameWidth: Int,
        frameHeight: Int,
    ): Float {
        if (frameWidth <= 0 || frameHeight <= 0) return 0f
        val boxArea = (detection.right - detection.left) * (detection.bottom - detection.top)
        val frameArea = frameWidth.toFloat() * frameHeight.toFloat()
        return boxArea / frameArea
    }

    private suspend fun session(context: Context): SessionHolder {
        sessionHolder?.let { return it }
        return sessionMutex.withLock {
            sessionHolder ?: createSession(context).also { sessionHolder = it }
        }
    }

    private fun createSession(context: Context): SessionHolder {
        val environment = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open(MODEL_ASSET_NAME).use { it.readBytes() }
        val session = environment.createSession(modelBytes, OrtSession.SessionOptions())
        val inputName = requireNotNull(session.inputNames.firstOrNull()) {
            "ONNX 模型缺少输入节点"
        }
        return SessionHolder(
            environment = environment,
            session = session,
            inputName = inputName,
            classLabels = resolveClassLabels(
                metadataLabels = loadMetadataClassLabels(session),
            ),
        )
    }

    private fun loadMetadataClassLabels(session: OrtSession): List<String> {
        val metadata = runCatching { session.metadata.customMetadata }.getOrDefault(emptyMap())
        return MODEL_NAMES_METADATA_KEYS
            .asSequence()
            .mapNotNull(metadata::get)
            .map(::parseModelClassLabels)
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun runInference(holder: SessionHolder, tensorData: FloatArray): List<YoloDetection> {
        return parseDetections(
            rawOutput = runInferenceOutput(
                environment = holder.environment,
                session = holder.session,
                inputName = holder.inputName,
                tensorData = tensorData,
            ),
            classLabels = holder.classLabels,
        )
    }

    private fun runInferenceOutput(
        environment: OrtEnvironment,
        session: OrtSession,
        inputName: String,
        tensorData: FloatArray,
    ): Any? {
        val shape = longArrayOf(1, RGB_CHANNEL_COUNT.toLong(), MODEL_SIZE.toLong(), MODEL_SIZE.toLong())
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensorData), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { outputs ->
                (outputs.firstOrNull() as? OnnxTensor)?.value
            }
        }
    }

    private fun prepareInput(sourceBitmap: Bitmap): PreparedFrame {
        val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, MODEL_SIZE, MODEL_SIZE, true)
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        scaledBitmap.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        val tensorData = FloatArray(RGB_CHANNEL_COUNT * MODEL_SIZE * MODEL_SIZE)
        val channelSize = MODEL_SIZE * MODEL_SIZE

        for (index in pixels.indices) {
            val pixel = pixels[index]
            tensorData[index] = ((pixel shr 16) and COLOR_MASK) / NORMALIZATION_DIVISOR
            tensorData[channelSize + index] = ((pixel shr 8) and COLOR_MASK) / NORMALIZATION_DIVISOR
            tensorData[channelSize * 2 + index] = (pixel and COLOR_MASK) / NORMALIZATION_DIVISOR
        }

        return PreparedFrame(tensorData = tensorData)
    }

    private data class SessionHolder(
        val environment: OrtEnvironment,
        val session: OrtSession,
        val inputName: String,
        val classLabels: List<String>,
    )

    private data class PreparedFrame(
        val tensorData: FloatArray,
    )

    private fun inputFrameError(summary: String): LocalVisionState {
        return LocalVisionState(
            status = LocalVisionStatus.ERROR,
            summary = summary,
            result = null,
            issue = LocalVisionIssue.INPUT_FRAME,
        )
    }

    private fun modelPipelineError(summary: String): LocalVisionState {
        return LocalVisionState(
            status = LocalVisionStatus.ERROR,
            summary = summary,
            result = null,
            issue = LocalVisionIssue.MODEL_PIPELINE,
        )
    }

    private fun hasValidJpegMarkers(jpegBytes: ByteArray): Boolean {
        if (jpegBytes.size < 4) {
            return false
        }
        val startsWithSoi =
            jpegBytes[0] == 0xFF.toByte() && jpegBytes[1] == 0xD8.toByte()
        val endsWithEoi =
            jpegBytes[jpegBytes.lastIndex - 1] == 0xFF.toByte() &&
                jpegBytes[jpegBytes.lastIndex] == 0xD9.toByte()
        return startsWithSoi && endsWithEoi
    }
}

internal fun parseModelClassLabels(serializedNames: String?): List<String> {
    if (serializedNames.isNullOrBlank()) {
        return emptyList()
    }

    val indexedLabels = Regex("""[\"']?(\d+)[\"']?\s*:\s*[\"']([^\"']+)[\"']""")
        .findAll(serializedNames)
        .map { matchResult ->
            matchResult.groupValues[1].toInt() to matchResult.groupValues[2]
        }
        .toList()
    if (indexedLabels.isNotEmpty()) {
        return indexedLabels
            .sortedBy { it.first }
            .map { it.second }
    }

    return Regex("""[\"']([^\"']+)[\"']""")
        .findAll(serializedNames)
        .map { it.groupValues[1] }
        .toList()
}

internal fun modelClassLabelsIssue(classLabels: List<String>): String? {
    return if (classLabels.isEmpty()) {
        "模型缺少类别标签元数据"
    } else {
        null
    }
}

internal fun supportsRequiredClassLabels(classLabels: List<String>): Boolean {
    return REQUIRED_CLASS_LABELS.all(classLabels::contains)
}

internal fun resolveClassLabels(metadataLabels: List<String>): List<String> {
    if (metadataLabels.isNotEmpty()) {
        return metadataLabels
    }
    return emptyList()
}

internal fun parseDetections(rawOutput: Any?, classLabels: List<String>): List<YoloDetection> {
    if (classLabels.isEmpty()) {
        return emptyList()
    }

    val matrix = extractDetectionMatrix(rawOutput) ?: return emptyList()
    if (matrix.isEmpty()) {
        return emptyList()
    }

    val expectedWithoutObjectness = BOX_PARAMETER_COUNT + classLabels.size
    val expectedWithObjectness = expectedWithoutObjectness + OBJECTNESS_VALUE_COUNT
    val firstRowSize = matrix.firstOrNull()?.size ?: return emptyList()

    return when {
        matrix.size == expectedWithoutObjectness || matrix.size == expectedWithObjectness -> {
            parseChannelFirstOutput(matrix, classLabels)
        }

        firstRowSize == expectedWithoutObjectness || firstRowSize == expectedWithObjectness -> {
            parseRowFirstOutput(matrix, classLabels)
        }

        matrix.size < firstRowSize -> parseChannelFirstOutput(matrix, classLabels)
        else -> parseRowFirstOutput(matrix, classLabels)
    }
}

private fun extractDetectionMatrix(rawOutput: Any?): List<FloatArray>? {
    return when (rawOutput) {
        null -> null
        is FloatArray -> listOf(rawOutput)
        is Array<*> -> when {
            rawOutput.isEmpty() -> emptyList()
            rawOutput.all { it is FloatArray } -> rawOutput.map { it as FloatArray }
            else -> extractDetectionMatrix(rawOutput.firstOrNull())
        }

        else -> null
    }
}

private fun parseChannelFirstOutput(
    matrix: List<FloatArray>,
    classLabels: List<String>,
): List<YoloDetection> {
    val expectedWithoutObjectness = BOX_PARAMETER_COUNT + classLabels.size
    if (matrix.size < expectedWithoutObjectness) {
        return emptyList()
    }

    val hasObjectness = matrix.size == expectedWithoutObjectness + OBJECTNESS_VALUE_COUNT
    val usableRows = BOX_PARAMETER_COUNT + classLabels.size + if (hasObjectness) OBJECTNESS_VALUE_COUNT else 0
    val candidateCount = matrix.take(usableRows).minOfOrNull(FloatArray::size) ?: return emptyList()

    return buildList {
        for (index in 0 until candidateCount) {
            val objectness = if (hasObjectness) matrix[BOX_PARAMETER_COUNT][index] else 1f
            val classScores = FloatArray(classLabels.size) { classIndex ->
                matrix[BOX_PARAMETER_COUNT + if (hasObjectness) OBJECTNESS_VALUE_COUNT else 0 + classIndex][index]
            }
            detectionFromValues(
                centerX = matrix[0][index],
                centerY = matrix[1][index],
                width = matrix[2][index],
                height = matrix[3][index],
                objectness = objectness,
                classScores = classScores,
                classLabels = classLabels,
            )?.let(::add)
        }
    }
}

private fun parseRowFirstOutput(
    rows: List<FloatArray>,
    classLabels: List<String>,
): List<YoloDetection> {
    val expectedWithoutObjectness = BOX_PARAMETER_COUNT + classLabels.size
    return rows.mapNotNull { row ->
        if (row.size < expectedWithoutObjectness) {
            return@mapNotNull null
        }

        val hasObjectness = row.size == expectedWithoutObjectness + OBJECTNESS_VALUE_COUNT
        val classScoresStart = BOX_PARAMETER_COUNT + if (hasObjectness) OBJECTNESS_VALUE_COUNT else 0
        detectionFromValues(
            centerX = row[0],
            centerY = row[1],
            width = row[2],
            height = row[3],
            objectness = if (hasObjectness) row[BOX_PARAMETER_COUNT] else 1f,
            classScores = row.copyOfRange(classScoresStart, classScoresStart + classLabels.size),
            classLabels = classLabels,
        )
    }
}

private fun detectionFromValues(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    objectness: Float,
    classScores: FloatArray,
    classLabels: List<String>,
): YoloDetection? {
    if (!centerX.isFinite() || !centerY.isFinite() || !width.isFinite() || !height.isFinite()) {
        return null
    }
    if (width <= 0f || height <= 0f) {
        return null
    }

    val classIndex = classScores.indices.maxByOrNull(classScores::get) ?: return null
    val confidence = (classScores[classIndex] * objectness.coerceAtLeast(0f))
    if (!confidence.isFinite() || confidence < MIN_CONFIDENCE) {
        return null
    }

    val label = classLabels.getOrNull(classIndex) ?: return null
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    return YoloDetection(
        label = label,
        confidence = confidence,
        left = centerX - halfWidth,
        top = centerY - halfHeight,
        right = centerX + halfWidth,
        bottom = centerY + halfHeight,
    )
}

internal fun selectHighestConfidenceDetection(
    detections: List<YoloDetection>,
    label: String,
): YoloDetection? {
    return detections
        .asSequence()
        .filter { it.label == label }
        .maxByOrNull(YoloDetection::confidence)
}

internal fun describeRelativeLocation(
    detection: YoloDetection,
    frameWidth: Int,
    frameHeight: Int,
): String {
    if (frameWidth <= 0 || frameHeight <= 0) {
        return "画面中央，距离未知"
    }

    val centerXRatio = ((detection.left + detection.right) / 2f) / frameWidth
    val centerYRatio = ((detection.top + detection.bottom) / 2f) / frameHeight
    val areaRatio = ((detection.right - detection.left) * (detection.bottom - detection.top)) /
        (frameWidth.toFloat() * frameHeight.toFloat())

    val horizontal = when {
        centerXRatio < 0.33f -> "左"
        centerXRatio > 0.67f -> "右"
        else -> "中"
    }
    val vertical = when {
        centerYRatio < 0.33f -> "上"
        centerYRatio > 0.67f -> "下"
        else -> "中"
    }
    val position = when {
        horizontal == "中" && vertical == "中" -> "画面中央"
        horizontal == "中" -> "画面${vertical}方"
        vertical == "中" -> "画面${horizontal}侧"
        else -> "画面$horizontal$vertical"
    }
    val distance = when {
        areaRatio >= 0.18f -> "距离较近"
        areaRatio >= 0.05f -> "距离中等"
        else -> "距离较远"
    }
    return "$position，$distance"
}

internal fun cropBoundsForDetection(
    detection: YoloDetection,
    sourceWidth: Int,
    sourceHeight: Int,
): CropBounds? {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return null
    }

    val left = floor(detection.left).toInt().coerceIn(0, sourceWidth)
    val top = floor(detection.top).toInt().coerceIn(0, sourceHeight)
    val right = ceil(detection.right).toInt().coerceIn(0, sourceWidth)
    val bottom = ceil(detection.bottom).toInt().coerceIn(0, sourceHeight)
    val width = right - left
    val height = bottom - top

    if (width <= 0 || height <= 0) {
        return null
    }

    return CropBounds(
        left = left,
        top = top,
        width = width,
        height = height,
    )
}

internal fun cropTextRegion(
    sourceBitmap: Bitmap,
    detection: YoloDetection,
): Bitmap? {
    val bounds = cropBoundsForDetection(detection, sourceBitmap.width, sourceBitmap.height) ?: return null
    return runCatching {
        Bitmap.createBitmap(sourceBitmap, bounds.left, bounds.top, bounds.width, bounds.height)
    }.getOrNull()
}

internal fun summarizeDetections(detections: List<YoloDetection>): String {
    if (detections.isEmpty()) {
        return "未检测到已知目标"
    }
    val topLabels = detections
        .sortedByDescending(YoloDetection::confidence)
        .map(YoloDetection::label)
        .distinct()
        .take(3)
    return "检测到 ${topLabels.joinToString("、")}"
}

internal fun buildVisionSummary(result: VisionAnalysisResult): String {
    val yoloSummary = if (result.hasMedicineBox) {
        "药盒位于${result.medicineBoxLocation}"
    } else {
        "未检测到药盒"
    }

    val ocrSummary = when {
        !result.hasOcrContent -> null // 静默模式：无文字时不播报
        result.ocrText!!.length > 50 -> "文字内容：${result.ocrText.take(50)}..."
        else -> "文字内容：${result.ocrText}"
    }

    return if (ocrSummary != null) {
        "$yoloSummary，$ocrSummary"
    } else {
        yoloSummary
    }
}
