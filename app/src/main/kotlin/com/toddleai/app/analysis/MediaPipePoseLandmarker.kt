package com.toddleai.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.toddleai.app.data.models.Landmark

/**
 * Real gait-capable pose backend.
 *
 * Wraps the MediaPipe Tasks Vision [PoseLandmarker] driven by `pose_landmarker_full.task`, the only
 * artifact in this repo that emits the full **33-landmark BlazePose layout including the lower body**
 * (knees 25/26, ankles 27/28, heels 29/30, foot-index 31/32) that the whole downstream gait pipeline
 * ([QualityGate], [GaitEventDetector], [ReplayAnalyzer]) is built around.
 *
 * The previously-bundled ExecuTorch `pose_landmark_*.pte` is the Qualcomm AI-Hub landmark *stage*,
 * which only outputs landmarks 0-24 (head -> hips) and therefore cannot drive gait analysis at all
 * (see `samples/pose/README.md` for the verified `(1,25,4)` I/O). This class replaces that path.
 *
 * Runs fully on-device (TFLite + XNNPACK CPU delegate, or GPU delegate). [RunningMode.IMAGE] gives a
 * synchronous, deterministic per-frame [detect] that works identically for live camera frames and for
 * frames pulled out of an imported video. Inference is serialized via [synchronized] because a single
 * [PoseLandmarker] instance is not safe to call from multiple threads concurrently.
 */
class MediaPipePoseLandmarker private constructor(
    private val landmarker: PoseLandmarker,
) {

    private val lock = Any()

    /**
     * Runs pose landmark detection on [bitmap] and returns the highest-confidence pose as a flat
     * list of [Landmark] in **original-image pixel coordinates**, matching the convention the rest of
     * the analysis pipeline expects. Returns `null` when no pose is found in the frame.
     */
    fun estimate(bitmap: Bitmap): List<Landmark>? = synchronized(lock) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: PoseLandmarkerResult = landmarker.detect(mpImage)

        val poses = result.landmarks()
        if (poses.isEmpty()) return null

        val normalized = poses[0]
        if (normalized.isEmpty()) return null

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        return normalized.map { lm ->
            // MediaPipe `visibility` is the in-frame/occlusion confidence the pipeline keys on; fall
            // back to `presence` (then 1f) so a model build that omits visibility still degrades safely.
            val confidence = lm.visibility().orElseGet { lm.presence().orElse(1f) }
            Landmark(
                x = (lm.x() * width).coerceIn(0f, width),
                y = (lm.y() * height).coerceIn(0f, height),
                visibility = confidence.coerceIn(0f, 1f),
            )
        }
    }

    fun close() {
        try {
            landmarker.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close PoseLandmarker cleanly", t)
        }
    }

    companion object {
        private const val TAG = "ToddleAIMediaPipePose"

        /** Number of landmarks the BlazePose full model emits (indices 0-32). */
        const val FULL_BODY_LANDMARK_COUNT = 33

        /**
         * Creates a landmarker from a `.task` asset that is bundled in the APK under `assets/`.
         *
         * MediaPipe reads the model straight from the asset stream, so no manual copy-to-cache is
         * needed. This performs native initialization and file I/O, so it MUST be called off the main
         * thread. Throws on failure; callers should guard it and surface a clear error instead of
         * crashing (the old code routed this `.task` through ExecuTorch `Module.load`, which segfaulted).
         */
        fun fromAsset(
            context: Context,
            assetPath: String,
            useGpu: Boolean,
        ): MediaPipePoseLandmarker {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(assetPath)
                .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            val landmarker = PoseLandmarker.createFromOptions(context, options)
            Log.i(TAG, "Loaded MediaPipe PoseLandmarker from assets/$assetPath (gpu=$useGpu)")
            return MediaPipePoseLandmarker(landmarker)
        }
    }
}
