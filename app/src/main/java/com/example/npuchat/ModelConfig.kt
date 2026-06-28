package com.example.npuchat

import java.io.File

/**
 * Where the model files live on-device. They are sideloaded via `adb push` (too large for the APK):
 *
 *   adb shell mkdir -p /data/local/tmp/llm
 *   adb push hybrid_llama_qnn.pte /data/local/tmp/llm/
 *   adb push tokenizer.json       /data/local/tmp/llm/   # or tokenizer.bin
 *
 * Files pushed to /data/local/tmp are world-readable, so a debug app can open them by path
 * (this is exactly what the official ExecuTorch LlamaDemo does).
 */
object ModelConfig {
    const val BASE_DIR = "/data/local/tmp/llm"
    const val MODEL_PATH = "$BASE_DIR/hybrid_llama_qnn.pte"

    private const val TOKENIZER_JSON = "$BASE_DIR/tokenizer.json"
    private const val TOKENIZER_BIN = "$BASE_DIR/tokenizer.bin"

    /** Temperature for sampling. 0f gives greedy/deterministic output. */
    const val TEMPERATURE = 0.8f

    /** Max tokens to generate per turn. */
    const val SEQ_LEN = 512

    fun modelExists(): Boolean = File(MODEL_PATH).exists()

    /** Prefer tokenizer.json (HF format); fall back to tokenizer.bin if conversion was needed. */
    fun tokenizerPath(): String? = when {
        File(TOKENIZER_JSON).exists() -> TOKENIZER_JSON
        File(TOKENIZER_BIN).exists() -> TOKENIZER_BIN
        else -> null
    }

    /** A human-readable list of what's missing, for the "model not found" screen. */
    fun missingFiles(): List<String> = buildList {
        if (!modelExists()) add(MODEL_PATH)
        if (tokenizerPath() == null) add("$TOKENIZER_JSON (or tokenizer.bin)")
    }
}
