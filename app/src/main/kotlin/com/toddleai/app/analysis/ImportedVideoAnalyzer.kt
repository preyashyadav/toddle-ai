package com.toddleai.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

class ImportedVideoAnalyzer(
    private val context: Context,
    private val poseEstimator: PoseEstimator,
    private val qualityGate: QualityGate,
    private val replayAnalyzer: ReplayAnalyzer,
) {

    suspend fun analyze(
        videoUri: Uri,
        childAgeMonths: Int,
        onProgress: (Float) -> Unit,
    ): ReplayAnalyzer.AnalysisResult = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            if (durationMs <= 0L) {
                return@withContext emptyResult("We couldn't read this video. Try another clip in MP4 or MOV format.")
            }

            val sampleIntervalMs = max(1L, (1000f / TARGET_ANALYSIS_FPS).toLong())
            val expectedSamples = max(1, ceil(durationMs.toDouble() / sampleIntervalMs.toDouble()).toInt())

            val frames = mutableListOf<PoseFrame>()
            val frameQualities = mutableListOf<FrameQuality>()
            var previousFrame: PoseFrame? = null
            var frameIndex = 0

            var timestampMs = 0L
            while (timestampMs <= durationMs) {
                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                )

                if (bitmap != null) {
                    processBitmap(
                        bitmap = bitmap,
                        frameIndex = frameIndex,
                        timestampMs = timestampMs,
                        previousFrame = previousFrame,
                        frames = frames,
                        frameQualities = frameQualities,
                    )?.let { previousFrame = it }
                }

                frameIndex++
                timestampMs += sampleIntervalMs
                val extractionProgress = (frameIndex.toFloat() / expectedSamples.toFloat()).coerceIn(0f, 1f)
                onProgress(extractionProgress * EXTRACTION_PROGRESS_WEIGHT)
            }

            if (frames.isEmpty() || frameQualities.isEmpty()) {
                return@withContext emptyResult("No usable pose frames were found in this video. Try a brighter side-view walking clip.")
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
            emptyResult("Video import failed: ${t.message ?: "unknown error"}")
        } finally {
            retriever.release()
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
        const val TARGET_ANALYSIS_FPS = 15f
        const val EXTRACTION_PROGRESS_WEIGHT = 0.6f
        const val REPLAY_PROGRESS_WEIGHT = 0.4f
    }
}
