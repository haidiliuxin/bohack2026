package com.neurogarden.shared.model

data class StressResult(
    val stressScore: Float,
    val confidence: Float,
    val state: EmotionState,
    val terrainName: String,
    val warning: String? = null
)
