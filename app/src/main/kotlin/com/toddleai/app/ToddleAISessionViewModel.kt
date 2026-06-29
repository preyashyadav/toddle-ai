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
import kotlin.math.roundToInt

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
    val framingGuidance: StateFlow<com.toddleai.app.analysis.FramingGuidance> = frameProcessor.framingGuidance
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

    // --- On-device LLM assistant ---
    private val llmEngine = com.toddleai.app.llm.LlmEngine()

    private val _chatMessages = MutableStateFlow<List<com.toddleai.app.llm.ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<com.toddleai.app.llm.ChatMessage>> = _chatMessages.asStateFlow()

    private val _llmStatus = MutableStateFlow(com.toddleai.app.llm.LlmStatus.IDLE)
    val llmStatus: StateFlow<com.toddleai.app.llm.LlmStatus> = _llmStatus.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var chatPrimed = false

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
    private var loadedPoseAsset: String? = null

    init {
        observeBackendPreference()
    }

    fun updateChildName(value: String) {
        _childName.value = value
    }

    fun updateChildAgeMonthsInput(value: String) {
        // Stored as the parent-facing age in YEARS (digits + one decimal point).
        val cleaned = value.filter { it.isDigit() || it == '.' }
        val singleDot = cleaned.indexOf('.').let { firstDot ->
            if (firstDot < 0) cleaned else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
        }
        _childAgeMonthsInput.value = singleDot.take(4)
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

    fun childAgeMonths(): Int? =
        _childAgeMonthsInput.value.toFloatOrNull()?.let { (it * 12f).roundToInt() }

    fun setAssistantQuestion(question: String) {
        _assistantQuestion.value = question
    }

    fun clearAssistantQuestion() {
        _assistantQuestion.value = null
    }

    /** Called when the chat screen opens: load the on-device model (once) and seed a greeting. */
    fun primeChat() {
        if (chatPrimed) return
        chatPrimed = true

        if (_chatMessages.value.isEmpty()) {
            _chatMessages.value = listOf(
                com.toddleai.app.llm.ChatMessage(
                    role = com.toddleai.app.llm.ChatRole.ASSISTANT,
                    text = "Hi! Ask me anything about your child's walking results — what a number means, whether it's typical, or how to get a clearer clip.",
                ),
            )
        }

        when {
            llmEngine.isLoaded -> _llmStatus.value = com.toddleai.app.llm.LlmStatus.READY
            !llmEngine.modelAvailable(
                com.toddleai.app.llm.LlmEngine.MODEL_PATH,
                com.toddleai.app.llm.LlmEngine.TOKENIZER_PATH,
            ) -> _llmStatus.value = com.toddleai.app.llm.LlmStatus.MISSING
            else -> {
                _llmStatus.value = com.toddleai.app.llm.LlmStatus.LOADING
                llmEngine.load(
                    com.toddleai.app.llm.LlmEngine.MODEL_PATH,
                    com.toddleai.app.llm.LlmEngine.TOKENIZER_PATH,
                ) { result ->
                    _llmStatus.value = if (result.isSuccess) {
                        com.toddleai.app.llm.LlmStatus.READY
                    } else {
                        com.toddleai.app.llm.LlmStatus.ERROR
                    }
                }
            }
        }
    }

    fun sendChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return
        if (_llmStatus.value != com.toddleai.app.llm.LlmStatus.READY) return

        val history = _chatMessages.value +
            com.toddleai.app.llm.ChatMessage(com.toddleai.app.llm.ChatRole.USER, trimmed)
        // Add the user message plus an empty assistant bubble we stream tokens into.
        _chatMessages.value = history + com.toddleai.app.llm.ChatMessage(
            com.toddleai.app.llm.ChatRole.ASSISTANT, "",
        )
        _isGenerating.value = true

        val gaitContext = com.toddleai.app.llm.PromptBuilder.gaitContext(
            observations = _analysisResult.value?.observations.orEmpty(),
            childAgeMonths = childAgeMonths() ?: 0,
        )
        // Only feed real turns (skip the seeded greeting + the empty streaming bubble) to the model.
        val modelHistory = history.drop(1)
        val prompt = com.toddleai.app.llm.PromptBuilder.buildPrompt(modelHistory, gaitContext)

        val builder = StringBuilder()
        llmEngine.generate(
            prompt = prompt,
            seqLen = CHAT_SEQ_LEN,
            onToken = { token ->
                builder.append(token)
                // Show cleaned partial text; the runtime stops on its own at the EOS token, so we do
                // NOT call stop() here (calling stop() left the module unusable for the next turn).
                updateLastAssistant(cleanAssistantText(builder.toString()))
            },
            onComplete = {
                updateLastAssistant(cleanAssistantText(builder.toString()).ifBlank { "…" })
                _isGenerating.value = false
            },
            onError = { message ->
                updateLastAssistant(cleanAssistantText(builder.toString()).ifBlank { "Sorry, I couldn't answer that. $message" })
                _isGenerating.value = false
            },
        )
    }

    private fun updateLastAssistant(text: String) {
        val current = _chatMessages.value
        if (current.isEmpty()) return
        _chatMessages.value = current.dropLast(1) +
            com.toddleai.app.llm.ChatMessage(com.toddleai.app.llm.ChatRole.ASSISTANT, text)
    }

    private fun cleanAssistantText(raw: String): String {
        var text = raw
        for (marker in com.toddleai.app.llm.PromptBuilder.STOP_MARKERS) {
            val idx = text.indexOf(marker)
            if (idx >= 0) text = text.substring(0, idx)
        }
        return text.trim()
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
            return "MediaPipe Pose (CPU/XNNPACK, on-device)"
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
        val candidate = findPoseModelAsset(backend)
        if (candidate == null) {
            _poseModelStatus.value = PoseModelStatus.Error(
                "No pose model asset found in app/src/main/assets.",
            )
            return
        }

        // The gait pose model is the same `.task` regardless of inference backend, so don't reload
        // (and never re-create MediaPipe concurrently) when only the backend preference changes —
        // a redundant second load races the native runtime and crashed it (SIGBUS). Backend selection
        // applies to the ExecuTorch LLM, not pose.
        if (candidate == loadedPoseAsset) return
        loadedPoseAsset = candidate

        modelLoadJob?.cancel()
        modelLoadJob = viewModelScope.launch {
            _poseModelStatus.value = PoseModelStatus.Loading
            try {
                // MediaPipe pose always runs on the stable CPU/XNNPACK delegate. The GPU delegate
                // crashes natively on this device class, and the Hexagon NPU is reserved for the LLM.
                // Loading does native init + asset I/O, so keep it off the main thread.
                withContext(Dispatchers.IO) {
                    poseEstimator.load(candidate, useGpu = false)
                }
                frameProcessor.clearBuffers()
                _poseModelStatus.value = PoseModelStatus.Ready(candidate)
            } catch (t: Throwable) {
                loadedPoseAsset = null
                _poseModelStatus.value = PoseModelStatus.Error(
                    "Pose model failed: ${t.message ?: "unknown error"}",
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
        llmEngine.release()
        super.onCleared()
    }

    private companion object {
        const val CHAT_SEQ_LEN = 768
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
