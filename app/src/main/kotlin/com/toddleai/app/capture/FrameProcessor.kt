package com.toddleai.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.toddleai.app.analysis.FramingGuidance
import com.toddleai.app.analysis.FramingGuide
import com.toddleai.app.analysis.FramingHint
import com.toddleai.app.analysis.GaitEventDetector
import com.toddleai.app.analysis.GuidanceEngine
import com.toddleai.app.analysis.GuidanceState
import com.toddleai.app.analysis.PoseEstimator
import com.toddleai.app.analysis.QualityGate
import com.toddleai.app.analysis.QualityLevel
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.max

class FrameProcessor(
    private val poseEstimator: PoseEstimator,
    private val qualityGate: QualityGate,
    private val guidanceEngine: GuidanceEngine,
) {
    private val gaitEventDetector = GaitEventDetector()
    private val framingGuide = FramingGuide()
    private val recentQualities = ArrayDeque<FrameQuality>()
    private var previousPoseFrame: PoseFrame? = null

    private val _currentPose = MutableStateFlow<PoseFrame?>(null)
    val currentPose: StateFlow<PoseFrame?> = _currentPose.asStateFlow()

    private val _framingGuidance = MutableStateFlow(SEARCHING_FRAMING)
    val framingGuidance: StateFlow<FramingGuidance> = _framingGuidance.asStateFlow()

    private val _currentQuality = MutableStateFlow<FrameQuality?>(null)
    val currentQuality: StateFlow<FrameQuality?> = _currentQuality.asStateFlow()

    private val _guidance = MutableStateFlow(
        GuidanceState(
            message = "Start walking when you're ready",
            stepCount = 0,
            stepsNeeded = 5,
            isRecordingAcceptable = false,
            overallQuality = QualityLevel.NEEDS_ADJUSTMENT,
        ),
    )
    val guidance: StateFlow<GuidanceState> = _guidance.asStateFlow()

    private val _inferenceTimeMs = MutableStateFlow(0f)
    val inferenceTimeMs: StateFlow<Float> = _inferenceTimeMs.asStateFlow()

    private val frameBuffer = mutableListOf<PoseFrame>()
    private val qualityBuffer = mutableListOf<FrameQuality>()

    fun processFrame(imageProxy: ImageProxy) {
        var bitmap: Bitmap? = null
        try {
            bitmap = imageProxy.toBitmapWithRotation() ?: return
            val poseFrame = poseEstimator.estimate(bitmap)
            _inferenceTimeMs.value = poseEstimator.getLastInferenceTimeMs()

            if (poseFrame == null) {
                _framingGuidance.value = SEARCHING_FRAMING
                publishGuidance()
                return
            }

            _framingGuidance.value = framingGuide.evaluate(poseFrame)

            val fps = estimateFps(poseFrame)
            val frameQuality = qualityGate.assessFrame(
                current = poseFrame,
                previous = previousPoseFrame,
                fps = fps,
            )

            synchronized(frameBuffer) {
                frameBuffer += poseFrame
                qualityBuffer += frameQuality
            }
            previousPoseFrame = poseFrame
            pushRecentQuality(frameQuality)

            _currentPose.value = poseFrame
            _currentQuality.value = frameQuality
            _inferenceTimeMs.value = poseEstimator.getLastInferenceTimeMs()
            publishGuidance()
        } finally {
            // Pose landmarks are copied out, so the per-frame bitmap can be freed immediately instead
            // of churning the heap during a long capture (estimate() runs synchronously).
            bitmap?.recycle()
            imageProxy.close()
        }
    }

    fun getBufferedFrames(): List<PoseFrame> {
        return synchronized(frameBuffer) { frameBuffer.toList() }
    }

    fun getQualityHistory(): List<FrameQuality> {
        return synchronized(frameBuffer) { qualityBuffer.toList() }
    }

    fun clearBuffers() {
        synchronized(frameBuffer) {
            frameBuffer.clear()
            qualityBuffer.clear()
        }
        recentQualities.clear()
        previousPoseFrame = null
        _currentPose.value = null
        _currentQuality.value = null
        _framingGuidance.value = SEARCHING_FRAMING
        _guidance.value = GuidanceState(
            message = "Start walking when you're ready",
            stepCount = 0,
            stepsNeeded = 5,
            isRecordingAcceptable = false,
            overallQuality = QualityLevel.NEEDS_ADJUSTMENT,
        )
        _inferenceTimeMs.value = 0f
    }

    private fun publishGuidance() {
        val frames = getBufferedFrames()
        val recordingDurationSeconds = when {
            frames.size < 2 -> 0f
            else -> ((frames.last().timestamp - frames.first().timestamp).coerceAtLeast(0L)) / 1000f
        }
        val fps = estimateFps(frames.lastOrNull())
        val detectedStepCount = gaitEventDetector.detectEvents(frames, fps).size

        _guidance.value = guidanceEngine.getGuidanceMessage(
            recentQualities = recentQualities.toList(),
            totalGoodSteps = detectedStepCount,
            recordingDuration = recordingDurationSeconds,
        )
    }

    private fun pushRecentQuality(frameQuality: FrameQuality) {
        recentQualities += frameQuality
        while (recentQualities.size > RECENT_QUALITY_WINDOW) {
            recentQualities.removeFirst()
        }
    }

    private fun estimateFps(latestFrame: PoseFrame?): Float {
        if (latestFrame == null) return DEFAULT_FPS

        val frames = synchronized(frameBuffer) { frameBuffer.takeLast(FPS_SAMPLE_WINDOW) }
        if (frames.size < 2) return DEFAULT_FPS

        val durationMs = (frames.last().timestamp - frames.first().timestamp).coerceAtLeast(1L)
        val intervals = max(1, frames.size - 1)
        return ((intervals * 1000f) / durationMs.toFloat()).coerceIn(MIN_FPS, MAX_FPS)
    }

    private fun ImageProxy.toBitmapWithRotation(): Bitmap? {
        val bitmap = when {
            format == ImageFormat.YUV_420_888 -> yuv420888ToBitmap()
            format == ImageFormat.JPEG -> jpegToBitmap()
            else -> null
        } ?: return null

        val rotationDegrees = imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun ImageProxy.jpegToBitmap(): Bitmap? {
        val buffer = planes.firstOrNull()?.buffer ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun ImageProxy.yuv420888ToBitmap(): Bitmap? {
        val nv21 = yuv420888ToNv21(this)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val outputStream = ByteArrayOutputStream()
        val compressed = yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, outputStream)
        if (!compressed) return null
        val imageBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val image = imageProxy.image ?: return ByteArray(0)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val nv21 = ByteArray(image.width * image.height * 3 / 2)
        var offset = 0

        copyPlane(
            plane = yPlane,
            width = image.width,
            height = image.height,
            output = nv21,
            offset = offset,
            pixelStrideOverride = 1,
        )
        offset += image.width * image.height

        val chromaWidth = image.width / 2
        val chromaHeight = image.height / 2
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        val vBytes = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
        val uBytes = ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[offset++] = vBytes[vIndex]
                nv21[offset++] = uBytes[uIndex]
            }
        }

        return nv21
    }

    private fun copyPlane(
        plane: android.media.Image.Plane,
        width: Int,
        height: Int,
        output: ByteArray,
        offset: Int,
        pixelStrideOverride: Int,
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

        var outputIndex = offset
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                output[outputIndex++] = bytes[rowStart + (col * pixelStrideOverride)]
            }
        }
    }

    private companion object {
        val SEARCHING_FRAMING = FramingGuidance(FramingHint.SEARCHING, "Point the camera at your child")
        const val DEFAULT_FPS = 30f
        const val MIN_FPS = 10f
        const val MAX_FPS = 60f
        const val FPS_SAMPLE_WINDOW = 10
        const val RECENT_QUALITY_WINDOW = 15
        const val JPEG_QUALITY = 85
    }
}
