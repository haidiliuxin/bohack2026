package com.neurogarden.app.algorithm

data class NormalizedEmotionLabel(
    val primary: String,
    val family: String,
    val valence: Float,
    val arousal: Float,
    val stress: Float,
    val fatigue: Float,
    val loneliness: Float
)

object EmotionLabelNormalizer {
    private val labelMap = mapOf(
        "平静" to NormalizedEmotionLabel("平静", "低唤醒正向", 0.55f, 0.20f, 0.16f, 0.12f, 0.08f),
        "专注" to NormalizedEmotionLabel("专注", "中唤醒中性偏正", 0.38f, 0.46f, 0.20f, 0.12f, 0.08f),
        "轻松" to NormalizedEmotionLabel("轻松", "低唤醒正向", 0.68f, 0.18f, 0.10f, 0.10f, 0.06f),
        "积极活跃" to NormalizedEmotionLabel("积极活跃", "高唤醒正向", 0.64f, 0.72f, 0.22f, 0.10f, 0.06f),
        "疲惫" to NormalizedEmotionLabel("疲惫", "低能量负向", -0.30f, 0.28f, 0.26f, 0.72f, 0.18f),
        "紧张" to NormalizedEmotionLabel("紧张", "高唤醒负向", -0.42f, 0.72f, 0.76f, 0.24f, 0.12f),
        "烦躁" to NormalizedEmotionLabel("烦躁", "高唤醒负向", -0.50f, 0.78f, 0.70f, 0.34f, 0.10f),
        "低落" to NormalizedEmotionLabel("低落", "低唤醒负向", -0.62f, 0.24f, 0.38f, 0.56f, 0.42f),
        "孤独" to NormalizedEmotionLabel("孤独", "低唤醒负向", -0.54f, 0.22f, 0.30f, 0.32f, 0.76f),
        "空落" to NormalizedEmotionLabel("空落", "低唤醒负向", -0.48f, 0.24f, 0.28f, 0.44f, 0.62f),
        "压力偏高" to NormalizedEmotionLabel("压力偏高", "高唤醒负向", -0.38f, 0.64f, 0.82f, 0.34f, 0.12f),
        "不确定" to NormalizedEmotionLabel("不确定", "证据不足", 0f, 0f, 0f, 0f, 0f)
    )

    fun normalize(raw: String?): NormalizedEmotionLabel {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return labelMap.getValue("不确定")
        val key = when {
            text.contains("专注") || text.contains("投入") -> "专注"
            text.contains("轻松") || text.contains("放松") || text.contains("舒缓") -> "轻松"
            text.contains("积极") || text.contains("活跃") || text.contains("兴奋") -> "积极活跃"
            text.contains("疲") || text.contains("累") || text.contains("困") -> "疲惫"
            text.contains("烦") || text.contains("躁") || text.contains("恼") -> "烦躁"
            text.contains("紧张") || text.contains("慌") || text.contains("绷") -> "紧张"
            text.contains("低落") || text.contains("难过") || text.contains("沮丧") -> "低落"
            text.contains("孤独") || text.contains("陪伴") -> "孤独"
            text.contains("空") || text.contains("空落") -> "空落"
            text.contains("压力") || text.contains("高压") -> "压力偏高"
            text.contains("平静") || text.contains("稳定") || text.contains("正常") -> "平静"
            else -> text.take(12)
        }
        return labelMap[key] ?: NormalizedEmotionLabel(
            primary = key,
            family = "开放标签",
            valence = 0f,
            arousal = 0.35f,
            stress = 0.30f,
            fatigue = 0.25f,
            loneliness = 0.15f
        )
    }

    fun normalizeMany(labels: List<String>): List<String> =
        labels.map { normalize(it).primary }.distinct().take(4)
}
