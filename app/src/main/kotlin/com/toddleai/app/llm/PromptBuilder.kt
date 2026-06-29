package com.toddleai.app.llm

import com.toddleai.app.data.models.Observation

/**
 * Builds Llama-3 chat-formatted prompts for the parent assistant, grounded in the actual gait
 * results so the model explains *this* recording rather than inventing numbers.
 */
object PromptBuilder {

    private const val SYSTEM = "You are ToddleAI, a warm, supportive assistant that helps a parent " +
        "understand their toddler's walking-video analysis. Use ONLY the measurements provided. Keep " +
        "answers short (2-4 sentences), plain, and encouraging. You are not a doctor: never diagnose, " +
        "and for any health worry gently suggest checking with a pediatrician."

    fun gaitContext(observations: List<Observation>, childAgeMonths: Int): String {
        if (observations.isEmpty()) {
            return "No analysis is available yet — the parent hasn't recorded a walking clip."
        }
        val years = childAgeMonths / 12
        val age = if (years >= 1) "$years-year-old" else "$childAgeMonths-month-old"
        val lines = observations.joinToString("\n") { "- ${it.measurement} (${it.context})".trim() }
        return "Child age: $age.\nMeasurements from the walking clip:\n$lines"
    }

    /** Renders the conversation in the Llama-3 template (the runtime adds the BOS token). */
    fun buildPrompt(history: List<ChatMessage>, gaitContext: String): String {
        val builder = StringBuilder()
        builder.append("<|start_header_id|>system<|end_header_id|>\n\n")
            .append(SYSTEM).append("\n\n").append(gaitContext).append("<|eot_id|>")
        for (message in history) {
            val role = if (message.role == ChatRole.USER) "user" else "assistant"
            builder.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(message.text).append("<|eot_id|>")
        }
        builder.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return builder.toString()
    }

    /** Llama-3 turn/stop markers that should never appear in the visible answer. */
    val STOP_MARKERS = listOf("<|eot_id|>", "<|end_of_text|>", "<|start_header_id|>", "<|eom_id|>")
}
