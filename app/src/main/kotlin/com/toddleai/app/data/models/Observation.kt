package com.toddleai.app.data.models

data class Observation(
    val type: String,
    val measurement: String,
    val context: String,
    val note: String,
    val confidence: String,
)
