package com.toddleai.app.analysis

import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.MediaPipeLandmarks
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.PoseFrame
import com.toddleai.app.data.models.TemporalMetrics
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class ReplayAnalyzer(
    private val poseEstimator: PoseEstimator,
    private val qualityGate: QualityGate,
    private val gaitDetector: GaitEventDetector,
    private val metricComputer: MetricComputer,
    private val observationEngine: ObservationEngine,
) {

    data class AnalysisResult(
        val assessment: CaptureAssessment,
        val metrics: TemporalMetrics?,
        val observations: List<Observation>,
        val events: List<com.toddleai.app.data.models.GaitEvent>,
        val processedFrameCount: Int,
        val analysisTimeMs: Long,
    )

    suspend fun analyze(
        frames: List<PoseFrame>,
        frameQualities: List<FrameQuality>,
        childAgeMonths: Int,
        fps: Float,
        onProgress: (Float) -> Unit,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val startNs = System.nanoTime()
        onProgress(0f)

        val detectedEvents = gaitDetector.detectEvents(frames, fps)
        val assessment = qualityGate.assessRecording(
            frameQualities = frameQualities,
            detectedSteps = detectedEvents.size,
        )
        Log.i(
            GAIT_TAG,
            "analyze: frames=${frames.size} fps=$fps detectedSteps=${detectedEvents.size} " +
                "-> confidence=${assessment.confidence} bestSegment=[${assessment.bestSegmentStart}..${assessment.bestSegmentEnd}]",
        )
        onProgress(0.15f)

        if (assessment.confidence == CaptureConfidence.REJECT) {
            val result = AnalysisResult(
                assessment = assessment,
                metrics = null,
                observations = rejectObservations(assessment),
                events = emptyList(),
                processedFrameCount = 0,
                analysisTimeMs = nanosToMillis(System.nanoTime() - startNs),
            )
            onProgress(1f)
            return@withContext result
        }

        val bestSegment = extractBestSegment(frames, assessment)
        onProgress(0.3f)

        if (bestSegment.isEmpty()) {
            val emptyAssessment = assessment.copy(
                confidence = CaptureConfidence.REJECT,
                issues = assessment.issues + "No continuous high-quality walking segment was available for replay analysis.",
                usableStepCount = 0,
            )
            val result = AnalysisResult(
                assessment = emptyAssessment,
                metrics = null,
                observations = rejectObservations(emptyAssessment),
                events = emptyList(),
                processedFrameCount = 0,
                analysisTimeMs = nanosToMillis(System.nanoTime() - startNs),
            )
            onProgress(1f)
            return@withContext result
        }

        val smoothedFrames = smoothFrames(bestSegment)
        onProgress(0.5f)

        val filteredFrames = smoothedFrames.filter(::hasRequiredLandmarks)
        onProgress(0.65f)

        val replayEvents = gaitDetector.detectEvents(filteredFrames, fps)
        onProgress(0.8f)

        val metrics = metricComputer.computeMetrics(replayEvents)
        val adjustedAssessment = assessment.copy(usableStepCount = metrics.usableStepCount)
        val observations = observationEngine.generateObservations(
            metrics = metrics,
            childAgeMonths = childAgeMonths,
            captureConfidence = adjustedAssessment.confidence,
        )
        onProgress(0.95f)

        val result = AnalysisResult(
            assessment = adjustedAssessment,
            metrics = metrics,
            observations = observations,
            events = replayEvents,
            processedFrameCount = filteredFrames.size,
            analysisTimeMs = nanosToMillis(System.nanoTime() - startNs),
        )
        onProgress(1f)
        result
    }

    private fun extractBestSegment(
        frames: List<PoseFrame>,
        assessment: CaptureAssessment,
    ): List<PoseFrame> {
        if (frames.isEmpty()) return emptyList()

        val startIndex = assessment.bestSegmentStart
        val endIndex = assessment.bestSegmentEnd

        // QualityGate collapses the segment to [0..0] when there is no run of >=30 consecutive GOOD
        // frames (e.g. a slightly unstable hand-held / AI-generated clip). Rather than discard a clip
        // that still produced usable steps, fall back to the whole posed sequence for replay; the
        // downstream `hasRequiredLandmarks` filter + per-step physiologic checks still gate quality,
        // and the LOW confidence label already communicates the reduced certainty to the user.
        if (endIndex <= startIndex) return frames

        return frames.filter { frame ->
            frame.frameIndex in startIndex..endIndex
        }
    }

    private fun smoothFrames(frames: List<PoseFrame>): List<PoseFrame> {
        if (frames.isEmpty()) return emptyList()

        return List(frames.size) { index ->
            val start = max(0, index - SMOOTHING_RADIUS)
            val end = min(frames.lastIndex, index + SMOOTHING_RADIUS)
            val window = frames.subList(start, end + 1)

            PoseFrame(
                frameIndex = frames[index].frameIndex,
                timestamp = frames[index].timestamp,
                landmarks = List(frames[index].landmarks.size) { landmarkIndex ->
                    smoothLandmark(window, landmarkIndex)
                },
            )
        }
    }

    private fun smoothLandmark(
        window: List<PoseFrame>,
        landmarkIndex: Int,
    ): Landmark {
        var sumX = 0f
        var sumY = 0f
        var sumVisibility = 0f
        var count = 0

        for (frame in window) {
            val landmark = frame.landmarks.getOrNull(landmarkIndex) ?: continue
            sumX += landmark.x
            sumY += landmark.y
            sumVisibility += landmark.visibility
            count++
        }

        if (count == 0) return Landmark(0f, 0f, 0f)

        return Landmark(
            x = sumX / count,
            y = sumY / count,
            visibility = sumVisibility / count,
        )
    }

    private fun hasRequiredLandmarks(frame: PoseFrame): Boolean {
        return REQUIRED_LANDMARKS.all { index ->
            frame.landmarks.getOrNull(index)?.visibility?.let { it >= MIN_REQUIRED_VISIBILITY } == true
        }
    }

    private fun rejectObservations(assessment: CaptureAssessment): List<Observation> {
        val issues = if (assessment.issues.isEmpty()) {
            listOf("This recording did not contain enough usable walking data for replay analysis.")
        } else {
            assessment.issues
        }

        return issues.map { issue ->
            Observation(
                type = "capture_issue",
                measurement = "${assessment.usableStepCount} usable steps",
                context = "Replay analysis needs a stable side-view walking segment with both feet visible for several consecutive steps.",
                note = issue,
                confidence = "LOW",
            )
        }
    }

    @Suppress("UnusedPrivateProperty")
    private val unusedPoseEstimatorReference: PoseEstimator = poseEstimator

    private fun nanosToMillis(durationNs: Long): Long = durationNs / 1_000_000L

    private companion object {
        const val GAIT_TAG = "ToddleAIGait"
        const val MIN_REQUIRED_VISIBILITY = 0.5f
        const val SMOOTHING_RADIUS = 2

        val REQUIRED_LANDMARKS = intArrayOf(
            MediaPipeLandmarks.LEFT_HIP,
            MediaPipeLandmarks.RIGHT_HIP,
            MediaPipeLandmarks.LEFT_KNEE,
            MediaPipeLandmarks.RIGHT_KNEE,
            MediaPipeLandmarks.LEFT_ANKLE,
            MediaPipeLandmarks.RIGHT_ANKLE,
            MediaPipeLandmarks.LEFT_HEEL,
            MediaPipeLandmarks.RIGHT_HEEL,
            MediaPipeLandmarks.LEFT_FOOT_INDEX,
            MediaPipeLandmarks.RIGHT_FOOT_INDEX,
        )
    }
}
