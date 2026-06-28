package com.toddleai.app.analysis

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end, on-device reproduction of the "import a video -> loader stuck" report.
 *
 * Runs the exact [ImportedVideoAnalyzer] pipeline against the bundled `toddler.mp4`, on the real
 * device, with real MediaPipe inference. Logs progress + timing (tag `ToddleAIDeviceTest`) so we can
 * see whether it hangs, how long each phase takes, and the final gait result.
 */
@RunWith(AndroidJUnit4::class)
class ImportedVideoAnalyzerDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun analyzesBundledToddlerClip() = runBlocking {
        val videoFile = copyAssetToCache("toddler.mp4")
        Log.i(TAG, "video copied to ${videoFile.absolutePath} (${videoFile.length()} bytes)")

        val poseEstimator = PoseEstimator(context)
        val loadStart = SystemClock.elapsedRealtime()
        poseEstimator.load("pose_landmarker_full.task", useGpu = false)
        Log.i(TAG, "model load took ${SystemClock.elapsedRealtime() - loadStart} ms; " +
            "producesGaitLandmarks=${poseEstimator.producesGaitLandmarks}")

        val analyzer = ImportedVideoAnalyzer(
            context = context,
            poseEstimator = poseEstimator,
            qualityGate = QualityGate(),
            replayAnalyzer = ReplayAnalyzer(
                poseEstimator = poseEstimator,
                qualityGate = QualityGate(),
                gaitDetector = GaitEventDetector(),
                metricComputer = MetricComputer(),
                observationEngine = ObservationEngine(),
            ),
        )

        val runStart = SystemClock.elapsedRealtime()
        var lastProgress = -1f
        var lastTick = runStart
        val result = analyzer.analyze(
            videoUri = Uri.fromFile(videoFile),
            childAgeMonths = 24,
        ) { progress ->
            // Log every progress step + wall-clock gap so a stall shows up as a long gap.
            if (progress - lastProgress >= 0.02f || progress >= 1f) {
                val now = SystemClock.elapsedRealtime()
                Log.i(TAG, "progress=${"%.2f".format(progress)} (+${now - lastTick} ms)")
                lastProgress = progress
                lastTick = now
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - runStart

        Log.i(TAG, "ANALYSIS DONE in $elapsed ms")
        Log.i(TAG, "  confidence      = ${result.assessment.confidence}")
        Log.i(TAG, "  usableSteps     = ${result.assessment.usableStepCount}")
        Log.i(TAG, "  processedFrames = ${result.processedFrameCount}")
        Log.i(TAG, "  events          = ${result.events.size}")
        Log.i(TAG, "  issues          = ${result.assessment.issues}")
        result.observations.forEach { Log.i(TAG, "  [${it.status}] ${it.measurement} — ${it.context}") }

        poseEstimator.release()

        // The point of the test is that it COMPLETES (does not hang) and exercises the pose model.
        assertTrue("analysis should finish in well under 2 min", elapsed < 120_000)
    }

    private fun copyAssetToCache(assetName: String): File {
        // The clip lives in the TEST apk's assets (instrumentation context), but everything else uses
        // the app-under-test context (whose assets contain pose_landmarker_full.task).
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val dest = File(context.cacheDir, assetName)
        testAssets.open(assetName).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest
    }

    private companion object {
        const val TAG = "ToddleAIDeviceTest"
    }
}
