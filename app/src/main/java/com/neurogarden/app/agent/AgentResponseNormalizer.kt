package com.neurogarden.app.agent

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object AgentResponseNormalizer {
    private val allowedStates = setOf(
        "stable",
        "mild_fluctuation",
        "observe",
        "needs_confirmation",
        "focus_attention",
        "unknown"
    )

    fun normalizeSignalResponse(rawText: String, fallbackRiskLevel: String): AgentSignalResponse {
        val json = runCatching { JSONObject(extractJson(rawText)) }.getOrNull() ?: JSONObject()
        val normalizedState = normalizeState(
            json.optString("state").ifBlank { json.optString("emotion").ifBlank { json.optString("riskLevel") } }
        )
        val rawRiskScore = json.optDouble("riskScore", fallbackScoreFor(normalizedState).toDouble()).toFloat()
        val riskScore100 = (if (rawRiskScore in 0f..1f) rawRiskScore * 100f else rawRiskScore)
            .coerceIn(0f, 100f)
        val confidence = json.optDouble("confidence", 0.55).toFloat().coerceIn(0f, 1f)
        val reasons = json.optJSONArray("mainReasons").toStringList()
            .ifEmpty { listOf("结构化特征触发本地安全兜底") }
            .map { sanitizeShortText(it).take(60) }
            .take(3)

        return AgentSignalResponse(
            riskScore = riskScore100 / 100f,
            riskLevel = normalizedState.toLegacyRiskLevel(fallbackRiskLevel),
            emotionalState = emotionalLabelFor(json, normalizedState),
            suggestedAction = sanitizeShortText(
                json.optString("suggestedAction", "建议查看状态偏离详情并继续观察。")
            ),
            careMessage = sanitizeShortText(
                json.optString("careMessage", "检测到节律波动，建议先暂停片刻并确认当前状态。")
            ),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", normalizedState == "needs_confirmation"),
            thresholdAdjustments = emptyMap(),
            confidence = confidence,
            reason = json.optString("reason", "normalized_agent_response").take(80),
            mainReasons = reasons,
            metricDeviationPercent = json.optJSONObject("metricDeviationPercent").toFloatMap()
        )
    }

    fun extractJson(rawText: String): String {
        val withoutThinking = rawText
            .replace(Regex("<thinking>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
            .find(withoutThinking)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null && fenced.startsWith("{") && fenced.endsWith("}")) return fenced
        val start = withoutThinking.indexOf('{')
        val end = withoutThinking.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            withoutThinking.substring(start, end + 1)
        } else {
            "{}"
        }
    }

    private fun normalizeState(raw: String): String {
        val value = raw.lowercase().trim()
        val mapped = when {
            value in allowedStates -> value
            value in setOf("calm", "normal", "low") -> "stable"
            value.contains("mild") || value.contains("fluctuation") || value.contains("support") -> "mild_fluctuation"
            value.contains("observe") || value.contains("warning") -> "observe"
            value.contains("confirm") || value.contains("guardian") -> "needs_confirmation"
            value.contains("focus") || value.contains("attention") || value.contains("urgent") -> "focus_attention"
            else -> "unknown"
        }
        return if (mapped in allowedStates) mapped else "unknown"
    }

    private fun emotionalLabelFor(json: JSONObject, normalizedState: String): String {
        val explicit = sanitizeShortText(json.optString("emotionalState")).takeIf {
            it.isNotBlank() && it != "结构化特征触发本地安全兜底"
        }
        if (explicit != null) return explicit.take(24)
        return when (normalizedState) {
            "stable" -> "相对稳定"
            "mild_fluctuation" -> "轻微波动"
            "observe" -> "需要观察"
            "needs_confirmation" -> "需要确认"
            "focus_attention" -> "重点关注"
            else -> "状态不明"
        }
    }

    private fun String.toLegacyRiskLevel(fallback: String): String = when (this) {
        "stable" -> "stable"
        "mild_fluctuation" -> "observe"
        "observe" -> "observe"
        "needs_confirmation" -> "guardian_check"
        "focus_attention" -> "support"
        else -> fallback.ifBlank { "observe" }
    }

    private fun fallbackScoreFor(state: String): Float = when (state) {
        "stable" -> 18f
        "mild_fluctuation" -> 38f
        "observe" -> 52f
        "needs_confirmation" -> 76f
        "focus_attention" -> 68f
        else -> 45f
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private fun JSONObject?.toFloatMap(): Map<String, Float> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { key ->
            max(-999f, min(999f, optDouble(key, 0.0).toFloat()))
        }
    }

    private fun sanitizeShortText(value: String): String {
        val diagnosisWords = listOf("焦虑症", "抑郁症", "自残", "自杀", "诊断", "临床", "治疗")
        return diagnosisWords.fold(value.replace('\n', ' ').trim()) { text, word ->
            text.replace(word, "状态偏离")
        }.ifBlank { "结构化特征触发本地安全兜底" }
    }
}
