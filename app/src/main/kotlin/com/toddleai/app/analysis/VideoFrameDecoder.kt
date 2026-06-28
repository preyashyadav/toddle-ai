package com.toddleai.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Robust on-device video frame extractor.
 *
 * Replaces `MediaMetadataRetriever.getFrameAtTime(...)`, which **deadlocks on the very first call**
 * on some devices (observed: Samsung S25 Ultra / Android 16 with a plain H.264 clip) because each
 * call issues a hardware-decoder seek. Instead this decodes the video **linearly** with
 * [MediaExtractor] + [MediaCodec] (no seeks) and emits roughly one frame every `1/targetFps` seconds.
 *
 * Output frames are ARGB_8888 [Bitmap]s in presentation order. Decoding runs on the caller's thread
 * (call from a background dispatcher).
 */
class VideoFrameDecoder {

    /** Called for each sampled frame. Receives a fresh bitmap (caller owns/recycles it), the frame
     *  presentation time in ms, and a 0-based sampled-frame index. */
    fun interface FrameCallback {
        fun onFrame(bitmap: Bitmap, timestampMs: Long, index: Int)
    }

    /**
     * Decodes [uri] and invokes [onFrame] for sampled frames. [onProgress] reports 0..1 based on
     * presentation time vs. duration. Returns the number of frames emitted. Throws on hard decode
     * failure (caller should surface a friendly message).
     */
    fun decode(
        context: Context,
        uri: Uri,
        targetFps: Float,
        onProgress: (Float) -> Unit,
        onFrame: FrameCallback,
    ): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex < 0) {
                Log.w(TAG, "no video track found in $uri")
                return 0
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            // ByteBuffer (non-Surface) mode so we can read YUV planes directly — no OpenGL needed.
            codec.configure(format, null, null, 0)
            codec.start()

            val intervalUs = (1_000_000.0 / targetFps).toLong().coerceAtLeast(1L)
            var nextSampleUs = 0L
            var emitted = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        val sampleSize = if (inputBuffer != null) {
                            extractor.readSampleData(inputBuffer, 0)
                        } else {
                            -1
                        }
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val shouldSample = !isConfig && info.size > 0 && info.presentationTimeUs >= nextSampleUs
                        if (shouldSample) {
                            val image = runCatching { codec.getOutputImage(outIndex) }.getOrNull()
                            val bitmap = image?.use { yuvImageToBitmap(it) }
                            if (bitmap != null) {
                                onFrame.onFrame(bitmap, info.presentationTimeUs / 1000L, emitted)
                                emitted++
                                nextSampleUs += intervalUs
                                if (durationUs > 0L) {
                                    onProgress((info.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output yet; keep feeding input / draining.
                    }
                    // INFO_OUTPUT_FORMAT_CHANGED / INFO_OUTPUT_BUFFERS_CHANGED: nothing to do.
                }
            }

            Log.i(TAG, "decoded $emitted sampled frames from $uri (duration=${durationUs / 1000} ms)")
            return emitted
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    /** Converts a YUV_420_888 [Image] from the codec into an ARGB_8888 [Bitmap] via NV21/JPEG. */
    private fun yuvImageToBitmap(image: Image): Bitmap {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), JPEG_QUALITY, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Y plane (respect rowStride).
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            val row = ByteArray(yRowStride)
            for (r in 0 until height) {
                yBuffer.position(r * yRowStride)
                yBuffer.get(row, 0, yRowStride)
                System.arraycopy(row, 0, nv21, pos, width)
                pos += width
            }
        }

        // Interleave V,U -> NV21, respecting row/pixel strides.
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val uRow = ByteArray(uRowStride)
        val vRow = ByteArray(vRowStride)
        for (r in 0 until chromaHeight) {
            uBuffer.position(r * uRowStride)
            uBuffer.get(uRow, 0, minOf(uRowStride, uBuffer.remaining()))
            vBuffer.position(r * vRowStride)
            vBuffer.get(vRow, 0, minOf(vRowStride, vBuffer.remaining()))
            for (c in 0 until chromaWidth) {
                nv21[pos++] = vRow[c * vPixelStride]
                nv21[pos++] = uRow[c * uPixelStride]
            }
        }
        return nv21
    }

    private companion object {
        const val TAG = "ToddleAIVideoDecoder"
        const val TIMEOUT_US = 10_000L
        const val JPEG_QUALITY = 90
    }
}
