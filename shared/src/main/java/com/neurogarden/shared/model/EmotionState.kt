package com.neurogarden.shared.model

enum class EmotionState(
    val displayName: String,
    val terrainName: String
) {
    CALM("平静", "湖面"),
    TENSE("轻度紧张", "山丘"),
    ANXIOUS("焦虑偏高", "山峰"),
    HIGH_AROUSAL("高唤醒压力", "陡峭山峰")
}
