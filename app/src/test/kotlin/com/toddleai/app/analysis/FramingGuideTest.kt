package com.toddleai.app.analysis

import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.MediaPipeLandmarks
import com.toddleai.app.data.models.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class FramingGuideTest {

    private val guide = FramingGuide()
    private val frameSize = 1000

    @Test
    fun noPoseIsSearching() {
        assertEquals(FramingHint.SEARCHING, guide.evaluate(null).hint)
    }

    @Test
    fun unknownFrameSizeIsNeutral() {
        val pose = PoseFrame(0, 0, fullBody(centerX = 0.5f, topY = 0.1f, feetY = 0.9f), sourceWidth = 0, sourceHeight = 0)
        assertEquals(FramingHint.NONE, guide.evaluate(pose).hint)
    }

    @Test
    fun tooFewLandmarksIsSearching() {
        val landmarks = blankLandmarks().toMutableList()
        landmarks[MediaPipeLandmarks.LEFT_SHOULDER] = visible(0.5f, 0.4f)
        landmarks[MediaPipeLandmarks.RIGHT_SHOULDER] = visible(0.5f, 0.4f)
        assertEquals(FramingHint.SEARCHING, guide.evaluate(frame(landmarks)).hint)
    }

    @Test
    fun centeredFullBodyIsGood() {
        val pose = frame(fullBody(centerX = 0.5f, topY = 0.12f, feetY = 0.9f))
        assertEquals(FramingHint.NONE, guide.evaluate(pose).hint)
    }

    @Test
    fun missingFeetAimsDown() {
        val landmarks = fullBody(centerX = 0.5f, topY = 0.12f, feetY = 0.9f).toMutableList()
        // Hide all foot landmarks (occluded / below frame).
        for (index in FOOT_INDICES) landmarks[index] = hidden(0.5f, 0.95f)
        assertEquals(FramingHint.AIM_DOWN, guide.evaluate(frame(landmarks)).hint)
    }

    @Test
    fun clippedTopAndBottomMovesBack() {
        val pose = frame(fullBody(centerX = 0.5f, topY = 0.01f, feetY = 0.99f))
        assertEquals(FramingHint.MOVE_BACK, guide.evaluate(pose).hint)
    }

    @Test
    fun clippedTopOnlyAimsUp() {
        val pose = frame(fullBody(centerX = 0.5f, topY = 0.01f, feetY = 0.7f))
        assertEquals(FramingHint.AIM_UP, guide.evaluate(pose).hint)
    }

    @Test
    fun smallBodyMovesCloser() {
        val pose = frame(fullBody(centerX = 0.5f, topY = 0.45f, feetY = 0.7f))
        assertEquals(FramingHint.MOVE_CLOSER, guide.evaluate(pose).hint)
    }

    @Test
    fun driftedLeftPansLeft() {
        val pose = frame(fullBody(centerX = 0.15f, topY = 0.12f, feetY = 0.9f))
        assertEquals(FramingHint.PAN_LEFT, guide.evaluate(pose).hint)
    }

    @Test
    fun driftedRightPansRight() {
        val pose = frame(fullBody(centerX = 0.85f, topY = 0.12f, feetY = 0.9f))
        assertEquals(FramingHint.PAN_RIGHT, guide.evaluate(pose).hint)
    }

    // --- helpers ---------------------------------------------------------------

    private fun frame(landmarks: List<Landmark>): PoseFrame =
        PoseFrame(0, 0, landmarks, sourceWidth = frameSize, sourceHeight = frameSize)

    private fun visible(nx: Float, ny: Float) = Landmark(nx * frameSize, ny * frameSize, 0.9f)
    private fun hidden(nx: Float, ny: Float) = Landmark(nx * frameSize, ny * frameSize, 0.1f)

    private fun blankLandmarks(): List<Landmark> = List(33) { Landmark(0f, 0f, 0f) }

    /** Builds a plausible full-body skeleton spanning [topY]..[feetY] centered on [centerX]. */
    private fun fullBody(centerX: Float, topY: Float, feetY: Float): List<Landmark> {
        val l = blankLandmarks().toMutableList()
        val midY = (topY + feetY) / 2f
        val kneeY = midY + (feetY - midY) * 0.5f
        val leftX = centerX - 0.06f
        val rightX = centerX + 0.06f

        l[MediaPipeLandmarks.LEFT_SHOULDER] = visible(leftX, topY)
        l[MediaPipeLandmarks.RIGHT_SHOULDER] = visible(rightX, topY)
        l[MediaPipeLandmarks.LEFT_HIP] = visible(leftX, midY)
        l[MediaPipeLandmarks.RIGHT_HIP] = visible(rightX, midY)
        l[MediaPipeLandmarks.LEFT_KNEE] = visible(leftX, kneeY)
        l[MediaPipeLandmarks.RIGHT_KNEE] = visible(rightX, kneeY)
        l[MediaPipeLandmarks.LEFT_ANKLE] = visible(leftX, feetY)
        l[MediaPipeLandmarks.RIGHT_ANKLE] = visible(rightX, feetY)
        l[MediaPipeLandmarks.LEFT_HEEL] = visible(leftX, feetY)
        l[MediaPipeLandmarks.RIGHT_HEEL] = visible(rightX, feetY)
        l[MediaPipeLandmarks.LEFT_FOOT_INDEX] = visible(leftX + 0.02f, feetY)
        l[MediaPipeLandmarks.RIGHT_FOOT_INDEX] = visible(rightX + 0.02f, feetY)
        return l
    }

    private companion object {
        val FOOT_INDICES = intArrayOf(
            MediaPipeLandmarks.LEFT_ANKLE, MediaPipeLandmarks.RIGHT_ANKLE,
            MediaPipeLandmarks.LEFT_HEEL, MediaPipeLandmarks.RIGHT_HEEL,
            MediaPipeLandmarks.LEFT_FOOT_INDEX, MediaPipeLandmarks.RIGHT_FOOT_INDEX,
        )
    }
}
