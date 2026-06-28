package com.toddleai.app.data.models

/**
 * Per-metric interpretation, kept separate from capture confidence:
 *  - TYPICAL: within the expected range for age.
 *  - ELEVATED: crossed a threshold AND the clip was trustworthy enough to say so (a "flag").
 *  - LOW_DATA: the clip was too low-quality / too few steps to judge this metric.
 */
enum class MetricStatus {
    TYPICAL,
    ELEVATED,
    LOW_DATA,
}

data class Observation(
    val type: String,
    val measurement: String,
    val context: String,
    val note: String,
    val confidence: String,
    val status: MetricStatus = MetricStatus.LOW_DATA,
)
