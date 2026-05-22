package com.neurogarden.app.algorithm

import com.neurogarden.shared.model.EmotionState

object EmotionStateMapper {
    fun map(stressScore: Float): EmotionState = when {
        stressScore < 0.35f -> EmotionState.CALM
        stressScore < 0.60f -> EmotionState.TENSE
        stressScore < 0.80f -> EmotionState.ANXIOUS
        else -> EmotionState.HIGH_AROUSAL
    }
}
