package com.neurogarden.app.passive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.neurogarden.app.MainActivity
import com.neurogarden.app.NeuroGardenApp
import com.neurogarden.app.agent.AgentSignalRequest
import com.neurogarden.app.agent.toDto
import com.neurogarden.app.algorithm.CareMode
import com.neurogarden.app.algorithm.CareModePolicies
import com.neurogarden.app.algorithm.DataQualityEvaluator
import com.neurogarden.app.algorithm.HabitLearningEngine
import com.neurogarden.app.algorithm.PersonalizedRiskCalculator
import com.neurogarden.app.algorithm.RiskLevel
import com.neurogarden.app.algorithm.TrendAnalyzer
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.shared.model.SensorPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class PassiveGuardianService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var elevatedTicks = 0
    private var combinedAlertTicks = 0

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(ONGOING_NOTIFICATION_ID, ongoingNotification())
        monitorJob = scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        while (currentCoroutineContext().isActive) {
            runCatching { collectAndEvaluate() }
            delay(30_000L)
        }
    }

    private suspend fun collectAndEvaluate() {
        val app = application as NeuroGardenApp
        val now = System.currentTimeMillis()
        val mode = app.careModeStore.currentModeSnapshot()
        val policy = CareModePolicies.policyFor(mode)
        val interaction = AccessibilitySignalStore.snapshotAndReset(this, now)
        val watchPacket = WatchSignalStore.currentPacket(this, now)
        val interactionRisk = interactionRisk(interaction)
        val physiologyRisk = watchPacket?.let { physiologyRisk(it) } ?: 0f
        val combinedRisk = (interactionRisk * 0.48f + physiologyRisk * 0.52f).coerceIn(0f, 1f)

        val sample = HabitSampleEntity(
            timestamp = now,
            heartRate = watchPacket?.heartRate ?: 0,
            breathRate = watchPacket?.breathRate ?: 0,
            motionLevel = watchPacket?.motionLevel ?: 0f,
            typingSpeed = interaction.typingSpeed,
            deleteRate = interaction.deleteRate,
            pauseDuration = interaction.pauseDuration,
            userFeedback = null,
            contextTag = if (watchPacket == null) "typing_without_watch" else "typing_with_watch",
            riskLevel = "observe",
            createdAt = now
        )
        app.habitRepository.saveSample(sample)

        val samples = app.habitRepository.getRecentSamples(30)
        val baseline = app.habitRepository.getLatestBaseline()
            ?: HabitLearningEngine.buildBaseline(samples, now)
        val thresholds = app.habitRepository.getLatestThresholdProfile()
            ?: HabitLearningEngine.buildThresholdProfile(baseline, now)
        val weather = app.weatherRepository.current()
        val agent = app.guardianAgentApi.analyzeSignals(
            AgentSignalRequest(
                userId = "local-demo-user",
                recentSignals = samples.map { it.toDto() },
                currentBaseline = baseline.toDto(),
                currentThresholds = thresholds.toDto(),
                latestRiskScore = combinedRisk,
                latestRiskLevel = "observe",
                userFeedback = null,
                weather = weather.eventLabel(),
                timeSegment = now.timeSegment()
            )
        )
        val risk = PersonalizedRiskCalculator.calculate(sample, baseline, thresholds, agent, samples)
        val trend = TrendAnalyzer.analyze(samples, now)
        val todayEvents = app.riskEventRepository.getTodayEvents(now)
        val quality = DataQualityEvaluator.evaluate(samples, emptyList(), todayEvents, baseline)
        app.riskEventRepository.recordIfNeeded(
            sample = sample,
            baseline = baseline,
            risk = risk,
            agentResponse = agent,
            weather = weather.eventLabel()
        )
        val combinedAlert = combinedAlertFor(interaction, watchPacket, interactionRisk, physiologyRisk)
        val reason = explainEvaluation(interaction, watchPacket, interactionRisk, physiologyRisk, combinedAlert, quality.qualityLevel)

        PassiveDebugStore.save(
            this,
            PassiveDebugSnapshot(
                evaluatedAt = now,
                typingSpeed = interaction.typingSpeed,
                deleteRate = interaction.deleteRate,
                pauseDuration = interaction.pauseDuration,
                interactionRisk = interactionRisk,
                heartRate = watchPacket?.heartRate ?: 0,
                breathRate = watchPacket?.breathRate ?: 0,
                motionLevel = watchPacket?.motionLevel ?: 0f,
                physiologyRisk = physiologyRisk,
                combinedRisk = combinedRisk,
                alertAllowed = combinedAlert != null && quality.qualityLevel != "low",
                lastReason = reason
            )
        )

        combinedAlertTicks = if (combinedAlert != null) combinedAlertTicks + 1 else 0
        elevatedTicks = if (risk.riskLevel >= RiskLevel.SUPPORT || trend.shouldIntervene) elevatedTicks + 1 else 0

        val notificationPlan = notificationPlanFor(mode, combinedAlert ?: risk.careMessage)
        val maxDailyNotifications = when (mode) {
            CareMode.SELF_MONITORING -> 4
            else -> policy.maxDailyGuardianAlerts
        }
        val cooldownMs = if (mode == CareMode.SPECIAL_CARE) {
            30L * 60L * 1000L
        } else {
            15L * 60L * 1000L
        }
        if ((combinedAlertTicks >= 1 || elevatedTicks >= 2 || trend.sustainedDeviationMinutes >= 15) &&
            quality.qualityLevel != "low" &&
            NotificationPolicyStore.canNotify(
                context = this,
                now = now,
                cooldownMs = cooldownMs,
                maxDailyNotifications = maxDailyNotifications
            )
        ) {
            notifySupport(notificationPlan.title, notificationPlan.message)
            NotificationPolicyStore.recordNotification(this, now)
            combinedAlertTicks = 0
            elevatedTicks = 0
        }
    }

    private fun interactionRisk(signal: InteractionSignalSnapshot): Float {
        val fastTyping = ((signal.typingSpeed - 120f) / 100f).coerceIn(0f, 1f)
        val slowTypingAfterInput = if (signal.typingSpeed in 1f..40f) 0.35f else 0f
        val deleteLift = (signal.deleteRate / 0.25f).coerceIn(0f, 1f)
        val pauseLift = (signal.pauseDuration / 12f).coerceIn(0f, 1f)
        return min(1f, max(0f, fastTyping * 0.30f + slowTypingAfterInput + deleteLift * 0.35f + pauseLift * 0.35f))
    }

    private fun physiologyRisk(packet: SensorPacket): Float {
        if (packet.motionLevel >= 0.60f) return 0f
        val heartLift = ((packet.heartRate - 82f) / 34f).coerceIn(0f, 1f)
        val breathLift = ((packet.breathRate - 16f) / 12f).coerceIn(0f, 1f)
        return (heartLift * 0.58f + breathLift * 0.42f).coerceIn(0f, 1f)
    }

    private fun combinedAlertFor(
        signal: InteractionSignalSnapshot,
        packet: SensorPacket?,
        interactionRisk: Float,
        physiologyRisk: Float
    ): String? {
        if (packet == null) return null
        if (packet.motionLevel >= 0.60f) return null
        if (interactionRisk < 0.40f || physiologyRisk < 0.35f) return null

        val typingReasons = buildList {
            if (signal.typingSpeed >= 160f) add("打字节奏变快")
            if (signal.deleteRate >= 0.25f) add("删除频率升高")
            if (signal.pauseDuration >= 12f && signal.typingSpeed > 0f) add("输入后停顿较久")
            if (interactionRisk >= 0.55f) add("输入节奏偏离")
        }.distinct()
        val watchReasons = buildList {
            if (packet.heartRate >= 92) add("心率偏高")
            if (packet.breathRate >= 19) add("呼吸偏快")
        }
        if (typingReasons.isEmpty() || watchReasons.isEmpty()) return null

        return "检测到${typingReasons.joinToString("、")}，同时${watchReasons.joinToString("、")}。这更像紧张/焦虑相关的状态偏离，要不要停十秒做一次慢呼吸？"
    }

    private fun explainEvaluation(
        signal: InteractionSignalSnapshot,
        packet: SensorPacket?,
        interactionRisk: Float,
        physiologyRisk: Float,
        combinedAlert: String?,
        dataQualityLevel: String
    ): String {
        if (dataQualityLevel == "low") return "仅记录：数据可信度 low，不升级为强提醒。"
        if (combinedAlert != null) return "满足弹窗条件：输入节奏异常 + 心率/呼吸异常 + 非运动干扰。"
        if (packet == null) return "未满足：没有真实手表数据，也没有开启模拟手表数据。"
        if (packet.motionLevel >= 0.60f) return "未满足：运动干扰过高，避免误报。"
        if (interactionRisk < 0.40f) {
            return "未满足：输入节奏风险不足。typed=${"%.1f".format(signal.typingSpeed)}, delete=${"%.2f".format(signal.deleteRate)}, pause=${"%.1f".format(signal.pauseDuration)}"
        }
        if (physiologyRisk < 0.35f) {
            return "未满足：心率/呼吸风险不足。heart=${packet.heartRate}, breath=${packet.breathRate}"
        }
        return "未满足：输入和生理信号没有同时达到提醒条件。"
    }

    private data class NotificationPlan(
        val title: String,
        val message: String
    )

    private fun notificationPlanFor(mode: CareMode, detail: String): NotificationPlan =
        when (mode) {
            CareMode.SELF_MONITORING -> NotificationPlan(
                title = "今日状态有些波动",
                message = "今日状态波动较明显，建议稍后查看状态摘要。$detail"
            )

            CareMode.FAMILY_GUARDIAN -> NotificationPlan(
                title = "建议进行一次状态确认",
                message = "检测到用户状态持续偏离日常节奏，建议进行一次状态确认。$detail"
            )

            CareMode.SPECIAL_CARE -> NotificationPlan(
                title = "照护确认提醒",
                message = "检测到被照护者出现持续状态偏离，建议照护者进行确认。$detail"
            )
        }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "NeuroGarden 综合状态提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当输入节奏和手表生理数据同时异常时显示状态提醒。"
            }
        )
    }

    private fun ongoingNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("NeuroGarden 正在守护")
            .setContentText("正在综合输入节奏和手表/模拟手表数据，不读取输入内容。")
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()

    private fun notifySupport(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            SUPPORT_NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(mainPendingIntent())
                .setDefaults(Notification.DEFAULT_ALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        )
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Long.timeSegment(): String {
        val hour = Calendar.getInstance().apply { timeInMillis = this@timeSegment }
            .get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "late_night"
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "night"
        }
    }

    companion object {
        private const val CHANNEL_ID = "combined_state_alerts"
        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val SUPPORT_NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, PassiveGuardianService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PassiveGuardianService::class.java))
        }
    }
}
