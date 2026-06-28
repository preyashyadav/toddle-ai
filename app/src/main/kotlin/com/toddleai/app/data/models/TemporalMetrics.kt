package com.toddleai.app.data.models

data class StepMeasurement(
    val duration: Float,
    val endingSide: Side,
    val confidence: Float,
)

data class TemporalMetrics(
    val stepTimes: List<StepMeasurement>,
    val meanStepTime: Float,
    // Median step time drives cadence and symmetry (robust to outliers, per the gait-engine spec).
    val medianStepTime: Float,
    val cadence: Float,
    val leftMeanStepTime: Float,
    val rightMeanStepTime: Float,
    val timingDifferenceMs: Float,
    val symmetryRatio: Float,
    // Engineering step-time asymmetry: 100·|L−R| / (0.5·(|L|+|R|)).
    val stepTimeAsymmetryPct: Float,
    val stepTimeCoV: Float,
    val usableStepCount: Int,
    val usableCycleCount: Int,
)
