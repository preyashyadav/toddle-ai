package com.toddleai.app.analysis

import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.FrameStatus

data class GuidanceState(
    val message: String?,
    val stepCount: Int,
    val stepsNeeded: Int,
    val isRecordingAcceptable: Boolean,
    val overallQuality: QualityLevel,
)

enum class QualityLevel {
    EXCELLENT,
    GOOD,
    NEEDS_ADJUSTMENT,
    POOR,
}

class GuidanceEngine {

    fun getGuidanceMessage(
        recentQualities: List<FrameQuality>,
        totalGoodSteps: Int,
        recordingDuration: Float,
    ): GuidanceState {
        val stepGoal = MINIMUM_GOOD_STEPS
        val stepsNeeded = (stepGoal - totalGoodSteps).coerceAtLeast(0)
        val acceptable = totalGoodSteps >= ACCEPTABLE_STEP_COUNT

        if (recentQualities.isEmpty()) {
            return GuidanceState(
                message = "Start walking when you're ready",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                isRecordingAcceptable = false,
                overallQuality = QualityLevel.NEEDS_ADJUSTMENT,
            )
        }

        if (recentQualities.none { it.fullBodyInFrame }) {
            return state(
                message = "Move the phone back — we need to see the full body",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = QualityLevel.POOR,
            )
        }

        if (recentQualities.none { it.bothFeetVisible }) {
            return state(
                message = "Lower the phone — both feet need to be visible",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = QualityLevel.POOR,
            )
        }

        if (recentQualities.none { it.cameraStable }) {
            return state(
                message = "Hold the phone steady",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = QualityLevel.NEEDS_ADJUSTMENT,
            )
        }

        val rejectedCount = recentQualities.count { it.status == FrameStatus.REJECTED }
        if (rejectedCount > recentQualities.size / 2) {
            return state(
                message = "Better lighting would help — move to a brighter area",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = QualityLevel.NEEDS_ADJUSTMENT,
            )
        }

        if (totalGoodSteps >= STRONG_CAPTURE_STEP_COUNT) {
            return state(
                message = "Great capture! You can stop recording, or keep going for more data",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = true,
                quality = QualityLevel.EXCELLENT,
            )
        }

        if (totalGoodSteps >= stepGoal && recordingDuration > MIN_ACCEPTABLE_DURATION_SECONDS) {
            return state(
                message = "Great capture! You can stop recording, or keep going for more data",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = QualityLevel.GOOD,
            )
        }

        if (looksGood(recentQualities) && totalGoodSteps < stepGoal) {
            return state(
                message = "$totalGoodSteps good steps — $stepsNeeded more needed",
                stepCount = totalGoodSteps,
                stepsNeeded = stepsNeeded,
                acceptable = acceptable,
                quality = if (totalGoodSteps >= 3) QualityLevel.GOOD else QualityLevel.EXCELLENT,
            )
        }

        return state(
            message = null,
            stepCount = totalGoodSteps,
            stepsNeeded = stepsNeeded,
            acceptable = acceptable,
            quality = overallQuality(recentQualities),
        )
    }

    private fun looksGood(recentQualities: List<FrameQuality>): Boolean {
        return recentQualities.all {
            it.fullBodyInFrame &&
                it.bothFeetVisible &&
                it.cameraStable &&
                it.status != FrameStatus.REJECTED
        }
    }

    private fun overallQuality(recentQualities: List<FrameQuality>): QualityLevel {
        val goodCount = recentQualities.count { it.status == FrameStatus.GOOD }
        val partialCount = recentQualities.count { it.status == FrameStatus.PARTIAL }
        val rejectedCount = recentQualities.count { it.status == FrameStatus.REJECTED }

        return when {
            rejectedCount > recentQualities.size / 2 -> QualityLevel.POOR
            goodCount == recentQualities.size -> QualityLevel.EXCELLENT
            goodCount + partialCount == recentQualities.size -> QualityLevel.GOOD
            else -> QualityLevel.NEEDS_ADJUSTMENT
        }
    }

    private fun state(
        message: String?,
        stepCount: Int,
        stepsNeeded: Int,
        acceptable: Boolean,
        quality: QualityLevel,
    ): GuidanceState {
        return GuidanceState(
            message = message,
            stepCount = stepCount,
            stepsNeeded = stepsNeeded,
            isRecordingAcceptable = acceptable,
            overallQuality = quality,
        )
    }

    private companion object {
        const val MINIMUM_GOOD_STEPS = 5
        const val ACCEPTABLE_STEP_COUNT = 8
        const val STRONG_CAPTURE_STEP_COUNT = 8
        const val MIN_ACCEPTABLE_DURATION_SECONDS = 5f
    }
}
