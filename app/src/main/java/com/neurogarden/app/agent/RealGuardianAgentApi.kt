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

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse {
        val systemPrompt = """
            You are NeuroGarden's psychological companion skill.
            Role: a calm, non-clinical emotional support companion.
            Goals: help the user slow down, name feelings gently, regain agency, and choose one tiny next step.
            Use the provided structured context: recent activity, abnormal signals, and personality/care preferences.
            Do not diagnose mental illness, do not claim certainty, do not mention raw passive text, and do not shame the user.
            If the user says they are unsafe, cannot control themselves, or may hurt themselves/others, calmly suggest contacting a trusted person or local emergency help and set shouldNotifyGuardian=true.
            Keep replies warm, concrete, and short: 2-5 Chinese sentences.
            Return JSON only:
            reply: string
            riskLevel: stable|observe|support|guardian_check|urgent_support
            suggestedAction: string
            shouldNotifyGuardian: boolean
            confidence: 0.0-1.0
            reason: string
        """.trimIndent()
        val payload = JSONObject()
            .put("currentRiskLevel", request.currentRiskLevel)
            .put("currentRiskScore", request.currentRiskScore)
            .put("userEmotionLabel", request.userEmotionLabel ?: JSONObject.NULL)
            .put("recentRiskContext", request.recentRiskContext ?: JSONObject.NULL)
            .put("personalityModel", request.personalityModel ?: JSONObject.NULL)
            .put("recentActivity", request.recentActivity ?: JSONObject.NULL)
            .put("recentSignals", JSONArray().also { array ->
                request.recentSignals.take(8).forEach { array.put(it.toJson()) }
            })
            .put("conversation", JSONArray().also { array ->
                request.conversation.takeLast(10).forEach {
                    array.put(JSONObject().put("role", it.role).put("content", it.content))
                }
            })
            .put("latestUserMessage", request.latestUserMessage)
            .toString()
        val text = requestText(systemPrompt, payload)
        return parseSupportConversation(text, request.currentRiskLevel)
    }

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

    private fun parseSupportConversation(text: String, fallbackLevel: String): SupportConversationResponse {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull()
            ?: runCatching {
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                JSONObject(text.substring(start, end + 1))
            }.getOrElse {
                return SupportConversationResponse(
                    reply = "我在。我们先把事情缩小一点：现在只需要慢慢呼一口气，然后告诉我身体哪里最紧。",
                    riskLevel = fallbackLevel,
                    suggestedAction = "继续温和陪伴",
                    shouldNotifyGuardian = false,
                    confidence = 0.55f,
                    reason = "support_response_parse_failed"
                )
            }
        return SupportConversationResponse(
            reply = json.optString("reply", "我在。先慢慢呼一口气，我们一点点来。").take(300),
            riskLevel = json.optString("riskLevel", fallbackLevel),
            suggestedAction = json.optString("suggestedAction", "继续温和陪伴").take(120),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", false),
            confidence = json.optDouble("confidence", 0.65).toFloat().coerceIn(0f, 1f),
            reason = json.optString("reason", "psychological_companion_skill").take(160)
        )
    }
}
