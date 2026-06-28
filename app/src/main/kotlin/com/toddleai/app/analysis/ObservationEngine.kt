package com.toddleai.app.analysis

import com.toddleai.app.data.GaitNorms
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.MetricStatus
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.TemporalMetrics
import java.util.Locale

class ObservationEngine {

    /**
     * Produces one card per gait metric: what the clip captured, and the typical benchmark for the
     * child's age. No capture-quality / confidence wording — just the measured value vs. the
     * age-appropriate range, with a neutral in/above/below note.
     */
    fun generateObservations(
        metrics: TemporalMetrics,
        childAgeMonths: Int,
        @Suppress("UNUSED_PARAMETER") captureConfidence: CaptureConfidence,
    ): List<Observation> {
        val ageLabel = ageLabel(childAgeMonths)
        val cadenceRange = GaitNorms.getCadenceRange(childAgeMonths)

        val observations = mutableListOf<Observation>()

        // --- Cadence ---
        val cadenceRangeText = "${format0(cadenceRange.low)}–${format0(cadenceRange.high)} steps/min"
        val cadenceComparison = when {
            metrics.cadence in cadenceRange.low..cadenceRange.high -> "In the typical range." to MetricStatus.TYPICAL
            metrics.cadence < cadenceRange.low -> "A little below the typical range." to MetricStatus.ELEVATED
            else -> "A little above the typical range." to MetricStatus.ELEVATED
        }
        observations += Observation(
            type = "cadence",
            measurement = "Cadence: ${format0(metrics.cadence)} steps/min",
            context = "Typical for a $ageLabel: $cadenceRangeText.",
            note = cadenceComparison.first,
            confidence = "",
            status = cadenceComparison.second,
        )

        // --- Left/right symmetry ---
        val symmetryInRange = metrics.stepTimeAsymmetryPct <= ASYMMETRY_THRESHOLD_PCT
        observations += Observation(
            type = "symmetry",
            measurement = "Left–right symmetry: ${format0(metrics.stepTimeAsymmetryPct)}% difference",
            context = "Even left/right step timing is typically within ${format0(ASYMMETRY_THRESHOLD_PCT)}% " +
                "(left ${format2(metrics.leftMeanStepTime)} s, right ${format2(metrics.rightMeanStepTime)} s).",
            note = if (symmetryInRange) "In the typical range." else "Above the typical range.",
            confidence = "",
            status = if (symmetryInRange) MetricStatus.TYPICAL else MetricStatus.ELEVATED,
        )

        // --- Step rhythm consistency ---
        val variabilityInRange = metrics.stepTimeCoV <= STEP_TIME_COV_THRESHOLD_PCT
        observations += Observation(
            type = "variability",
            measurement = "Step rhythm: ${format0(metrics.stepTimeCoV)}% variation",
            context = "Step timing is typically within ${format0(STEP_TIME_COV_THRESHOLD_PCT)}% variation; " +
                "young children are naturally a bit higher.",
            note = if (variabilityInRange) "In the typical range." else "Above the typical range.",
            confidence = "",
            status = if (variabilityInRange) MetricStatus.TYPICAL else MetricStatus.ELEVATED,
        )

        return observations
    }

    /** Parent-facing age phrase, e.g. "2-year-old" (or "20-month-old" under a year). */
    private fun ageLabel(months: Int): String {
        val years = months / 12
        return if (years < 1) "${months}-month-old" else "$years-year-old"
    }

    private fun format0(value: Float): String = String.format(Locale.US, "%.0f", value)

    private fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val ASYMMETRY_THRESHOLD_PCT = 10f
        const val STEP_TIME_COV_THRESHOLD_PCT = 15f
    }
}
