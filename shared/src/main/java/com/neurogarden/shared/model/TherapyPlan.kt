package com.neurogarden.shared.model

data class TherapyPlan(
    val mode: TherapyMode,
    val sceneName: String,
    val soundName: String,
    val inhaleSeconds: Int,
    val exhaleSeconds: Int,
    val animationSpeed: Float,
    val aiText: String,
    val watchHapticPattern: String
)
