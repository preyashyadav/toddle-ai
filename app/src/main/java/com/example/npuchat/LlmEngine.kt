package com.example.npuchat

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.util.concurrent.Executors

/** Streaming events surfaced from a single [LlmEngine.generate] call. */
sealed interface GenEvent {
    data class Token(val text: String) : GenEvent
    /** JSON stats emitted by ExecuTorch when generation finishes. */
    data class Stats(val json: String) : GenEvent
    data class Error(val code: Int, val message: String) : GenEvent
}

/**
 * Thin wrapper around ExecuTorch's [LlmModule]. The native module is single-threaded and not
 * re-entrant, so every load/generate call is pinned to one dedicated thread ([engineDispatcher]).
 * [stop] is the exception — it flips a native flag and is safe to call from another thread while
 * generation is running.
 */
class LlmEngine {

    private val engineThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llm-engine").apply { isDaemon = true }
    }
    private val engineDispatcher: CoroutineDispatcher = engineThread.asCoroutineDispatcher()

    @Volatile private var module: LlmModule? = null

    val isLoaded: Boolean get() = module != null

    /** Construct and load the model. Blocking + slow (seconds) — call from a background coroutine. */
    suspend fun load(modelPath: String, tokenizerPath: String) = withContext(engineDispatcher) {
        check(module == null) { "Model already loaded" }
        Log.i(TAG, "Loading model=$modelPath tokenizer=$tokenizerPath")
        // The .pte is a *hybrid* QNN llama model (prefill_forward / kv_forward). Use the QNN llama
        // runner (model type 4), not the generic text runner which expects a single "forward".
        val m = LlmModule(MODEL_TYPE_QNN_LLAMA, modelPath, tokenizerPath, ModelConfig.TEMPERATURE)
        m.load()
        module = m
        Log.i(TAG, "Model loaded")
    }

    /**
     * Stream a generation. Emits [GenEvent.Token]s as they are produced, optionally a final
     * [GenEvent.Stats], or a [GenEvent.Error]. The flow completes when native generate() returns.
     */
    fun generate(prompt: String): Flow<GenEvent> = channelFlow {
        withContext(engineDispatcher) {
            val m = module ?: error("Model not loaded")
            // Fresh KV cache so the model state matches the full prompt we send each turn.
            // Best-effort: the QNN hybrid runner re-prefills each call, so reset may be a no-op.
            try { m.resetContext() } catch (t: Throwable) { Log.w(TAG, "resetContext: ${t.message}") }
            val callback = object : LlmCallback {
                override fun onResult(result: String) {
                    trySendBlocking(GenEvent.Token(result))
                }

                override fun onStats(stats: String) {
                    trySendBlocking(GenEvent.Stats(stats))
                }

                override fun onError(errorCode: Int, message: String) {
                    Log.e(TAG, "generate onError code=$errorCode msg=$message")
                    trySendBlocking(GenEvent.Error(errorCode, message))
                }
            }
            // Blocks on the engine thread until generation completes or stop() is called.
            m.generate(prompt, ModelConfig.SEQ_LEN, callback, /* echo = */ false)
        }
    }

    /** Request the in-flight generation to stop early. Safe to call from the main thread. */
    fun stop() {
        module?.stop()
    }

    fun close() {
        engineThread.execute {
            try {
                module?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "close() failed", t)
            } finally {
                module = null
            }
        }
        engineThread.shutdown()
    }

    companion object {
        private const val TAG = "LlmEngine"
        /** jni_layer_llama.cpp MODEL_TYPE_QNN_LLAMA — selects the hybrid prefill/kv QNN runner. */
        private const val MODEL_TYPE_QNN_LLAMA = 4
    }
}
