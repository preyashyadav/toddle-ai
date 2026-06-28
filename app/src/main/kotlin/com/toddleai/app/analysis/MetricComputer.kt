package com.toddleai.app.analysis

import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.Side
import com.toddleai.app.data.models.StepMeasurement
import com.toddleai.app.data.models.TemporalMetrics
import kotlin.math.abs
import kotlin.math.sqrt

class MetricComputer {

    fun computeMetrics(events: List<GaitEvent>): TemporalMetrics {
        if (events.size < 2) return noData()

        val sortedEvents = events.sortedBy { it.frameIndex }
        val steps = sortedEvents.zipWithNext()
            .mapNotNull { (first, second) ->
                if (first.side == second.side) {
                    null
                } else {
                    val duration = second.timeSeconds - first.timeSeconds
                    if (duration in MIN_STEP_TIME_SECONDS..MAX_STEP_TIME_SECONDS) {
                        StepMeasurement(
                            duration = duration,
                            endingSide = second.side,
                            confidence = minOf(first.confidence, second.confidence),
                        )
                    } else {
                        null
                    }
                }
            }

        if (steps.isEmpty()) return noData()

        val leftSteps = steps.filter { it.endingSide == Side.LEFT }
        val rightSteps = steps.filter { it.endingSide == Side.RIGHT }

        // Median step time drives cadence (robust to outliers, engine spec §10.6); mean is kept as a
        // secondary descriptor.
        val medianStepTime = steps.map { it.duration }.median()
        val meanStepTime = steps.meanDuration()
        val leftMean = leftSteps.meanDuration()
        val rightMean = rightSteps.meanDuration()

        val cadence = if (medianStepTime > 0f) 60f / medianStepTime else 0f
        val timingDifferenceMs = when {
            leftMean > 0f && rightMean > 0f -> abs(leftMean - rightMean) * 1000f
            else -> 0f
        }
        val symmetryRatio = when {
            leftMean > 0f && rightMean > 0f -> leftMean / rightMean
            else -> 0f
        }
        // AsymmetryPct = 100·|L−R| / (0.5·(|L|+|R|)+ε)  (engine spec §10.10).
        val asymmetryPct = when {
            leftMean > 0f && rightMean > 0f ->
                100f * abs(leftMean - rightMean) / (0.5f * (leftMean + rightMean) + EPSILON)
            else -> 0f
        }
        // CV% = 100·SD/|mean|  (engine spec §10.11).
        val stepTimeCoV = when {
            meanStepTime > 0f && steps.size > 1 -> (steps.standardDeviation() / meanStepTime) * 100f
            else -> 0f
        }

        return TemporalMetrics(
            stepTimes = steps,
            meanStepTime = meanStepTime,
            medianStepTime = medianStepTime,
            cadence = cadence,
            leftMeanStepTime = leftMean,
            rightMeanStepTime = rightMean,
            timingDifferenceMs = timingDifferenceMs,
            symmetryRatio = symmetryRatio,
            stepTimeAsymmetryPct = asymmetryPct,
            stepTimeCoV = stepTimeCoV,
            usableStepCount = steps.size,
            usableCycleCount = minOf(leftSteps.size, rightSteps.size),
        )
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun List<StepMeasurement>.meanDuration(): Float {
        if (isEmpty()) return 0f
        return sumOf { it.duration.toDouble() }.toFloat() / size
    }

    private fun List<StepMeasurement>.standardDeviation(): Float {
        if (size < 2) return 0f
        val mean = meanDuration().toDouble()
        val variance = sumOf { measurement ->
            val delta = measurement.duration - mean
            delta * delta
        } / size
        return sqrt(variance).toFloat()
    }

    private fun noData(): TemporalMetrics = TemporalMetrics(
        stepTimes = emptyList(),
        meanStepTime = 0f,
        medianStepTime = 0f,
        cadence = 0f,
        leftMeanStepTime = 0f,
        rightMeanStepTime = 0f,
        timingDifferenceMs = 0f,
        symmetryRatio = 0f,
        stepTimeAsymmetryPct = 0f,
        stepTimeCoV = 0f,
        usableStepCount = 0,
        usableCycleCount = 0,
    )

    private companion object {
        const val MIN_STEP_TIME_SECONDS = 0.25f
        const val MAX_STEP_TIME_SECONDS = 1.5f
        const val EPSILON = 1e-6f
    }
}
