package com.toddleai.app.llm

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

/**
 * De-risk: can the installed app process actually load + run the on-device LLM `.pte`?
 * (Samsung SELinux blocks the app from the Hexagon DSP for QNN, so this proves whether an in-app
 * LLM chat is feasible at all.) Logs under tag "ToddleAILlmSmoke".
 */
@RunWith(AndroidJUnit4::class)
class LlmSmokeTest {

    @Test
    fun loadAndGenerate() {
        val modelPath = LlmEngine.MODEL_PATH
        val tokenizerPath = LlmEngine.TOKENIZER_PATH
        Log.i(TAG, "model exists=${File(modelPath).exists()} (${File(modelPath).length()} B), " +
            "tokenizer exists=${File(tokenizerPath).exists()}")

        val module = LlmModule(modelPath, tokenizerPath, 0.8f)

        val t0 = SystemClock.elapsedRealtime()
        Log.i(TAG, "calling load() ...")
        module.load()
        Log.i(TAG, "load() returned in ${SystemClock.elapsedRealtime() - t0} ms")

        val out = StringBuilder()
        val genStart = SystemClock.elapsedRealtime()
        module.generate(
            "Q: Say hello in one short sentence.\nA:",
            256,
            object : LlmCallback {
                override fun onResult(result: String) {
                    out.append(result)
                    Log.i(TAG, "token=${result.replace("\n", "\\n")}")
                }
                override fun onStats(stats: String) { Log.i(TAG, "stats=$stats") }
                override fun onError(code: Int, message: String) { Log.e(TAG, "ERROR code=$code msg=$message") }
            },
            false,
        )
        Log.i(TAG, "GENERATED in ${SystemClock.elapsedRealtime() - genStart} ms: ${out.toString().trim()}")

        // Now exercise the REAL chat pipeline: grounded Llama-3 template + stop-token cleanup.
        val observations = listOf(
            com.toddleai.app.data.models.Observation(
                type = "cadence", measurement = "Cadence: 142 steps/min",
                context = "Typical for a 2-year-old: 120-160 steps/min.", note = "", confidence = "",
            ),
            com.toddleai.app.data.models.Observation(
                type = "symmetry", measurement = "Left-right symmetry: 6% difference",
                context = "Even left/right step timing is typically within 10%.", note = "", confidence = "",
            ),
        )
        val ctx = PromptBuilder.gaitContext(observations, childAgeMonths = 24)
        val history = listOf(ChatMessage(ChatRole.USER, "Is my child's walking normal?"))
        val prompt = PromptBuilder.buildPrompt(history, ctx)

        val chat = StringBuilder()
        module.generate(prompt, 512, object : LlmCallback {
            override fun onResult(result: String) {
                chat.append(result)
                if (PromptBuilder.STOP_MARKERS.any { chat.contains(it) }) module.stop()
            }
            override fun onStats(stats: String) {}
            override fun onError(code: Int, message: String) { Log.e(TAG, "chat err $code $message") }
        }, false)
        var answer = chat.toString()
        for (m in PromptBuilder.STOP_MARKERS) answer = answer.substringBefore(m)
        Log.i(TAG, "CHAT turn1 A='${answer.trim()}'")

        // TURN 2 — the case that was failing. Reset context, re-send full history + new question.
        module.resetContext()
        val history2 = history + ChatMessage(ChatRole.ASSISTANT, answer.trim()) +
            ChatMessage(ChatRole.USER, "What does the symmetry number mean?")
        val prompt2 = PromptBuilder.buildPrompt(history2, ctx)
        val chat2 = StringBuilder()
        module.generate(prompt2, 768, object : LlmCallback {
            override fun onResult(result: String) { chat2.append(result) }
            override fun onStats(stats: String) {}
            override fun onError(code: Int, message: String) { Log.e(TAG, "turn2 err $code $message") }
        }, false)
        var answer2 = chat2.toString()
        for (m in PromptBuilder.STOP_MARKERS) answer2 = answer2.substringBefore(m)
        Log.i(TAG, "CHAT turn2 A='${answer2.trim()}'")

        module.close()
    }

    private companion object {
        const val TAG = "ToddleAILlmSmoke"
    }
}
