package com.neurogarden.wear.data

import org.json.JSONObject

data class BreathPatternCommand(
    val inhaleSeconds: Int,
    val exhaleSeconds: Int,
    val pattern: String
)

object PhoneCommandReceiver {
    fun parseBreathPattern(payload: ByteArray): BreathPatternCommand? =
        runCatching {
            val json = JSONObject(payload.decodeToString())
            if (json.optString("type") != "breath_pattern") return@runCatching null
            BreathPatternCommand(
                inhaleSeconds = json.optInt("inhaleSeconds", 4).coerceIn(2, 10),
                exhaleSeconds = json.optInt("exhaleSeconds", 6).coerceIn(2, 12),
                pattern = json.optString("pattern", "slow_breath")
            )
        }.getOrNull()
}
