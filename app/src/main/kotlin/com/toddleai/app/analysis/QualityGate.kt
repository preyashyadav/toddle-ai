package com.toddleai.app.analysis

import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.FrameStatus
import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.PoseFrame
import com.toddleai.app.data.models.leftAnkle
import com.toddleai.app.data.models.leftFootIndex
import com.toddleai.app.data.models.leftHeel
import com.toddleai.app.data.models.leftHip
import com.toddleai.app.data.models.leftKnee
import com.toddleai.app.data.models.leftShoulder
import com.toddleai.app.data.models.rightAnkle
import com.toddleai.app.data.models.rightFootIndex
import com.toddleai.app.data.models.rightHeel
import com.toddleai.app.data.models.rightHip
import com.toddleai.app.data.models.rightKnee
import com.toddleai.app.data.models.rightShoulder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class QualityGate(
    private val maxDriftPixels: Float = 15f,
) {

    fun assessFrame(
        current: PoseFrame,
        previous: PoseFrame?,
        fps: Float,
    ): FrameQuality {
        val majorLandmarks = listOf(
            current.leftHip(),
            current.rightHip(),
            current.leftKnee(),
            current.rightKnee(),
            current.leftAnkle(),
            current.rightAnkle(),
            current.leftHeel(),
            current.rightHeel(),
            current.leftFootIndex(),
            current.rightFootIndex(),
        )
        val feetLandmarks = listOf(
            current.leftHeel(),
            current.rightHeel(),
            current.leftFootIndex(),
            current.rightFootIndex(),
        )
        val fullBodyLandmarks = listOf(
            current.leftShoulder(),
            current.rightShoulder(),
            current.leftAnkle(),
            current.rightAnkle(),
        )

        val allMajorLandmarksVisible = majorLandmarks.all { it.visibility > 0.4f }
        val bothFeetVisible = feetLandmarks.all { it.visibility > 0.5f }
        val fullBodyInFrame = fullBodyLandmarks.all { it.visibility > 0.3f }
        val cameraStable = previous?.let { isCameraStable(current, it) } ?: true
        val landmarkConfidenceMean = current.landmarks
            .takeIf { it.isNotEmpty() }
            ?.map { it.visibility }
            ?.average()
            ?.toFloat()
            ?: 0f

        val status = when {
            !bothFeetVisible || landmarkConfidenceMean < 0.4f -> FrameStatus.REJECTED
            allMajorLandmarksVisible && fullBodyInFrame && cameraStable && landmarkConfidenceMean > 0.6f -> FrameStatus.GOOD
            bothFeetVisible && landmarkConfidenceMean > 0.4f -> FrameStatus.PARTIAL
            else -> FrameStatus.REJECTED
        }

        return FrameQuality(
            frameIndex = current.frameIndex,
            allMajorLandmarksVisible = allMajorLandmarksVisible,
            bothFeetVisible = bothFeetVisible,
            fullBodyInFrame = fullBodyInFrame,
            cameraStable = cameraStable,
            landmarkConfidenceMean = landmarkConfidenceMean,
            status = status,
        )
    }

    fun assessRecording(
        frameQualities: List<FrameQuality>,
        detectedSteps: Int,
    ): CaptureAssessment {
        if (frameQualities.isEmpty()) {
            return CaptureAssessment(
                confidence = CaptureConfidence.REJECT,
                issues = listOf("No usable frames were available from this recording."),
                bestSegmentStart = 0,
                bestSegmentEnd = 0,
                usableStepCount = 0,
            )
        }

        val goodFrames = frameQualities.filter { it.status == FrameStatus.GOOD }
        val goodFrameRatio = goodFrames.size.toFloat() / frameQualities.size.toFloat()
        val bestSegment = longestGoodSegment(frameQualities)

        val confidence = when {
            detectedSteps >= 6 && goodFrameRatio > 0.70f && bestSegment.length >= MIN_GOOD_SEGMENT_FRAMES -> CaptureConfidence.HIGH
            detectedSteps >= 5 && goodFrameRatio > 0.50f && bestSegment.length >= MIN_GOOD_SEGMENT_FRAMES -> CaptureConfidence.MEDIUM
            detectedSteps >= 3 -> CaptureConfidence.LOW
            else -> CaptureConfidence.REJECT
        }

        val issues = mutableListOf<String>()

        val hiddenFeetRatio = frameQualities.count { !it.bothFeetVisible }.toFloat() / frameQualities.size.toFloat()
        if (hiddenFeetRatio > 0f) {
            issues += "Feet were hidden in ${formatPercent(hiddenFeetRatio)} of frames. Record from knee height."
        }

        val unstableRatio = frameQualities.count { !it.cameraStable }.toFloat() / frameQualities.size.toFloat()
        if (unstableRatio > 0.10f) {
            issues += "Camera movement detected. Hold the phone still or prop it up."
        }

        if (detectedSteps < 5) {
            issues += "Only $detectedSteps steps detected. Record a longer walking sequence."
        }

        val likelyFrontFacingRatio = frameQualities.count {
            it.bothFeetVisible && it.fullBodyInFrame && !it.allMajorLandmarksVisible
        }.toFloat() / frameQualities.size.toFloat()
        if (likelyFrontFacingRatio > 0.35f) {
            issues += "Child appears to be facing the camera. Record from the side."
        }

        val segmentStart = if (bestSegment.length >= MIN_GOOD_SEGMENT_FRAMES) bestSegment.start else 0
        val segmentEnd = if (bestSegment.length >= MIN_GOOD_SEGMENT_FRAMES) bestSegment.end else 0

        return CaptureAssessment(
            confidence = confidence,
            issues = issues,
            bestSegmentStart = segmentStart,
            bestSegmentEnd = segmentEnd,
            usableStepCount = detectedSteps,
        )
    }

    private fun isCameraStable(
        current: PoseFrame,
        previous: PoseFrame,
    ): Boolean {
        val currentShoulderWidth = horizontalDistance(current.leftShoulder(), current.rightShoulder())
        val previousShoulderWidth = horizontalDistance(previous.leftShoulder(), previous.rightShoulder())
        val currentTorsoHeight = torsoHeight(current)
        val previousTorsoHeight = torsoHeight(previous)

        if (
            currentShoulderWidth <= MIN_BODY_MEASUREMENT_PIXELS ||
            previousShoulderWidth <= MIN_BODY_MEASUREMENT_PIXELS ||
            currentTorsoHeight <= MIN_BODY_MEASUREMENT_PIXELS ||
            previousTorsoHeight <= MIN_BODY_MEASUREMENT_PIXELS
        ) {
            return true
        }

        val shoulderWidthShift = normalizedChange(currentShoulderWidth, previousShoulderWidth)
        val torsoHeightShift = normalizedChange(currentTorsoHeight, previousTorsoHeight)

        return shoulderWidthShift <= MAX_BODY_SCALE_SHIFT && torsoHeightShift <= MAX_BODY_SCALE_SHIFT
    }

    private fun torsoHeight(frame: PoseFrame): Float {
        val shoulderY = (frame.leftShoulder().y + frame.rightShoulder().y) / 2f
        val hipY = (frame.leftHip().y + frame.rightHip().y) / 2f
        return abs(hipY - shoulderY)
    }

    private fun horizontalDistance(
        first: Landmark,
        second: Landmark,
    ): Float {
        return abs(first.x - second.x)
    }

    private fun normalizedChange(
        current: Float,
        previous: Float,
    ): Float {
        return abs(current - previous) / max(previous, MIN_BODY_MEASUREMENT_PIXELS)
    }

    private fun longestGoodSegment(frameQualities: List<FrameQuality>): GoodSegment {
        var best = GoodSegment(0, 0, 0)
        var currentStart = -1
        var currentLength = 0

        for (frameQuality in frameQualities) {
            if (frameQuality.status == FrameStatus.GOOD) {
                if (currentStart == -1) {
                    currentStart = frameQuality.frameIndex
                }
                currentLength++
                val currentEnd = frameQuality.frameIndex
                if (currentLength > best.length) {
                    best = GoodSegment(
                        start = currentStart,
                        end = currentEnd,
                        length = currentLength,
                    )
                }
            } else {
                currentStart = -1
                currentLength = 0
            }
        }

        return best
    }

    private fun formatPercent(ratio: Float): String {
        return String.format(Locale.US, "%.0f%%", ratio * 100f)
    }

    private data class GoodSegment(
        val start: Int,
        val end: Int,
        val length: Int,
    )

    private companion object {
        const val MIN_GOOD_SEGMENT_FRAMES = 30
        const val MIN_BODY_MEASUREMENT_PIXELS = 8f
        const val MAX_BODY_SCALE_SHIFT = 0.18f
    }
}
