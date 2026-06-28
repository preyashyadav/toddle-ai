package com.toddleai.app.data.models

enum class Side {
    LEFT,
    RIGHT,
}

data class GaitEvent(
    val frameIndex: Int,
    val timeSeconds: Float,
    val side: Side,
    val confidence: Float,
)
