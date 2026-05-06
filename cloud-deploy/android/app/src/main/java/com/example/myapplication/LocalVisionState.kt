package com.example.myapplication

data class LocalVisionState(
    val status: LocalVisionStatus,
    val summary: String,
    val result: VisionAnalysisResult? = null,
    val issue: LocalVisionIssue? = null,
) {
    fun waitingForNextFrame(): LocalVisionState {
        return if (issue == LocalVisionIssue.MODEL_PIPELINE) {
            this
        } else {
            waitingForNewFrame()
        }
    }

    companion object {
        val IDLE = LocalVisionState(
            status = LocalVisionStatus.IDLE,
            summary = "等待图像帧",
            result = null,
            issue = null,
        )
        val PROCESSING = LocalVisionState(
            status = LocalVisionStatus.PROCESSING,
            summary = "正在分析最新画面",
            result = null,
            issue = null,
        )

        fun waitingForNewFrame(): LocalVisionState {
            return LocalVisionState(
                status = LocalVisionStatus.IDLE,
                summary = "等待新图像帧",
                result = null,
                issue = LocalVisionIssue.NO_NEW_FRAME,
            )
        }
    }
}

enum class LocalVisionStatus {
    IDLE,
    PROCESSING,
    FRAME_ANALYZED,
    ERROR,
}

enum class LocalVisionIssue {
    INPUT_FRAME,
    MODEL_PIPELINE,
    NO_NEW_FRAME,
}
