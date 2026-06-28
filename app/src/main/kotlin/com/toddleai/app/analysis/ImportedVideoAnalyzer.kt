package com.toddleai.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.toddleai.app.data.models.FrameStatus
import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportedVideoAnalyzer(
    private val context: Context,
    private val poseEstimator: PoseEstimator,
    private val qualityGate: QualityGate,
    private val replayAnalyzer: ReplayAnalyzer,
) {

    private val videoFrameDecoder = VideoFrameDecoder()

    suspend fun analyze(
        videoUri: Uri,
        childAgeMonths: Int,
        onProgress: (Float) -> Unit,
    ): ReplayAnalyzer.AnalysisResult = withContext(Dispatchers.Default) {
        try {
            val frames = mutableListOf<PoseFrame>()
            val frameQualities = mutableListOf<FrameQuality>()
            var previousFrame: PoseFrame? = null

            // Linear MediaCodec decode (NOT MediaMetadataRetriever.getFrameAtTime, which deadlocks on
            // the first seek on some devices, e.g. S25 Ultra / Android 16). Pose runs per sampled frame.
            Log.i(GAIT_TAG, "decode start uri=$videoUri")
            val emitted = videoFrameDecoder.decode(
                context = context,
                uri = videoUri,
                targetFps = TARGET_ANALYSIS_FPS,
                onProgress = { p -> onProgress((p * EXTRACTION_PROGRESS_WEIGHT).coerceIn(0f, 1f)) },
            ) { bitmap, timestampMs, index ->
                processBitmap(
                    bitmap = bitmap,
                    frameIndex = index,
                    timestampMs = timestampMs,
                    previousFrame = previousFrame,
                    frames = frames,
                    frameQualities = frameQualities,
                )?.let { previousFrame = it }
            }

            // Make a run observable: how many sampled frames produced a pose, and the quality
            // breakdown that drives accept/reject. Filter logcat with tag "ToddleAIGait".
            val good = frameQualities.count { it.status == FrameStatus.GOOD }
            val partial = frameQualities.count { it.status == FrameStatus.PARTIAL }
            val rejected = frameQualities.count { it.status == FrameStatus.REJECTED }
            val feetVisible = frameQualities.count { it.bothFeetVisible }
            Log.i(
                GAIT_TAG,
                "import: decodedFrames=$emitted posedFrames=${frames.size} " +
                    "status[good=$good partial=$partial rejected=$rejected] feetVisibleFrames=$feetVisible",
            )

            if (frames.isEmpty() || frameQualities.isEmpty()) {
                val message = if (emitted == 0) {
                    "We couldn't read this video. Try another clip in MP4 or MOV format."
                } else {
                    "No usable pose frames were found in this video. Try a brighter side-view walking clip."
                }
                return@withContext emptyResult(message)
            }

            replayAnalyzer.analyze(
                frames = frames,
                frameQualities = frameQualities,
                childAgeMonths = childAgeMonths,
                fps = TARGET_ANALYSIS_FPS,
                onProgress = { replayProgress ->
                    val combined = EXTRACTION_PROGRESS_WEIGHT + (replayProgress * REPLAY_PROGRESS_WEIGHT)
                    onProgress(combined.coerceIn(0f, 1f))
                },
            )
        } catch (_: SecurityException) {
            emptyResult("ToddleAI couldn't access that video anymore. Please pick it again.")
        } catch (t: Throwable) {
            Log.w(GAIT_TAG, "video import failed", t)
            emptyResult("Video import failed: ${t.message ?: "unknown error"}")
        } finally {
            onProgress(1f)
        }
    }

    private fun processBitmap(
        bitmap: Bitmap,
        frameIndex: Int,
        timestampMs: Long,
        previousFrame: PoseFrame?,
        frames: MutableList<PoseFrame>,
        frameQualities: MutableList<FrameQuality>,
    ): PoseFrame? {
        return try {
            val poseFrame = poseEstimator.estimate(
                bitmap = bitmap,
                frameIndexOverride = frameIndex,
                timestampOverride = timestampMs,
            ) ?: return null

            val frameQuality = qualityGate.assessFrame(
                current = poseFrame,
                previous = previousFrame,
                fps = TARGET_ANALYSIS_FPS,
            )

            frames += poseFrame
            frameQualities += frameQuality
            poseFrame
        } finally {
            bitmap.recycle()
        }
    }

    private fun emptyResult(message: String): ReplayAnalyzer.AnalysisResult {
        return ReplayAnalyzer.AnalysisResult(
            assessment = CaptureAssessment(
                confidence = CaptureConfidence.REJECT,
                issues = listOf(message),
                bestSegmentStart = 0,
                bestSegmentEnd = 0,
                usableStepCount = 0,
            ),
            metrics = null,
            observations = listOf(
                Observation(
                    type = "capture_issue",
                    measurement = "0 usable steps",
                    context = "Imported videos still need a steady side-view walking segment with both feet visible.",
                    note = message,
                    confidence = "LOW",
                ),
            ),
            events = emptyList(),
            processedFrameCount = 0,
            analysisTimeMs = 0L,
        )
    }

    private companion object {
        const val GAIT_TAG = "ToddleAIGait"
        const val TARGET_ANALYSIS_FPS = 15f
        const val EXTRACTION_PROGRESS_WEIGHT = 0.6f
        const val REPLAY_PROGRESS_WEIGHT = 0.4f
    }
}
