package com.toddleai.app.analysis

import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.Side
import com.toddleai.app.data.models.StepMeasurement
import com.toddleai.app.data.models.TemporalMetrics
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

        val meanStepTime = steps.meanDuration()
        val leftMean = leftSteps.meanDuration()
        val rightMean = rightSteps.meanDuration()
        val cadence = if (meanStepTime > 0f) 60f / meanStepTime else 0f
        val timingDifferenceMs = when {
            leftMean > 0f && rightMean > 0f -> kotlin.math.abs(leftMean - rightMean) * 1000f
            else -> 0f
        }
        val symmetryRatio = when {
            leftMean > 0f && rightMean > 0f -> leftMean / rightMean
            else -> 0f
        }
        val stepTimeCoV = when {
            meanStepTime > 0f && steps.size > 1 -> (steps.standardDeviation() / meanStepTime) * 100f
            else -> 0f
        }

        return TemporalMetrics(
            stepTimes = steps,
            meanStepTime = meanStepTime,
            cadence = cadence,
            leftMeanStepTime = leftMean,
            rightMeanStepTime = rightMean,
            timingDifferenceMs = timingDifferenceMs,
            symmetryRatio = symmetryRatio,
            stepTimeCoV = stepTimeCoV,
            usableStepCount = steps.size,
            usableCycleCount = minOf(leftSteps.size, rightSteps.size),
        )
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
        cadence = 0f,
        leftMeanStepTime = 0f,
        rightMeanStepTime = 0f,
        timingDifferenceMs = 0f,
        symmetryRatio = 0f,
        stepTimeCoV = 0f,
        usableStepCount = 0,
        usableCycleCount = 0,
    )

    private companion object {
        const val MIN_STEP_TIME_SECONDS = 0.25f
        const val MAX_STEP_TIME_SECONDS = 1.5f
    }
}
