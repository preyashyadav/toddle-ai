package com.toddleai.app.analysis

import com.toddleai.app.data.GaitNorms
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.TemporalMetrics
import java.util.Locale
import kotlin.math.abs

class ObservationEngine {

    fun generateObservations(
        metrics: TemporalMetrics,
        childAgeMonths: Int,
        captureConfidence: CaptureConfidence,
    ): List<Observation> {
        val observations = mutableListOf<Observation>()
        val cadenceRange = GaitNorms.getCadenceRange(childAgeMonths)
        val milestoneContext = GaitNorms.getWalkingMilestoneContext(childAgeMonths)

        observations += Observation(
            type = "cadence",
            measurement = "${format1(metrics.cadence)} steps/min",
            context = "Your child's cadence was ${format1(metrics.cadence)} steps/min. Published research reports ranges of approximately ${format0(cadenceRange.low)}-${format0(cadenceRange.high)} steps/min for children around this age, with substantial individual variation. ${cadenceRange.source}. $milestoneContext",
            note = "This cadence value is provided as developmental context for this recording.",
            confidence = confidenceLabel(captureConfidence, metrics.usableStepCount),
        )

        if (metrics.usableStepCount < MIN_STEPS_FOR_PATTERN_RULES) {
            observations += Observation(
                type = "low_data",
                measurement = "${metrics.usableStepCount} usable steps",
                context = "Only a small number of usable steps were available from this recording. Short recordings can make temporal patterns less stable from one pass to the next.",
                note = "Try recording another clip with more continuous walking, both feet visible, and a steady side view.",
                confidence = confidenceLabel(captureConfidence, metrics.usableStepCount),
            )
            return observations
        }

        var hasPatternObservation = false

        if (metrics.timingDifferenceMs > TIMING_DIFFERENCE_THRESHOLD_MS) {
            hasPatternObservation = true
            observations += Observation(
                type = "timing_difference",
                measurement = "Left ${format3(metrics.leftMeanStepTime)} s, right ${format3(metrics.rightMeanStepTime)} s, difference ${format1(metrics.timingDifferenceMs)} ms",
                context = "A repeated left/right timing difference was observed in this recording. Published developmental gait studies show that step timing can vary with age, attention, walking speed, and recording quality.",
                note = "This is an observation from this recording, not a health judgment.",
                confidence = confidenceLabel(captureConfidence, metrics.usableStepCount),
            )
        }

        val normalizedCoV = normalizedCoefficientOfVariation(metrics.stepTimeCoV)
        if (normalizedCoV > STEP_TIME_COV_THRESHOLD) {
            hasPatternObservation = true
            observations += Observation(
                type = "variability",
                measurement = "${format1(normalizedCoV * 100f)}% step-time variation",
                context = "The step-time variability in this recording was ${format1(normalizedCoV * 100f)}%. Higher variability can appear in younger walkers and can also reflect fatigue, distraction, pace changes, or coordination differences during a short recording.",
                note = "This variability description is intended as context for this recording only.",
                confidence = confidenceLabel(captureConfidence, metrics.usableStepCount),
            )
        }

        if (!hasPatternObservation) {
            observations += Observation(
                type = "summary",
                measurement = "Cadence ${format1(metrics.cadence)} steps/min, mean step time ${format3(metrics.meanStepTime)} s",
                context = "For this recording, the temporal measures stayed close to the broad published ranges used for age-based context. Individual variation is expected, especially during early walking development.",
                note = "These observations describe this recording only and can be paired with another recording if you want a more stable comparison.",
                confidence = confidenceLabel(captureConfidence, metrics.usableStepCount),
            )
        }

        return observations
    }

    private fun confidenceLabel(
        captureConfidence: CaptureConfidence,
        usableStepCount: Int,
    ): String {
        return when {
            captureConfidence == CaptureConfidence.REJECT -> "LOW"
            usableStepCount in 2..3 -> "LOW"
            captureConfidence == CaptureConfidence.LOW -> "LOW"
            captureConfidence == CaptureConfidence.MEDIUM -> "MEDIUM"
            else -> "HIGH"
        }
    }

    private fun normalizedCoefficientOfVariation(value: Float): Float {
        return if (value > 1f) value / 100f else value
    }

    private fun format0(value: Float): String = String.format(Locale.US, "%.0f", value)

    private fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun format3(value: Float): String = String.format(Locale.US, "%.3f", value)

    private companion object {
        const val MIN_STEPS_FOR_PATTERN_RULES = 5
        const val TIMING_DIFFERENCE_THRESHOLD_MS = 50f
        const val STEP_TIME_COV_THRESHOLD = 0.15f
    }
}
