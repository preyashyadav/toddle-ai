package com.toddleai.app.analysis

import com.toddleai.app.data.models.MediaPipeLandmarks
import com.toddleai.app.data.models.PoseFrame

/** A real-time camera-aiming cue derived from where the subject sits inside the frame. */
enum class FramingHint {
    /** Framing is good — no correction needed. */
    NONE,

    /** No usable body detected yet. */
    SEARCHING,

    /** Subject's feet/lower body are cut at the bottom — aim the phone down. */
    AIM_DOWN,

    /** Subject is cut at the top — aim the phone up. */
    AIM_UP,

    /** Subject has drifted to the left edge — pan left to re-center. */
    PAN_LEFT,

    /** Subject has drifted to the right edge — pan right to re-center. */
    PAN_RIGHT,

    /** Subject fills the frame — step back to fit the whole body. */
    MOVE_BACK,

    /** Subject is small/far — move a little closer. */
    MOVE_CLOSER,
}

data class FramingGuidance(
    val hint: FramingHint,
    val message: String,
)

/**
 * Turns a live [PoseFrame] into a single directional framing cue so the capture UI can show an arrow
 * guiding the parent to aim the phone for the best gait clip. Side-view gait needs the **whole body
 * with both feet** in frame and reasonably centered, so feet-cutoff is the top priority.
 *
 * Returns one hint at a time (the most important correction) to keep the on-screen guidance calm
 * rather than showing several arrows at once.
 */
class FramingGuide {

    fun evaluate(pose: PoseFrame?): FramingGuidance {
        if (pose == null) return SEARCHING_GUIDANCE

        val width = pose.sourceWidth.toFloat()
        val height = pose.sourceHeight.toFloat()
        // Without a known frame size we can't reason about position; stay neutral (no arrow).
        if (width <= 0f || height <= 0f) return NEUTRAL

        val visible = pose.landmarks.filter { it.visibility >= VISIBILITY_THRESHOLD }
        if (visible.size < MIN_VISIBLE_LANDMARKS) return SEARCHING_GUIDANCE

        var minNx = 1f
        var maxNx = 0f
        var minNy = 1f
        var maxNy = 0f
        for (landmark in visible) {
            val nx = (landmark.x / width).coerceIn(0f, 1f)
            val ny = (landmark.y / height).coerceIn(0f, 1f)
            if (nx < minNx) minNx = nx
            if (nx > maxNx) maxNx = nx
            if (ny < minNy) minNy = ny
            if (ny > maxNy) maxNy = ny
        }
        val centerX = (minNx + maxNx) / 2f
        val heightFraction = maxNy - minNy

        var feetVisible = false
        var lowestFootY = 0f
        for (index in FOOT_LANDMARKS) {
            val landmark = pose.landmarks.getOrNull(index) ?: continue
            if (landmark.visibility >= VISIBILITY_THRESHOLD) {
                feetVisible = true
                val footY = landmark.y / height
                if (footY > lowestFootY) lowestFootY = footY
            }
        }

        // The body is clipped at the bottom if the feet are missing or jammed against the edge,
        // and at the top if the head/shoulders are jammed against the top edge.
        val bottomClipped = !feetVisible || lowestFootY > FEET_NEAR_BOTTOM || maxNy > BODY_NEAR_BOTTOM
        val topClipped = minNy < TOP_CUTOFF

        return when {
            // Cut off at BOTH ends -> the subject is too close to fit; step back.
            topClipped && bottomClipped ->
                FramingGuidance(FramingHint.MOVE_BACK, "Step back to fit the whole body")

            // Only the lower body is cut off -> aim the phone down to capture the feet.
            bottomClipped ->
                FramingGuidance(FramingHint.AIM_DOWN, "Tilt down so both feet stay in view")

            // Only the top is cut off -> aim up.
            topClipped ->
                FramingGuidance(FramingHint.AIM_UP, "Tilt up to fit the whole body")

            // Subject is small in the frame -> too far.
            heightFraction < TOO_FAR_FRACTION ->
                FramingGuidance(FramingHint.MOVE_CLOSER, "Move a little closer")

            // Drifted toward an edge (about to walk out of frame).
            centerX < LEFT_EDGE ->
                FramingGuidance(FramingHint.PAN_LEFT, "Pan left to keep your child centered")
            centerX > RIGHT_EDGE ->
                FramingGuidance(FramingHint.PAN_RIGHT, "Pan right to keep your child centered")

            else -> FramingGuidance(FramingHint.NONE, "Framing looks good — ready to walk")
        }
    }

    private companion object {
        const val VISIBILITY_THRESHOLD = 0.5f
        const val MIN_VISIBLE_LANDMARKS = 6

        // Normalized-Y above which feet are considered cut off at the bottom.
        const val FEET_NEAR_BOTTOM = 0.96f
        const val BODY_NEAR_BOTTOM = 0.985f
        const val TOP_CUTOFF = 0.03f

        // Fraction of frame height the body spans, below which the subject is too far away.
        const val TOO_FAR_FRACTION = 0.45f

        // Horizontal center thresholds (kept near the edges so normal walking doesn't trigger pans).
        const val LEFT_EDGE = 0.22f
        const val RIGHT_EDGE = 0.78f

        val FOOT_LANDMARKS = intArrayOf(
            MediaPipeLandmarks.LEFT_ANKLE,
            MediaPipeLandmarks.RIGHT_ANKLE,
            MediaPipeLandmarks.LEFT_HEEL,
            MediaPipeLandmarks.RIGHT_HEEL,
            MediaPipeLandmarks.LEFT_FOOT_INDEX,
            MediaPipeLandmarks.RIGHT_FOOT_INDEX,
        )

        val SEARCHING_GUIDANCE = FramingGuidance(FramingHint.SEARCHING, "Point the camera at your child")
        val NEUTRAL = FramingGuidance(FramingHint.NONE, "")
    }
}
