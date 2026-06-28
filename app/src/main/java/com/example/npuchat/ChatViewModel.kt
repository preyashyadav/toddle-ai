package com.example.npuchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** Lifecycle of the model files + native module. */
sealed interface ModelStatus {
    data object Loading : ModelStatus
    data class MissingFiles(val paths: List<String>) : ModelStatus
    data object Ready : ModelStatus
    data class LoadError(val message: String) : ModelStatus
}

class ChatViewModel : ViewModel() {

    private val engine = LlmEngine()
    private var nextId = 0L

    val messages = mutableStateListOf<ChatMessage>()

    var status by mutableStateOf<ModelStatus>(ModelStatus.Loading)
        private set
    var isGenerating by mutableStateOf(false)
        private set
    /** Tokens/sec of the most recent reply (decode speed), null until first reply. */
    var lastTokPerSec by mutableStateOf<Double?>(null)
        private set

    init {
        checkAndLoad()
    }

    fun checkAndLoad() {
        val missing = ModelConfig.missingFiles()
        if (missing.isNotEmpty()) {
            status = ModelStatus.MissingFiles(missing)
            return
        }
        status = ModelStatus.Loading
        viewModelScope.launch {
            status = try {
                engine.load(ModelConfig.MODEL_PATH, ModelConfig.tokenizerPath()!!)
                ModelStatus.Ready
            } catch (t: Throwable) {
                ModelStatus.LoadError(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || isGenerating || status != ModelStatus.Ready) return

        messages.add(ChatMessage(Role.USER, text, nextId++))
        val prompt = ChatTemplate.build(messages.toList())

        val assistantId = nextId++
        messages.add(ChatMessage(Role.ASSISTANT, "", assistantId))

        isGenerating = true
        lastTokPerSec = null
        val startNs = System.nanoTime()
        var tokens = 0

        viewModelScope.launch {
            try {
                engine.generate(prompt).collect { event ->
                    when (event) {
                        is GenEvent.Token -> {
                            tokens++
                            appendTo(assistantId, event.text)
                        }
                        is GenEvent.Stats -> { /* raw stats logged in LlmEngine */ }
                        is GenEvent.Error -> appendTo(assistantId, "\n[error ${event.code}] ${event.message}")
                    }
                }
            } catch (t: Throwable) {
                appendTo(assistantId, "\n[error] ${t.message}")
            } finally {
                val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                if (tokens > 0 && elapsedSec > 0) lastTokPerSec = tokens / elapsedSec
                isGenerating = false
            }
        }
    }

    fun stop() = engine.stop()

    private fun appendTo(id: Long, chunk: String) {
        val idx = messages.indexOfLast { it.id == id }
        if (idx >= 0) {
            val cur = messages[idx]
            messages[idx] = cur.copy(content = cur.content + chunk)
        }
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
