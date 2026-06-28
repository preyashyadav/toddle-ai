package com.toddleai.app.data.models

enum class FrameStatus {
    GOOD,
    PARTIAL,
    REJECTED,
}

data class FrameQuality(
    val frameIndex: Int,
    val allMajorLandmarksVisible: Boolean,
    val bothFeetVisible: Boolean,
    val fullBodyInFrame: Boolean,
    val cameraStable: Boolean,
    val landmarkConfidenceMean: Float,
    val status: FrameStatus,
)
