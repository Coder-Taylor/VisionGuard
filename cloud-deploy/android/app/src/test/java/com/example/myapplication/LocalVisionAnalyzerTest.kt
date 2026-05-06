package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloInferenceManagerTest {
    @Test
    fun `selectHighestConfidenceDetection prefers strongest medicine box`() {
        val detections = listOf(
            YoloDetection(
                label = MEDICINE_BOX_LABEL,
                confidence = 0.41f,
                left = 12f,
                top = 20f,
                right = 100f,
                bottom = 140f,
            ),
            YoloDetection(
                label = TEXT_REGION_LABEL,
                confidence = 0.95f,
                left = 60f,
                top = 40f,
                right = 180f,
                bottom = 120f,
            ),
            YoloDetection(
                label = MEDICINE_BOX_LABEL,
                confidence = 0.83f,
                left = 40f,
                top = 36f,
                right = 220f,
                bottom = 210f,
            ),
        )

        val detection = selectHighestConfidenceDetection(detections, MEDICINE_BOX_LABEL)

        assertNotNull(detection)
        assertEquals(0.83f, detection?.confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `describeRelativeLocation maps box to left top and near`() {
        val detection = YoloDetection(
            label = MEDICINE_BOX_LABEL,
            confidence = 0.9f,
            left = 0f,
            top = 0f,
            right = 140f,
            bottom = 140f,
        )

        assertEquals(
            "画面左上，距离较近",
            describeRelativeLocation(detection, frameWidth = 320, frameHeight = 320),
        )
    }

    @Test
    fun `cropBoundsForDetection clamps coordinates into source frame`() {
        val detection = YoloDetection(
            label = TEXT_REGION_LABEL,
            confidence = 0.77f,
            left = -18f,
            top = 6f,
            right = 360f,
            bottom = 270f,
        )

        assertEquals(
            CropBounds(left = 0, top = 6, width = 320, height = 234),
            cropBoundsForDetection(detection, sourceWidth = 320, sourceHeight = 240),
        )
    }

    @Test
    fun `parseModelClassLabels parses indexed metadata map`() {
        assertEquals(
            listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL),
            parseModelClassLabels("{0: 'Medicine_Box', 1: 'Text_Region'}"),
        )
    }

    @Test
    fun `modelClassLabelsIssue reports missing metadata labels`() {
        assertEquals("模型缺少类别标签元数据", modelClassLabelsIssue(emptyList()))
    }

    @Test
    fun `modelClassLabelsIssue accepts labeled models`() {
        assertNull(modelClassLabelsIssue(listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL)))
    }

    @Test
    fun `supportsRequiredClassLabels requires medicine box and text region`() {
        assertTrue(supportsRequiredClassLabels(listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL)))
    }

    @Test
    fun `summarizeDetections reports top generic labels in compatibility mode`() {
        val detections = listOf(
            YoloDetection(
                label = "bottle",
                confidence = 0.91f,
                left = 10f,
                top = 12f,
                right = 100f,
                bottom = 180f,
            ),
            YoloDetection(
                label = "cup",
                confidence = 0.78f,
                left = 120f,
                top = 30f,
                right = 200f,
                bottom = 160f,
            ),
        )

        assertEquals("检测到 bottle、cup", summarizeDetections(detections))
    }

    @Test
    fun `summarizeDetections reports no objects when detections empty`() {
        assertEquals("未检测到已知目标", summarizeDetections(emptyList()))
    }

    @Test
    fun `parseDetections supports row first output with objectness`() {
        val detections = parseDetections(
            rawOutput = arrayOf(
                arrayOf(
                    floatArrayOf(160f, 160f, 80f, 100f, 0.9f, 0.8f, 0.1f),
                    floatArrayOf(220f, 170f, 70f, 90f, 0.7f, 0.2f, 0.95f),
                ),
            ),
            classLabels = listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL),
        )

        assertEquals(2, detections.size)
        assertEquals(MEDICINE_BOX_LABEL, detections[0].label)
        assertEquals(0.72f, detections[0].confidence, 0.0001f)
        assertEquals(TEXT_REGION_LABEL, detections[1].label)
        assertEquals(0.665f, detections[1].confidence, 0.0001f)
    }

    @Test
    fun `parseDetections supports channel first output without objectness`() {
        val detections = parseDetections(
            rawOutput = arrayOf(
                arrayOf(
                    floatArrayOf(120f, 240f),
                    floatArrayOf(100f, 180f),
                    floatArrayOf(60f, 80f),
                    floatArrayOf(50f, 90f),
                    floatArrayOf(0.91f, 0.15f),
                    floatArrayOf(0.05f, 0.88f),
                ),
            ),
            classLabels = listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL),
        )

        assertEquals(2, detections.size)
        assertEquals(MEDICINE_BOX_LABEL, detections[0].label)
        assertEquals(TEXT_REGION_LABEL, detections[1].label)
    }

    @Test
    fun `resolveClassLabels uses metadata labels when present`() {
        val labels = resolveClassLabels(
            metadataLabels = listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL),
        )

        assertEquals(listOf(MEDICINE_BOX_LABEL, TEXT_REGION_LABEL), labels)
    }

    @Test
    fun `resolveClassLabels keeps metadata empty without explicit labels`() {
        val labels = resolveClassLabels(
            metadataLabels = emptyList(),
        )

        assertTrue(labels.isEmpty())
    }

    @Test
    fun `supportsRequiredClassLabels rejects models missing text region`() {
        assertFalse(supportsRequiredClassLabels(listOf(MEDICINE_BOX_LABEL)))
    }

    @Test
    fun `supportsRequiredClassLabels rejects models missing medicine box`() {
        assertFalse(supportsRequiredClassLabels(listOf(TEXT_REGION_LABEL)))
    }

    @Test
    fun `supportsRequiredClassLabels does not block compatibility mode`() {
        assertFalse(supportsRequiredClassLabels(listOf("person", "bottle", "cup")))
    }
}
