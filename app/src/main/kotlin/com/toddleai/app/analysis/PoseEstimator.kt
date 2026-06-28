package com.toddleai.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.PoseFrame
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PoseEstimator(private val context: Context) {

    private var module: Module? = null
    private var tfliteInterpreter: Any? = null
    private var mediaPipe: MediaPipePoseLandmarker? = null
    private var lastInferenceTimeMs: Float = 0f
    private var nextFrameIndex: Int = 0
    private var loadedModelPath: String? = null

    /**
     * True only when the loaded model emits the full 33-landmark BlazePose layout (incl. feet) that
     * gait analysis requires. The ExecuTorch `.pte` landmark stage does NOT (it stops at the hips),
     * so the rest of the app can use this to avoid presenting unusable "results".
     */
    var producesGaitLandmarks: Boolean = false
        private set

    fun load(modelPath: String, useGpu: Boolean = false) {
        release()
        loadedModelPath = modelPath
        val loadStartNs = System.nanoTime()
        try {
            // `.task` -> MediaPipe Tasks PoseLandmarker, the only gait-capable on-device path.
            // It is NOT an ExecuTorch program; routing it through Module.load() segfaults natively,
            // which is exactly what crashed the previous integration. MediaPipe reads straight from
            // the APK asset, so we skip the copy-to-cache used by the ExecuTorch/TFLite paths.
            if (modelPath.endsWith(".task", ignoreCase = true)) {
                mediaPipe = MediaPipePoseLandmarker.fromAsset(context, modelPath, useGpu)
                producesGaitLandmarks = true
                return
            }

            val assetFile = copyAssetToCache(modelPath)
            if (assetFile.extension.equals("tflite", ignoreCase = true)) {
                tfliteInterpreter = loadTfliteInterpreter(assetFile)
                Log.i(TAG, "Loaded TFLite pose fallback from ${assetFile.absolutePath}")
            } else {
                module = Module.load(assetFile.absolutePath)
                Log.i(TAG, "Loaded ExecuTorch pose model from ${assetFile.absolutePath}")
            }
        } catch (executorchError: Throwable) {
            Log.w(TAG, "ExecuTorch load failed for $modelPath, trying TFLite fallback", executorchError)
            val fallbackAssetPath = fallbackTflitePath(modelPath)
            if (fallbackAssetPath != null) {
                try {
                    val fallbackFile = copyAssetToCache(fallbackAssetPath)
                    tfliteInterpreter = loadTfliteInterpreter(fallbackFile)
                    Log.i(TAG, "Loaded TFLite pose fallback from ${fallbackFile.absolutePath}")
                } catch (fallbackError: Throwable) {
                    release()
                    throw IllegalStateException(
                        "Unable to load pose model from assets/$modelPath or fallback assets/$fallbackAssetPath",
                        fallbackError,
                    )
                }
            } else {
                release()
                throw IllegalStateException(
                    "Unable to load pose model from assets/$modelPath and no .tflite fallback was found.",
                    executorchError,
                )
            }
        } finally {
            lastInferenceTimeMs = nanosToMillis(System.nanoTime() - loadStartNs)
        }
    }

    fun estimate(
        bitmap: Bitmap,
        frameIndexOverride: Int? = null,
        timestampOverride: Long? = null,
    ): PoseFrame? {
        val inferenceStartNs = System.nanoTime()

        // MediaPipe handles its own preprocessing (letterbox + internal detector ROI), so feed it the
        // raw bitmap rather than the 256x256 NHWC tensor the ExecuTorch path needs.
        if (mediaPipe != null) {
            return try {
                val landmarks = mediaPipe?.estimate(bitmap)
                if (landmarks == null || landmarks.size != MediaPipePoseLandmarker.FULL_BODY_LANDMARK_COUNT) {
                    null
                } else {
                    PoseFrame(
                        frameIndex = frameIndexOverride ?: nextFrameIndex++,
                        timestamp = timestampOverride ?: SystemClock.elapsedRealtime(),
                        landmarks = landmarks,
                        sourceWidth = bitmap.width,
                        sourceHeight = bitmap.height,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MediaPipe pose estimation failed on current frame", t)
                null
            } finally {
                lastInferenceTimeMs = nanosToMillis(System.nanoTime() - inferenceStartNs)
            }
        }

        val modelInput = preprocess(bitmap)

        return try {
            val rawOutput = when {
                module != null -> runExecuTorch(modelInput)
                tfliteInterpreter != null -> runTflite(modelInput)
                else -> return null
            }

            val landmarks = parseLandmarks(rawOutput, bitmap.width, bitmap.height)
            if (landmarks.size != LANDMARK_COUNT) {
                Log.w(TAG, "Unexpected landmark count: ${landmarks.size}")
                null
            } else {
                PoseFrame(
                    frameIndex = frameIndexOverride ?: nextFrameIndex++,
                    timestamp = timestampOverride ?: SystemClock.elapsedRealtime(),
                    landmarks = landmarks,
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Pose estimation failed on current frame", t)
            null
        } finally {
            lastInferenceTimeMs = nanosToMillis(System.nanoTime() - inferenceStartNs)
        }
    }

    fun getLastInferenceTimeMs(): Float = lastInferenceTimeMs

    fun release() {
        producesGaitLandmarks = false

        try {
            mediaPipe?.close()
        } finally {
            mediaPipe = null
        }

        try {
            module?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close ExecuTorch module cleanly", t)
        } finally {
            module = null
        }

        try {
            val closeMethod = tfliteInterpreter?.javaClass?.getMethod("close")
            closeMethod?.invoke(tfliteInterpreter)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to close TFLite interpreter cleanly", t)
        } finally {
            tfliteInterpreter = null
        }
    }

    private fun runExecuTorch(input: FloatArray): FloatArray {
        val inputTensor = Tensor.fromBlob(input, longArrayOf(1, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong(), 3))
        val outputs = module?.forward(EValue.from(inputTensor)).orEmpty()

        val outputTensor = outputs
            .firstOrNull { it.isTensor }
            ?.toTensor()
            ?: throw IllegalStateException("ExecuTorch forward() returned no tensor output")

        return outputTensor.dataAsFloatArray
    }

    private fun runTflite(input: FloatArray): FloatArray {
        val interpreter = tfliteInterpreter ?: throw IllegalStateException("TFLite interpreter is not loaded")
        val runMethod = interpreter.javaClass.getMethod("run", Any::class.java, Any::class.java)

        val inputBuffer = ByteBuffer
            .allocateDirect(input.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        inputBuffer.asFloatBuffer().put(input)

        val outputBuffer = ByteBuffer
            .allocateDirect(TFLITE_OUTPUT_FLOATS * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())

        runMethod.invoke(interpreter, inputBuffer, outputBuffer)
        outputBuffer.rewind()

        return FloatArray(TFLITE_OUTPUT_FLOATS).also { outputBuffer.asFloatBuffer().get(it) }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        resized.getPixels(
            pixels,
            0,
            MODEL_INPUT_SIZE,
            0,
            0,
            MODEL_INPUT_SIZE,
            MODEL_INPUT_SIZE,
        )

        val input = FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
        var offset = 0
        for (pixel in pixels) {
            val red = ((pixel shr 16) and 0xFF) / 255f
            val green = ((pixel shr 8) and 0xFF) / 255f
            val blue = (pixel and 0xFF) / 255f

            input[offset++] = normalizeChannel(red)
            input[offset++] = normalizeChannel(green)
            input[offset++] = normalizeChannel(blue)
        }
        return input
    }

    private fun parseLandmarks(
        rawOutput: FloatArray,
        originalWidth: Int,
        originalHeight: Int,
    ): List<Landmark> {
        val valuesPerLandmark = inferValuesPerLandmark(rawOutput)
        if (valuesPerLandmark < 3) return emptyList()

        return List(LANDMARK_COUNT) { index ->
            val base = index * valuesPerLandmark
            val rawX = rawOutput.getOrElse(base) { 0f }
            val rawY = rawOutput.getOrElse(base + 1) { 0f }
            val rawVisibility = rawOutput.getOrElse(base + visibilityIndex(valuesPerLandmark)) { 1f }

            Landmark(
                x = scaleCoordinate(rawX, originalWidth.toFloat()),
                y = scaleCoordinate(rawY, originalHeight.toFloat()),
                visibility = rawVisibility.coerceIn(0f, 1f),
            )
        }
    }

    private fun inferValuesPerLandmark(rawOutput: FloatArray): Int {
        val supported = listOf(5, 4, 3)
        return supported.firstOrNull { rawOutput.size >= LANDMARK_COUNT * it } ?: 0
    }

    private fun visibilityIndex(valuesPerLandmark: Int): Int {
        return when {
            valuesPerLandmark >= 4 -> 3
            else -> 2
        }
    }

    private fun scaleCoordinate(raw: Float, originalSize: Float): Float {
        return when {
            raw in 0f..1f -> raw * originalSize
            raw in 0f..MODEL_INPUT_SIZE.toFloat() -> (raw / MODEL_INPUT_SIZE.toFloat()) * originalSize
            else -> raw
        }
    }

    private fun normalizeChannel(value: Float): Float {
        return when (NORMALIZATION_MODE) {
            NormalizationMode.ZERO_TO_ONE -> value
            NormalizationMode.NEGATIVE_ONE_TO_ONE -> (value * 2f) - 1f
        }
    }

    private fun copyAssetToCache(assetPath: String): File {
        val destination = File(context.cacheDir, assetPath.substringAfterLast('/'))
        if (destination.exists() && destination.length() > 0) {
            return destination
        }

        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        return destination
    }

    private fun fallbackTflitePath(modelPath: String): String? {
        if (modelPath.endsWith(".tflite", ignoreCase = true)) {
            return modelPath
        }

        val candidate = modelPath.substringBeforeLast('.') + ".tflite"
        return try {
            context.assets.open(candidate).close()
            candidate
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadTfliteInterpreter(modelFile: File): Any {
        val interpreterClass = Class.forName("org.tensorflow.lite.Interpreter")
        val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")
        val options = optionsClass.getDeclaredConstructor().newInstance()
        return interpreterClass
            .getDeclaredConstructor(File::class.java, optionsClass)
            .newInstance(modelFile, options)
    }

    private fun nanosToMillis(durationNs: Long): Float = durationNs / 1_000_000f

    private enum class NormalizationMode {
        ZERO_TO_ONE,
        NEGATIVE_ONE_TO_ONE,
    }

    private companion object {
        const val TAG = "ToddleAIPoseEstimator"
        const val LANDMARK_COUNT = 33
        const val MODEL_INPUT_SIZE = 256
        const val FLOAT_BYTES = 4
        const val TFLITE_OUTPUT_FLOATS = LANDMARK_COUNT * 5
        val NORMALIZATION_MODE = NormalizationMode.ZERO_TO_ONE
    }
}
