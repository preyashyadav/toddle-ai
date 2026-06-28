package com.example.npuchat

enum class Role { USER, ASSISTANT }

/** One chat bubble. [content] is mutated in place (via copy) as tokens stream in. */
data class ChatMessage(
    val role: Role,
    val content: String,
    val id: Long,
)
