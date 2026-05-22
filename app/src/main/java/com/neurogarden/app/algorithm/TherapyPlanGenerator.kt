package com.neurogarden.app.algorithm

import com.neurogarden.shared.model.EmotionState
import com.neurogarden.shared.model.TherapyMode
import com.neurogarden.shared.model.TherapyPlan

object TherapyPlanGenerator {
    fun generatePlan(state: EmotionState): TherapyPlan = when (state) {
        EmotionState.CALM -> TherapyPlan(
            TherapyMode.CALM, "湖面", "light_wind", 4, 4, 1.0f,
            "保持这个节奏，让身体继续放松。", "soft"
        )
        EmotionState.TENSE -> TherapyPlan(
            TherapyMode.TENSE, "湖边", "ambient_piano", 4, 4, 0.8f,
            "你已经注意到自己的状态了，我们慢慢把节奏放下来。", "stable"
        )
        EmotionState.ANXIOUS -> TherapyPlan(
            TherapyMode.ANXIOUS, "雨林", "soft_rain", 4, 6, 0.6f,
            "先不用解决问题，我们只做一次慢呼吸。", "slow_breath"
        )
        EmotionState.HIGH_AROUSAL -> TherapyPlan(
            TherapyMode.HIGH_AROUSAL, "深林雨夜", "deep_rain", 4, 7, 0.5f,
            "我在这里。现在只需要跟着光圈，慢慢呼气。", "very_slow_breath"
        )
    }
}
