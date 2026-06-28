package com.toddleai.app.data.models

enum class CaptureConfidence {
    HIGH,
    MEDIUM,
    LOW,
    REJECT,
}

data class CaptureAssessment(
    val confidence: CaptureConfidence,
    val issues: List<String>,
    val bestSegmentStart: Int,
    val bestSegmentEnd: Int,
    val usableStepCount: Int,
)
