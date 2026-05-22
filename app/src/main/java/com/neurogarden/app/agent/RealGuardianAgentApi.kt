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
        val prompt = """
            你是 NeuroGarden 的结构化情绪状态评判 Agent。你不是医生，不做医学诊断，也不预测极端行为。
            你的任务是综合近期授权采集的结构化特征、个人基线、阈值、天气、时间段和用户主动反馈，判断当前状态更接近哪种情绪/身心状态。

            请只输出 JSON，不要输出 Markdown。
            JSON 字段：
            {
              "riskScore": 0.0-1.0,
              "riskLevel": "stable|observe|support|guardian_check|urgent_support",
              "emotionalState": "相对稳定|轻微压力偏离|高压紧张|焦虑紧绷|低落疲惫|疲惫恢复慢|可能需要陪伴|运动干扰|继续观察",
              "arousalScore": 0.0-1.0,
              "valenceScore": -1.0-1.0,
              "fatigueScore": 0.0-1.0,
              "lonelinessScore": 0.0-1.0,
              "stressScore": 0.0-1.0,
              "mainReasons": ["最多5个结构化原因"],
              "metricDeviationPercent": {"heartRate": 0, "breathRate": 0, "typingSpeed": 0, "deleteRate": 0, "pauseDuration": 0},
              "suggestedAction": "一句轻量建议",
              "careMessage": "不超过60字的温和提示",
              "shouldNotifyGuardian": false,
              "confidence": 0.0-1.0,
              "reason": "简短解释"
            }

            判断规则：
            - 不能只分正常/异常，要识别复杂状态。
            - 如果 motionLevel 明显高，优先考虑运动干扰并降低置信度。
            - 如果数据不足或基线可信度低，不要强提醒。
            - 如果只是单项指标异常，倾向 observe。
            - 只有多个指标持续偏离，才考虑 support 或 guardian_check。
            - 被动采集不包含用户输入原文，请不要臆测具体事件。
        """.trimIndent()
        val userPayload = JSONObject()
            .put("latestRiskScore", request.latestRiskScore)
            .put("latestRiskLevel", request.latestRiskLevel)
            .put("userEmotionLabel", request.userEmotionLabel ?: JSONObject.NULL)
            .put("userFeedback", request.userFeedback ?: JSONObject.NULL)
            .put("weather", request.weather ?: JSONObject.NULL)
            .put("timeSegment", request.timeSegment ?: JSONObject.NULL)
            .put("baseline", request.currentBaseline.toJson())
            .put("thresholds", request.currentThresholds.toJson())
            .put("recentSignals", JSONArray().also { array ->
                request.recentSignals.take(12).forEach { array.put(it.toJson()) }
            })
            .toString()
        val json = requestJson(
            systemPrompt = prompt,
            userPrompt = "请基于以下结构化数据进行判断：\n$userPayload"
        )
        return AgentSignalResponse(
            riskScore = json.optDoubleOrNull("riskScore")?.toFloat(),
            riskLevel = json.optString("riskLevel", request.latestRiskLevel),
            emotionalState = json.optString("emotionalState").takeIf { it.isNotBlank() },
            arousalScore = json.optDoubleOrNull("arousalScore")?.toFloat(),
            valenceScore = json.optDoubleOrNull("valenceScore")?.toFloat(),
            fatigueScore = json.optDoubleOrNull("fatigueScore")?.toFloat(),
            lonelinessScore = json.optDoubleOrNull("lonelinessScore")?.toFloat(),
            stressScore = json.optDoubleOrNull("stressScore")?.toFloat(),
            suggestedAction = json.optString("suggestedAction", "温和询问用户当前感受"),
            careMessage = json.optString("careMessage", "我注意到你的节奏有些变化。要不要先停一下，慢慢呼吸几次？"),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", false),
            thresholdAdjustments = emptyMap(),
            confidence = json.optDouble("confidence", 0.6).toFloat(),
            reason = json.optString("reason", "由真实 Agent 根据必要特征值生成"),
            mainReasons = json.optJSONArray("mainReasons")?.toStringList().orEmpty(),
            metricDeviationPercent = json.optJSONObject("metricDeviationPercent")?.toFloatMap().orEmpty()
        )
    }

    override suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse =
        ThresholdTuningResponse(emptyMap(), "真实 Agent 暂不直接改阈值，保留本地策略", 0.6f)

    override suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse {
        return CareMessageResponse(
            message = "我注意到状态有些波动。可以先停一下，慢慢呼吸几次。",
            suggestedAction = "慢呼吸 30 秒"
        )
    }

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse {
        val conversationText = request.conversation.takeLast(8).joinToString("\n") {
            "${it.role}: ${it.content}"
        }
        val json = requestJson(
            systemPrompt = """
                你是 NeuroGarden 的心理支持与情绪舒缓 Agent，职责是在异常弹窗中进行短时、温和、非诊断式的情绪安抚。
                规则：
                - 像温和的心理支持者一样回应，但不要自称医生，不做医学诊断，不替代专业心理咨询。
                - 不判断用户有疾病，不预测极端行为。
                - 不追问隐私细节，不要求用户解释完整原因。
                - 每次回复控制在 80 字以内，问题要轻柔、可跳过。
                - 优先帮助用户稳定呼吸、确认当下感受、降低紧张感。
                - 如果用户明确表示需要他人帮助，再建议联系守护人。
                输出 JSON：reply, riskLevel, suggestedAction, shouldNotifyGuardian, confidence, reason。
            """.trimIndent(),
            userPrompt = """
                当前风险：${request.currentRiskLevel} / ${request.currentRiskScore}
                用户最近主动情绪标注：${request.userEmotionLabel ?: "无"}
                最近对话：
                $conversationText
                用户最新回复：${request.latestUserMessage}
            """.trimIndent()
        )
        return SupportConversationResponse(
            reply = json.optString("reply", "谢谢你告诉我。我们先慢一点，把注意力放到下一次呼气上。"),
            riskLevel = json.optString("riskLevel", request.currentRiskLevel),
            suggestedAction = json.optString("suggestedAction", "继续温和陪伴并引导呼吸"),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", false),
            confidence = json.optDouble("confidence", 0.65).toFloat(),
            reason = json.optString("reason", "弹窗内情绪舒缓 Agent 根据用户主动回复生成")
        )
    }

    private suspend fun requestJson(systemPrompt: String, userPrompt: String): JSONObject = withContext(Dispatchers.IO) {
        require(BuildConfig.GUARDIAN_API_KEY.isNotBlank() && BuildConfig.GUARDIAN_API_URL.isNotBlank()) {
            "Guardian API is not configured"
        }
        val responseText = if (BuildConfig.GUARDIAN_API_URL.contains("/anthropic", ignoreCase = true)) {
            requestAnthropicText(systemPrompt, userPrompt)
        } else {
            requestOpenAiText(systemPrompt, userPrompt)
        }
        JSONObject(responseText.toJsonObjectText())
    }

    private fun requestOpenAiText(systemPrompt: String, userPrompt: String): String {
        val connection = (URL(endpoint()).openConnection() as HttpURLConnection).apply {
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
            .put(
                "messages",
                JSONArray()
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
        val contentBlocks = JSONObject(response).getJSONArray("content")
        val texts = buildList {
            for (index in 0 until contentBlocks.length()) {
                val block = contentBlocks.getJSONObject(index)
                if (block.optString("type") == "text") {
                    add(block.optString("text"))
                }
            }
        }
        return texts.joinToString("\n").ifBlank { error("Guardian API returned no text block") }
    }

    private fun endpoint(): String {
        val raw = BuildConfig.GUARDIAN_API_URL.trim().trimEnd('/')
        return if (raw.endsWith("/chat/completions")) raw else "$raw/v1/chat/completions"
    }

    private fun anthropicEndpoint(): String {
        val raw = BuildConfig.GUARDIAN_API_URL.trim().trimEnd('/')
        return if (raw.endsWith("/v1/messages")) raw else "$raw/v1/messages"
    }

    private fun String.toJsonObjectText(): String {
        val trimmed = trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        if (fenced != null && fenced.startsWith("{") && fenced.endsWith("}")) return fenced
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        error("Guardian API returned non-JSON content")
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

    private fun JSONObject.toFloatMap(): Map<String, Float> =
        keys().asSequence().associateWith { key -> optDouble(key, 0.0).toFloat() }

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
