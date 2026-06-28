package com.toddleai.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.ImageProxy
import com.toddleai.app.analysis.GuidanceEngine
import com.toddleai.app.analysis.GuidanceState
import com.toddleai.app.analysis.ImportedVideoAnalyzer
import com.toddleai.app.analysis.MetricComputer
import com.toddleai.app.analysis.ObservationEngine
import com.toddleai.app.analysis.PoseEstimator
import com.toddleai.app.analysis.QualityGate
import com.toddleai.app.analysis.ReplayAnalyzer
import com.toddleai.app.capture.FrameProcessor
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.PoseFrame
import com.toddleai.app.settings.InferenceBackend
import com.toddleai.app.settings.InferenceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToddleAISessionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val inferenceSettings = InferenceSettings(application)

    private val poseEstimator = PoseEstimator(application)
    private val qualityGate = QualityGate()
    private val guidanceEngine = GuidanceEngine()
    private val frameProcessor = FrameProcessor(
        poseEstimator = poseEstimator,
        qualityGate = qualityGate,
        guidanceEngine = guidanceEngine,
    )
    private val replayAnalyzer = ReplayAnalyzer(
        poseEstimator = poseEstimator,
        qualityGate = qualityGate,
        gaitDetector = com.toddleai.app.analysis.GaitEventDetector(),
        metricComputer = MetricComputer(),
        observationEngine = ObservationEngine(),
    )
    private val importedVideoAnalyzer = ImportedVideoAnalyzer(
        context = application,
        poseEstimator = poseEstimator,
        qualityGate = qualityGate,
        replayAnalyzer = replayAnalyzer,
    )

    val currentPose: StateFlow<PoseFrame?> = frameProcessor.currentPose
    val currentQuality: StateFlow<FrameQuality?> = frameProcessor.currentQuality
    val guidance: StateFlow<GuidanceState> = frameProcessor.guidance
    val inferenceTimeMs: StateFlow<Float> = frameProcessor.inferenceTimeMs

    private val _childName = MutableStateFlow("")
    val childName: StateFlow<String> = _childName.asStateFlow()

    private val _childAgeMonthsInput = MutableStateFlow("")
    val childAgeMonthsInput: StateFlow<String> = _childAgeMonthsInput.asStateFlow()

    private val _analysisProgress = MutableStateFlow(0f)
    val analysisProgress: StateFlow<Float> = _analysisProgress.asStateFlow()

    private val _analysisResult = MutableStateFlow<ReplayAnalyzer.AnalysisResult?>(null)
    val analysisResult: StateFlow<ReplayAnalyzer.AnalysisResult?> = _analysisResult.asStateFlow()

    private val _assistantQuestion = MutableStateFlow<String?>(null)
    val assistantQuestion: StateFlow<String?> = _assistantQuestion.asStateFlow()

    private val _onboardingVisible = MutableStateFlow(true)
    val onboardingVisible: StateFlow<Boolean> = _onboardingVisible.asStateFlow()

    private val _poseModelStatus = MutableStateFlow<PoseModelStatus>(PoseModelStatus.Loading)
    val poseModelStatus: StateFlow<PoseModelStatus> = _poseModelStatus.asStateFlow()

    private val _activeBackend = MutableStateFlow(InferenceBackend.XNNPACK)
    val activeBackend: StateFlow<InferenceBackend> = _activeBackend.asStateFlow()

    private val _sessionMode = MutableStateFlow(SessionMode.LiveCapture)
    val sessionMode: StateFlow<SessionMode> = _sessionMode.asStateFlow()

    private val _importedVideoUri = MutableStateFlow<String?>(null)
    val importedVideoUri: StateFlow<String?> = _importedVideoUri.asStateFlow()

    private var modelLoadJob: Job? = null

    init {
        observeBackendPreference()
    }

    fun updateChildName(value: String) {
        _childName.value = value
    }

    fun updateChildAgeMonthsInput(value: String) {
        _childAgeMonthsInput.value = value.filter(Char::isDigit).take(3)
    }

    fun dismissOnboarding() {
        _onboardingVisible.value = false
    }

    fun beginCaptureSession() {
        _sessionMode.value = SessionMode.LiveCapture
        _importedVideoUri.value = null
        frameProcessor.clearBuffers()
        _analysisResult.value = null
        _analysisProgress.value = 0f
    }

    fun beginImportedVideoSession(uri: Uri) {
        _sessionMode.value = SessionMode.ImportedVideo
        _importedVideoUri.value = uri.toString()
        frameProcessor.clearBuffers()
        _analysisResult.value = null
        _analysisProgress.value = 0f
    }

    fun recentQualities(limit: Int = 15): List<FrameQuality> {
        return frameProcessor.getQualityHistory().takeLast(limit)
    }

    fun bufferedFrames(): List<PoseFrame> = frameProcessor.getBufferedFrames()

    fun bufferedQualities(): List<FrameQuality> = frameProcessor.getQualityHistory()

    fun processFrame(imageProxy: ImageProxy) {
        frameProcessor.processFrame(imageProxy)
    }

    fun analyzeCapture(
        onComplete: (ReplayAnalyzer.AnalysisResult) -> Unit,
    ) {
        val ageMonths = childAgeMonths()
        if (ageMonths == null) {
            val fallback = rejectResult("Enter your child's age in months before recording.")
            _analysisResult.value = fallback
            _analysisProgress.value = 1f
            onComplete(fallback)
            return
        }

        viewModelScope.launch {
            if (_sessionMode.value == SessionMode.ImportedVideo) {
                val importedVideo = _importedVideoUri.value?.let(Uri::parse)
                if (importedVideo == null) {
                    val fallback = rejectResult("No imported video was selected. Please pick a clip and try again.")
                    _analysisResult.value = fallback
                    _analysisProgress.value = 1f
                    onComplete(fallback)
                    return@launch
                }

                val result = importedVideoAnalyzer.analyze(
                    videoUri = importedVideo,
                    childAgeMonths = ageMonths,
                    onProgress = { progress -> _analysisProgress.value = progress },
                )
                _analysisResult.value = result
                onComplete(result)
                return@launch
            }

            val frames = bufferedFrames()
            val qualities = bufferedQualities()
            if (frames.isEmpty() || qualities.isEmpty()) {
                val fallback = rejectResult("No walking frames were captured. Try recording again with the child walking left to right.")
                _analysisResult.value = fallback
                _analysisProgress.value = 1f
                onComplete(fallback)
                return@launch
            }

            val fps = estimateFps(frames)
            val result = replayAnalyzer.analyze(
                frames = frames,
                frameQualities = qualities,
                childAgeMonths = ageMonths,
                fps = fps,
                onProgress = { progress -> _analysisProgress.value = progress },
            )
            _analysisResult.value = result
            onComplete(result)
        }
    }

    fun childAgeMonths(): Int? = _childAgeMonthsInput.value.toIntOrNull()

    fun setAssistantQuestion(question: String) {
        _assistantQuestion.value = question
    }

    fun clearAssistantQuestion() {
        _assistantQuestion.value = null
    }

    fun analysisSourceLabel(): String = when (_sessionMode.value) {
        SessionMode.LiveCapture -> "live camera capture"
        SessionMode.ImportedVideo -> "imported test video"
    }

    fun poseModelLabel(): String = when (val status = _poseModelStatus.value) {
        is PoseModelStatus.Ready -> status.assetPath.substringAfterLast('/')
        is PoseModelStatus.Error -> status.message
        PoseModelStatus.Loading -> "Loading pose model…"
    }

    fun runtimeBackendLabel(): String {
        // Reflect the engine that actually produced the landmarks. Gait analysis runs on the
        // MediaPipe on-device PoseLandmarker; the ExecuTorch/QNN path is the LLM chat backbone.
        if (poseEstimator.producesGaitLandmarks) {
            return when (_activeBackend.value) {
                InferenceBackend.XNNPACK -> "MediaPipe Pose (CPU/XNNPACK, on-device)"
                InferenceBackend.QNN -> "MediaPipe Pose (GPU, on-device)"
            }
        }
        return when (_activeBackend.value) {
            InferenceBackend.XNNPACK -> "ExecuTorch XNNPACK"
            InferenceBackend.QNN -> "QNN"
        }
    }

    private fun observeBackendPreference() {
        viewModelScope.launch {
            inferenceSettings.backendPreference.collectLatest { backend ->
                _activeBackend.value = backend
                loadPoseModel(backend)
            }
        }
    }

    private fun loadPoseModel(backend: InferenceBackend) {
        modelLoadJob?.cancel()
        modelLoadJob = viewModelScope.launch {
            _poseModelStatus.value = PoseModelStatus.Loading

            val candidate = findPoseModelAsset(backend)
            if (candidate == null) {
                _poseModelStatus.value = PoseModelStatus.Error(
                    "No ${backend.displayName()} pose model asset found in app/src/main/assets.",
                )
                return@launch
            }

            try {
                // QNN -> GPU delegate is the closest on-device accelerator MediaPipe can use inside
                // the app sandbox (the Hexagon NPU is reserved for the ExecuTorch LLM path). Loading
                // does native init + asset I/O, so keep it off the main thread.
                val useGpu = backend == InferenceBackend.QNN
                withContext(Dispatchers.IO) {
                    poseEstimator.load(candidate, useGpu)
                }
                frameProcessor.clearBuffers()
                _poseModelStatus.value = PoseModelStatus.Ready(candidate)
            } catch (t: Throwable) {
                _poseModelStatus.value = PoseModelStatus.Error(
                    "${backend.displayName()} pose model failed: ${t.message ?: "unknown error"}",
                )
            }
        }
    }

    private fun findPoseModelAsset(backend: InferenceBackend): String? {
        val assets = listAssetFiles("")

        // The MediaPipe `.task` PoseLandmarker is the only model that emits the 33-landmark layout
        // (incl. feet) gait analysis needs, so it is always preferred regardless of backend. The
        // ExecuTorch `.pte` landmark stage stops at the hips and cannot produce usable gait results.
        assets.firstOrNull { it.isGaitPoseModelAsset() }?.let { return it }

        val backendHint = when (backend) {
            InferenceBackend.XNNPACK -> listOf("cpu", "xnn")
            InferenceBackend.QNN -> listOf("qnn")
        }

        return assets.firstOrNull { path ->
            path.isPoseModelAsset() && backendHint.any { hint -> path.contains(hint, ignoreCase = true) }
        } ?: assets.firstOrNull { path ->
            path.isPoseModelAsset() && path.contains("pose_landmark", ignoreCase = true)
        }
    }

    private fun String.isGaitPoseModelAsset(): Boolean {
        return endsWith(".task", ignoreCase = true) && contains("pose", ignoreCase = true)
    }

    private fun String.isPoseModelAsset(): Boolean {
        return (endsWith(".pte", ignoreCase = true) ||
            endsWith(".tflite", ignoreCase = true) ||
            endsWith(".task", ignoreCase = true)) &&
            contains("pose", ignoreCase = true)
    }

    private fun listAssetFiles(path: String): List<String> {
        val children = getApplication<Application>().assets.list(path).orEmpty()
        if (children.isEmpty()) {
            return if (path.isBlank()) emptyList() else listOf(path)
        }

        return children.flatMap { child ->
            val nextPath = if (path.isBlank()) child else "$path/$child"
            listAssetFiles(nextPath)
        }
    }

    private fun estimateFps(frames: List<PoseFrame>): Float {
        if (frames.size < 2) return 30f
        val durationMs = (frames.last().timestamp - frames.first().timestamp).coerceAtLeast(1L)
        return (((frames.size - 1) * 1000f) / durationMs.toFloat()).coerceIn(10f, 60f)
    }

    private fun rejectResult(message: String): ReplayAnalyzer.AnalysisResult {
        return ReplayAnalyzer.AnalysisResult(
            assessment = com.toddleai.app.data.models.CaptureAssessment(
                confidence = com.toddleai.app.data.models.CaptureConfidence.REJECT,
                issues = listOf(message),
                bestSegmentStart = 0,
                bestSegmentEnd = 0,
                usableStepCount = 0,
            ),
            metrics = null,
            observations = listOf(
                com.toddleai.app.data.models.Observation(
                    type = "capture_issue",
                    measurement = "0 usable steps",
                    context = "A stable side-view walking segment is required before temporal gait measures can be computed on-device.",
                    note = message,
                    confidence = "LOW",
                ),
            ),
            events = emptyList(),
            processedFrameCount = 0,
            analysisTimeMs = 0L,
        )
    }

    override fun onCleared() {
        poseEstimator.release()
        super.onCleared()
    }
}

private fun InferenceBackend.displayName(): String = when (this) {
    InferenceBackend.XNNPACK -> "XNNPACK"
    InferenceBackend.QNN -> "QNN"
}

sealed interface PoseModelStatus {
    data object Loading : PoseModelStatus
    data class Ready(val assetPath: String) : PoseModelStatus
    data class Error(val message: String) : PoseModelStatus
}

enum class SessionMode {
    LiveCapture,
    ImportedVideo,
}
