package com.neurogarden.app.agent

import com.neurogarden.app.data.local.ConversationSummaryEntity
import com.neurogarden.app.data.local.FeedbackRecordEntity
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.RiskEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CompanionContextBuilder {
    fun buildRecentActivity(
        samples: List<HabitSampleEntity>,
        events: List<RiskEventEntity> = emptyList()
    ): String {
        val latest = samples.maxByOrNull { it.timestamp }
        val latestEvent = events.maxByOrNull { it.startTime }
        if (latest == null && latestEvent == null) return "暂无近期结构化行为样本。"

        val signals = buildList {
            latest?.let { sample ->
                addAll(sample.behaviorSignals())
                sample.appSceneLabel().takeIf { it.isNotBlank() }?.let { add("最近操作场景偏向：$it") }
            }
            latestEvent?.let { event ->
                val reasons = event.mainReasons.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (reasons.isNotEmpty()) add("提醒直接原因：${reasons.take(3).joinToString("、")}")
                add("提醒时段：${event.startTime.shortTime()}，状态评分 ${"%.2f".format(event.riskScore)}")
            }
        }.distinct()

        return if (signals.isEmpty()) {
            "近期只有少量结构化记录，暂时不推断具体原因。"
        } else {
            "刚才更可能触发提醒的线索：${signals.joinToString("；")}。这些线索只来自心率、呼吸、输入节奏和操作场景类别，不包含输入原文或具体内容。"
        }
    }

    fun buildPersonalityModel(
        feedbacks: List<FeedbackRecordEntity>,
        summaries: List<ConversationSummaryEntity>,
        samples: List<HabitSampleEntity>,
        lastEmotionLabel: String?
    ): String {
        val labels = feedbacks.map { it.userLabel }.filter { it.isNotBlank() }
        val commonLabels = labels.groupingBy { it }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString("、") { it.key }
            .ifBlank { "暂无稳定标注" }

        val helpfulRate = if (feedbacks.isEmpty()) {
            "暂无足够反馈"
        } else {
            val helpful = feedbacks.count { it.helpful }
            "$helpful/${feedbacks.size} 次反馈认为提醒有帮助"
        }

        val style = inferPreferredStyle(lastEmotionLabel, summaries)
        val rhythm = inferRhythm(samples)
        val memory = summaries.take(5)
            .map { ChatTextSanitizer.cleanShortText(it.summary, "一次简短陪伴对话") }
            .joinToString("；")
            .ifBlank { "暂无历史对话摘要" }

        return listOf(
            "用户最近常见自我标注：$commonLabels",
            "提醒接受度：$helpfulRate",
            "沟通偏好：$style",
            "近期节律画像：$rhythm",
            "历史陪伴记忆：$memory"
        ).joinToString("。")
    }

    fun openingFor(
        currentRiskLevel: String,
        recentActivity: String,
        personalityModel: String
    ): String {
        val contextHint = recentActivity
            .removePrefix("刚才更可能触发提醒的线索：")
            .substringBefore("。这些线索")
            .take(90)
        val styleHint = when {
            personalityModel.contains("省力") -> "你不用组织很多语言，发一个词也可以。"
            personalityModel.contains("少讲道理") -> "我先不讲道理，只陪你把这一刻放轻一点。"
            personalityModel.contains("安全感") -> "我们先确认此刻是安全、可控的。"
            else -> "你可以只说一个词，我会跟着你的节奏来。"
        }
        val riskOpening = when (currentRiskLevel) {
            "urgent_support", "guardian_check" -> "我注意到刚才的波动比较明显，$contextHint"
            "support" -> "我看到刚才有一阵节律变紧，$contextHint"
            "observe" -> "刚才有一点节律偏离，$contextHint"
            else -> "我在，刚才的记录没有显示特别强的波动"
        }.trim().trimEnd('；', '，')

        return "$riskOpening。$styleHint"
    }

    fun summarizeConversation(
        userMessage: String,
        assistantReply: String,
        recentActivity: String
    ): String {
        val userTone = when {
            userMessage.contains("焦") || userMessage.contains("慌") -> "用户表达焦躁或高唤醒"
            userMessage.contains("难受") || userMessage.contains("低落") -> "用户表达难受或低落"
            userMessage.contains("累") || userMessage.contains("困") -> "用户表达疲惫"
            userMessage.contains("紧张") -> "用户表达紧张"
            userMessage.contains("没事") || userMessage.contains("还好") -> "用户表示状态可控"
            else -> "用户进行了简短回应"
        }
        val supportMove = when {
            assistantReply.contains("联系") || assistantReply.contains("守护") -> "建议连接可信任支持"
            assistantReply.contains("呼吸") || assistantReply.contains("慢慢") -> "建议呼吸和落地练习"
            assistantReply.contains("水") || assistantReply.contains("坐") -> "建议低成本照护动作"
            else -> "继续支持性倾听"
        }
        val trigger = recentActivity.substringAfter("线索：", recentActivity).substringBefore("。").take(80)
        return "$userTone；触发线索：$trigger；$supportMove。"
    }

    private fun HabitSampleEntity.behaviorSignals(): List<String> = buildList {
        if (typingSpeed >= 150f) add("输入节奏明显变快")
        if (typingSpeed in 1f..55f) add("输入速度明显放慢")
        if (deleteRate >= 0.18f) add("反复修改或删除增加")
        if (pauseDuration >= 6f && typingSpeed > 0f) add("输入后停顿变长")
        if (heartRate >= 96) add("心率偏高")
        if (breathRate >= 18) add("呼吸偏快")
        if (motionLevel >= 0.60f) add("运动干扰较高，需要谨慎判断")
    }

    private fun HabitSampleEntity.appSceneLabel(): String = when {
        contextTag.contains("chat", ignoreCase = true) -> "社交/聊天"
        contextTag.contains("video", ignoreCase = true) -> "视频/内容浏览"
        contextTag.contains("browser", ignoreCase = true) -> "网页浏览"
        contextTag.contains("game", ignoreCase = true) -> "游戏"
        contextTag.contains("typing", ignoreCase = true) -> "输入操作"
        contextTag.contains("wear", ignoreCase = true) -> "手表采集"
        contextTag.contains("demo", ignoreCase = true) -> "演示数据"
        else -> ""
    }

    private fun inferPreferredStyle(
        lastEmotionLabel: String?,
        summaries: List<ConversationSummaryEntity>
    ): String = when {
        lastEmotionLabel == "累" || summaries.any { it.summary.contains("疲惫") } ->
            "省力、短句、允许休息，避免布置复杂任务"
        lastEmotionLabel == "焦" || summaries.any { it.summary.contains("焦躁") } ->
            "少讲道理，先承认感受，再给一个很小的选择"
        lastEmotionLabel == "低落" || summaries.any { it.summary.contains("低落") } ->
            "稳定陪伴、低压力确认、避免积极说教"
        lastEmotionLabel == "紧张" || summaries.any { it.summary.contains("紧张") } ->
            "先给安全感和身体落地提示，再轻柔询问"
        lastEmotionLabel == "没事" ->
            "尊重自主性，低打扰，提供可选支持"
        else ->
            "温和、短句、非评判，一次只问一个问题"
    }

    private fun inferRhythm(samples: List<HabitSampleEntity>): String {
        if (samples.isEmpty()) return "样本不足，暂不形成稳定判断"
        val recent = samples.take(12)
        val highDelete = recent.count { it.deleteRate >= 0.18f }
        val longPause = recent.count { it.pauseDuration >= 6f }
        val highPhysiology = recent.count { it.heartRate >= 96 || it.breathRate >= 18 }
        return buildList {
            if (highDelete >= 2) add("近期反复修改偏多")
            if (longPause >= 2) add("输入后停顿偏长")
            if (highPhysiology >= 2) add("生理唤醒偏高")
        }.joinToString("、").ifBlank { "近期节律相对平稳或样本偏少" }
    }

    private fun Long.shortTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
}
