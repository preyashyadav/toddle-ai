package com.toddleai.app.data.models

data class StepMeasurement(
    val duration: Float,
    val endingSide: Side,
    val confidence: Float,
)

data class TemporalMetrics(
    val stepTimes: List<StepMeasurement>,
    val meanStepTime: Float,
    val cadence: Float,
    val leftMeanStepTime: Float,
    val rightMeanStepTime: Float,
    val timingDifferenceMs: Float,
    val symmetryRatio: Float,
    val stepTimeCoV: Float,
    val usableStepCount: Int,
    val usableCycleCount: Int,
)
