package com.toddleai.app.llm

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class LlmStatus {
    /** Not asked to load yet. */
    IDLE,

    /** Loading the model into memory. */
    LOADING,

    /** Ready to chat. */
    READY,

    /** The on-device model file isn't present. */
    MISSING,

    /** Load/generation failed. */
    ERROR,
}
