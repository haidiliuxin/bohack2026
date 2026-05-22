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
            你是 NeuroGarden 的温和情绪守护 Agent。只基于特征值判断状态偏离，不做医学诊断。
            请输出 JSON：riskLevel, suggestedAction, careMessage, shouldNotifyGuardian, confidence, reason。
            风险等级只能是 stable, observe, support, guardian_check, urgent_support。
            近期特征数量：${request.recentSignals.size}
            最新风险分：${request.latestRiskScore}
            最新风险等级：${request.latestRiskLevel}
            基线可信度：${request.currentBaseline.confidenceLevel}
            用户最近主动情绪标注：${request.userEmotionLabel ?: "无"}
        """.trimIndent()
        val json = requestJson(prompt, "请生成克制、轻柔、不吓人的关怀判断。")
        return AgentSignalResponse(
            riskLevel = json.optString("riskLevel", request.latestRiskLevel),
            suggestedAction = json.optString("suggestedAction", "温和询问用户当前感受"),
            careMessage = json.optString("careMessage", "我注意到你的节奏有些变化。要不要先停一下，慢慢呼吸几次？"),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", false),
            thresholdAdjustments = emptyMap(),
            confidence = json.optDouble("confidence", 0.6).toFloat(),
            reason = json.optString("reason", "由真实 Agent 根据必要特征值生成")
        )
    }

    override suspend fun tuneThresholds(request: ThresholdTuningRequest): ThresholdTuningResponse =
        ThresholdTuningResponse(emptyMap(), "真实 Agent 暂不直接改阈值，保留本地策略", 0.6f)

    override suspend fun generateCareMessage(request: CareMessageRequest): CareMessageResponse {
        val json = requestJson(
            systemPrompt = "你是一个轻柔、非诊断式的情绪支持助手。避免命令式语气，允许用户跳过回答。",
            userPrompt = "请针对风险等级 ${request.riskLevel} 写一句不超过 45 字的关怀语，并给出一个轻量行动。输出 JSON：message, suggestedAction。"
        )
        return CareMessageResponse(
            message = json.optString("message", "我在这里。我们可以先一起做一次慢呼吸。"),
            suggestedAction = json.optString("suggestedAction", "慢呼吸 30 秒")
        )
    }

    override suspend fun continueSupportConversation(request: SupportConversationRequest): SupportConversationResponse {
        val conversationText = request.conversation.takeLast(8).joinToString("\n") {
            "${it.role}: ${it.content}"
        }
        val json = requestJson(
            systemPrompt = """
                你是 NeuroGarden 的关怀对话 Agent。目标是轻柔复核用户状态并提供支持。
                规则：
                - 不做医学诊断，不预测极端行为。
                - 不追问隐私细节，不要求用户解释完整原因。
                - 问题要短、柔和、可跳过。
                - 如果用户表达明显不安全、失控或需要他人帮助，建议联系守护人。
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
            reply = json.optString("reply", "谢谢你告诉我。现在可以先只关注下一次呼气，我们慢一点来。"),
            riskLevel = json.optString("riskLevel", request.currentRiskLevel),
            suggestedAction = json.optString("suggestedAction", "继续陪伴并引导呼吸"),
            shouldNotifyGuardian = json.optBoolean("shouldNotifyGuardian", false),
            confidence = json.optDouble("confidence", 0.65).toFloat(),
            reason = json.optString("reason", "真实 Agent 根据用户主动对话复核状态")
        )
    }

    private suspend fun requestJson(systemPrompt: String, userPrompt: String): JSONObject = withContext(Dispatchers.IO) {
        require(BuildConfig.GUARDIAN_API_KEY.isNotBlank() && BuildConfig.GUARDIAN_API_URL.isNotBlank()) {
            "Guardian API is not configured"
        }
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
        val content = JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        JSONObject(content)
    }

    private fun endpoint(): String {
        val raw = BuildConfig.GUARDIAN_API_URL.trim().trimEnd('/')
        return if (raw.endsWith("/chat/completions")) raw else "$raw/v1/chat/completions"
    }
}
