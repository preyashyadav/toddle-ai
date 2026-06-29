package com.toddleai.app.llm

import android.util.Log
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File
import java.util.concurrent.Executors

/**
 * On-device LLM runtime for the parent assistant. Wraps ExecuTorch's [LlmModule] (Llama-3.2-1B,
 * XNNPACK/CPU — no QNN, so it isn't blocked by the Samsung SELinux DSP restriction).
 *
 * [LlmModule] is single-threaded and stateful, so all native calls are serialized onto one engine
 * thread. Token streaming is delivered through callbacks; the caller marshals them to the UI.
 */
class LlmEngine {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ToddleAI-LLM") }

    @Volatile private var module: LlmModule? = null
    @Volatile var isLoaded: Boolean = false
        private set

    fun modelAvailable(modelPath: String, tokenizerPath: String): Boolean =
        File(modelPath).exists() && File(tokenizerPath).exists()

    /** Loads the model on the engine thread. [onResult] receives success or the failure reason. */
    fun load(
        modelPath: String,
        tokenizerPath: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        executor.execute {
            try {
                if (!File(modelPath).exists() || !File(tokenizerPath).exists()) {
                    onResult(Result.failure(IllegalStateException("model or tokenizer missing")))
                    return@execute
                }
                Log.i(TAG, "loading LLM: $modelPath")
                val loaded = LlmModule(modelPath, tokenizerPath, TEMPERATURE)
                loaded.load()
                module = loaded
                isLoaded = true
                Log.i(TAG, "LLM loaded")
                onResult(Result.success(Unit))
            } catch (t: Throwable) {
                isLoaded = false
                Log.e(TAG, "LLM load failed", t)
                onResult(Result.failure(t))
            }
        }
    }

    /**
     * Streams a completion for [prompt] (already chat-formatted). [onToken] is invoked per decoded
     * piece on the engine thread; [onComplete] when generation ends; [onError] on failure.
     */
    fun generate(
        prompt: String,
        seqLen: Int,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val active = module
        if (active == null || !isLoaded) {
            onError("Assistant model is not loaded.")
            return
        }
        executor.execute {
            try {
                // Each turn re-sends the full conversation, so clear the KV cache from the previous
                // generation first. Without this the 2nd generate() fails ([ExecuTorch Error 0x12]).
                try {
                    active.resetContext()
                } catch (t: Throwable) {
                    Log.w(TAG, "resetContext failed (continuing)", t)
                }
                active.generate(
                    prompt,
                    seqLen,
                    object : LlmCallback {
                        override fun onResult(result: String) = onToken(result)
                        override fun onStats(stats: String) {}
                        override fun onError(code: Int, message: String) = onError("LLM error $code: $message")
                    },
                    false,
                )
                onComplete()
            } catch (t: Throwable) {
                Log.e(TAG, "generation failed", t)
                onError(t.message ?: "Generation failed.")
            }
        }
    }

    /** Interrupts an in-flight generation (safe to call from any thread). */
    fun stop() {
        try {
            module?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop failed", t)
        }
    }

    fun release() {
        executor.execute {
            try {
                module?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "close failed", t)
            }
            module = null
            isLoaded = false
        }
        executor.shutdown()
    }

    companion object {
        private const val TAG = "ToddleAILlmEngine"
        private const val TEMPERATURE = 0.6f

        // Pushed to the device (XNNPACK Llama-3.2-1B SpinQuant + Llama-3 tokenizer).
        const val MODEL_PATH = "/data/local/tmp/llm/llama32_1b_xnnpack_spinquant.pte"
        const val TOKENIZER_PATH = "/data/local/tmp/llm/llama32_tokenizer.json"
    }
}
