package com.example.npuchat

/**
 * Renders the Qwen chat format in Kotlin. The ExecuTorch [org.pytorch.executorch.extension.llm.LlmModule]
 * does NOT apply the model's `chat_template.jinja`, so the app must format prompts itself.
 *
 * Qwen (2.5 / 3) ChatML format:
 *
 *   <|im_start|>system
 *   {system}<|im_end|>
 *   <|im_start|>user
 *   {user}<|im_end|>
 *   <|im_start|>assistant
 *   {assistant}<|im_end|>
 *   ...
 *   <|im_start|>assistant
 *
 * The trailing open `assistant` tag is where generation continues.
 */
object ChatTemplate {

    const val DEFAULT_SYSTEM = "You are a helpful assistant."

    /**
     * Build the full prompt for the whole conversation. We re-send the entire history each turn and
     * call `resetContext()` before generating, so the model's KV cache always matches the prompt.
     *
     * @param messages ordered conversation turns (user/assistant). System prompt is prepended.
     */
    fun build(messages: List<ChatMessage>, system: String = DEFAULT_SYSTEM): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        for (m in messages) {
            val role = if (m.role == Role.USER) "user" else "assistant"
            sb.append("<|im_start|>").append(role).append('\n')
                .append(m.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
