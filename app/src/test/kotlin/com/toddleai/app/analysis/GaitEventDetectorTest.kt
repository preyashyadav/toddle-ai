package com.toddleai.app.analysis

import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.MediaPipeLandmarks
import com.toddleai.app.data.models.PoseFrame
import com.toddleai.app.data.models.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class GaitEventDetectorTest {

    private val detector = GaitEventDetector()

    @Test
    fun emptyInputReturnsEmptyOutput() {
        val result = detector.detectEvents(emptyList(), fps = 30f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun syntheticSineWaveProducesExpectedPeaks() {
        val fps = 30f
        val frames = syntheticWalkingFrames(
            frameCount = 120,
            fps = fps,
            periodFrames = 30.0,
        )

        val events = detector.detectEvents(frames, fps)
        val leftFrames = events.filter { it.side == Side.LEFT }.map { it.frameIndex }
        val rightFrames = events.filter { it.side == Side.RIGHT }.map { it.frameIndex }

        assertPeakNear(leftFrames, 8)
        assertPeakNear(leftFrames, 38)
        assertPeakNear(leftFrames, 68)
        assertPeakNear(rightFrames, 23)
        assertPeakNear(rightFrames, 53)
        assertPeakNear(rightFrames, 83)
    }

    @Test
    fun stepsShorterThanQuarterSecondAreRejected() {
        val fps = 30f
        val frames = syntheticWalkingFrames(
            frameCount = 120,
            fps = fps,
            periodFrames = 12.0,
        )

        val events = detector.detectEvents(frames, fps)

        assertFalse(events.isEmpty())
        events.zipWithNext().forEach { (first, second) ->
            assertTrue(second.timeSeconds - first.timeSeconds >= 0.25f)
        }
    }

    @Test
    fun lowVisibilityShortGapsAreInterpolatedButLongGapsBreakSegments() {
        val fps = 30f
        val shortGapFrames = syntheticWalkingFrames(
            frameCount = 90,
            fps = fps,
            periodFrames = 30.0,
            leftLowVisibilityRange = 36..39,
        )
        val longGapFrames = syntheticWalkingFrames(
            frameCount = 90,
            fps = fps,
            periodFrames = 30.0,
            leftLowVisibilityRange = 35..41,
        )

        val shortGapEvents = detector.detectEvents(shortGapFrames, fps)
        val longGapEvents = detector.detectEvents(longGapFrames, fps)

        val shortLeftFrames = shortGapEvents.filter { it.side == Side.LEFT }.map { it.frameIndex }
        val longLeftFrames = longGapEvents.filter { it.side == Side.LEFT }.map { it.frameIndex }

        assertPeakNear(shortLeftFrames, 38)
        assertFalse(longLeftFrames.any { kotlin.math.abs(it - 38) <= 2 })
        assertTrue(shortLeftFrames.size > longLeftFrames.size)
    }

    private fun syntheticWalkingFrames(
        frameCount: Int,
        fps: Float,
        periodFrames: Double,
        leftLowVisibilityRange: IntRange? = null,
    ): List<PoseFrame> {
        return List(frameCount) { frameIndex ->
            val phase = (2.0 * PI * frameIndex) / periodFrames
            val leftSignal = sin(phase).toFloat()
            val rightSignal = sin(phase + PI).toFloat()
            val leftVisibility = if (leftLowVisibilityRange?.contains(frameIndex) == true) 0.2f else 1f

            PoseFrame(
                frameIndex = frameIndex,
                timestamp = ((frameIndex / fps) * 1000f).toLong(),
                landmarks = List(33) { landmarkIndex ->
                    when (landmarkIndex) {
                        MediaPipeLandmarks.LEFT_HIP -> Landmark(x = -0.1f, y = 0.5f, visibility = 1f)
                        MediaPipeLandmarks.RIGHT_HIP -> Landmark(x = 0.1f, y = 0.5f, visibility = 1f)
                        MediaPipeLandmarks.LEFT_HEEL -> Landmark(x = leftSignal, y = 1f, visibility = leftVisibility)
                        MediaPipeLandmarks.RIGHT_HEEL -> Landmark(x = rightSignal, y = 1f, visibility = 1f)
                        else -> Landmark(x = 0f, y = 0f, visibility = 1f)
                    }
                },
            )
        }
    }

    private fun assertPeakNear(frames: List<Int>, expected: Int) {
        assertTrue("Expected a peak near frame $expected but got $frames", frames.any { kotlin.math.abs(it - expected) <= 2 })
    }
}
