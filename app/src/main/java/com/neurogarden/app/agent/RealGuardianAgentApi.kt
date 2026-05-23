package com.neurogarden.app.agent

import com.neurogarden.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RealGuardianAgentApi : GuardianAgentApi {
    override suspend fun analyzeSignals(request: AgentSignalRequest): AgentSignalResponse {
        val systemPrompt = """
            You are NeuroGarden's structured state monitoring agent.
            Use only authorized structured features. Do not diagnose, do not infer diseases,
            do not predict extreme behavior, and do not save or request raw text.
            Return JSON only with these fields:
            state: stable|mild_fluctuation|observe|needs_confirmation|focus_attention|unknown
            riskScore: 0-100
            confidence: 0.0-1.0
            mainReasons: string array, max 3
            suggestedAction: short non-medical action
            careMessage: short non-medical status note
            shouldNotifyGuardian: boolean
            metricDeviationPercent: object
            reason: short summary
        """.trimIndent()
        val payload = JSONObject()
            .put("latestRiskScore", request.latestRiskScore)
            .put("latestRiskLevel", request.latestRiskLevel)
            .put("userFeedback", request.userFeedback ?: JSONObject.NULL)
            .put("weather", request.weather ?: JSONObject.NULL)
            .put("timeSegment", request.timeSegment ?: JSONObject.NULL)
            .put("baseline", request.currentBaseline.toJson())
            .put("thresholds", request.currentThresholds.toJson())
            .put("recentSignals", JSONArray().also { array ->
                request.recentSignals.take(12).forEach { array.put(it.toJson()) }
            })
            .toString()
        val responseText = requestText(systemPrompt, payload)
        return AgentResponseNormalizer.normalizeSignalResponse(responseText, request.latestRiskLevel)
    }

    override suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse =
        ThresholdTuningResponse(emptyMap(), "Real Agent does not directly mutate thresholds in demo mode.", 0.6f)

    override suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse =
        CareMessageResponse(
            message = "检测到节律波动，建议先暂停片刻并查看结构化指标。",
            suggestedAction = "继续观察"
        )

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse =
        SupportConversationResponse(
            reply = "当前版本不启用长对话，仅展示结构化监测和反馈闭环。",
            riskLevel = request.currentRiskLevel,
            suggestedAction = "查看状态偏离详情",
            shouldNotifyGuardian = false,
            confidence = 0.6f,
            reason = "chat_disabled_for_guardian_monitoring"
        )

    private suspend fun requestText(systemPrompt: String, userPayload: String): String = withContext(Dispatchers.IO) {
        require(BuildConfig.GUARDIAN_API_KEY.isNotBlank() && BuildConfig.GUARDIAN_API_URL.isNotBlank()) {
            "Guardian API is not configured"
        }
        if (BuildConfig.GUARDIAN_API_URL.contains("/anthropic", ignoreCase = true)) {
            requestAnthropicText(systemPrompt, userPayload)
        } else {
            requestOpenAiText(systemPrompt, userPayload)
        }
    }

    private fun requestOpenAiText(systemPrompt: String, userPrompt: String): String {
        val connection = (URL(openAiEndpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.GUARDIAN_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject()
            .put("model", BuildConfig.GUARDIAN_MODEL)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (connection.responseCode !in 200..299) error("Guardian API failed: ${connection.responseCode}")
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun requestAnthropicText(systemPrompt: String, userPrompt: String): String {
        val connection = (URL(anthropicEndpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.GUARDIAN_API_KEY}")
            setRequestProperty("X-Api-Key", BuildConfig.GUARDIAN_API_KEY)
            setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject()
            .put("model", BuildConfig.GUARDIAN_MODEL)
            .put("max_tokens", 1200)
            .put("system", systemPrompt)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userPrompt)))
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (connection.responseCode !in 200..299) error("Guardian API failed: ${connection.responseCode}")
        val contentBlocks = JSONObject(response).getJSONArray("content")
        return buildList {
            for (index in 0 until contentBlocks.length()) {
                val block = contentBlocks.getJSONObject(index)
                if (block.optString("type") == "text") add(block.optString("text"))
            }
        }.joinToString("\n")
    }

    private fun openAiEndpoint(): String {
        val raw = BuildConfig.GUARDIAN_API_URL.trim().trimEnd('/')
        return if (raw.endsWith("/chat/completions")) raw else "$raw/v1/chat/completions"
    }

    private fun anthropicEndpoint(): String {
        val raw = BuildConfig.GUARDIAN_API_URL.trim().trimEnd('/')
        return if (raw.endsWith("/v1/messages")) raw else "$raw/v1/messages"
    }

    private fun UserHabitBaselineDto.toJson(): JSONObject =
        JSONObject()
            .put("avgRestingHeartRate", avgRestingHeartRate)
            .put("avgBreathRate", avgBreathRate)
            .put("avgTypingSpeed", avgTypingSpeed)
            .put("avgDeleteRate", avgDeleteRate)
            .put("avgPauseDuration", avgPauseDuration)
            .put("avgRecoveryDuration", avgRecoveryDuration)
            .put("sampleCount", sampleCount)
            .put("confidenceLevel", confidenceLevel)

    private fun ThresholdProfileDto.toJson(): JSONObject =
        JSONObject()
            .put("heartRateDeltaWarning", heartRateDeltaWarning)
            .put("breathRateWarning", breathRateWarning)
            .put("typingSpeedDeltaWarning", typingSpeedDeltaWarning)
            .put("deleteRateWarning", deleteRateWarning)
            .put("pauseDurationWarning", pauseDurationWarning)
            .put("riskTriggerDuration", riskTriggerDuration)
            .put("guardianNotifyThreshold", guardianNotifyThreshold)

    private fun HabitSampleDto.toJson(): JSONObject =
        JSONObject()
            .put("timestamp", timestamp)
            .put("heartRate", heartRate)
            .put("breathRate", breathRate)
            .put("motionLevel", motionLevel)
            .put("typingSpeed", typingSpeed)
            .put("deleteRate", deleteRate)
            .put("pauseDuration", pauseDuration)
            .put("userFeedback", userFeedback ?: JSONObject.NULL)
            .put("contextTag", contextTag)
            .put("riskLevel", riskLevel)
}
