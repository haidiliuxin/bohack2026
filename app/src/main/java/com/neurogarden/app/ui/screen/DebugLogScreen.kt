package com.neurogarden.app.ui.screen

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neurogarden.app.data.local.AgentAuditLogEntity
import com.neurogarden.app.passive.AccessibilitySignalStore
import com.neurogarden.app.passive.NotificationPolicyStore
import com.neurogarden.app.passive.PassiveOverlayAlert
import com.neurogarden.app.passive.PassiveDebugSnapshot
import com.neurogarden.app.passive.PassiveDebugStore
import com.neurogarden.app.passive.PendingPassiveAlertStore
import com.neurogarden.app.passive.WatchSignalStore
import kotlinx.coroutines.delay

@Composable
fun DebugLogScreen(
    logs: List<AgentAuditLogEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var passive by remember { mutableStateOf(PassiveDebugStore.read(context)) }
    var accessibility by remember { mutableStateOf(AccessibilitySignalStore.debugSnapshot(context)) }
    var watchSettings by remember { mutableStateOf(WatchSignalStore.readSettings(context)) }
    var watchPacket by remember { mutableStateOf(WatchSignalStore.currentPacket(context)) }
    var pendingAlert by remember { mutableStateOf(PendingPassiveAlertStore.read(context)) }
    var notificationStatus by remember { mutableStateOf(NotificationPolicyStore.status(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            passive = PassiveDebugStore.read(context)
            accessibility = AccessibilitySignalStore.debugSnapshot(context)
            watchSettings = WatchSignalStore.readSettings(context)
            watchPacket = WatchSignalStore.currentPacket(context)
            pendingAlert = PendingPassiveAlertStore.read(context)
            notificationStatus = NotificationPolicyStore.status(context)
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("采集日志", style = MaterialTheme.typography.headlineMedium)
        Text("这个页面每秒刷新，用来确认输入节奏、手表/模拟手表、后台判断和 Agent 请求日志是否闭环。")

        DebugCard("无障碍输入事件") {
            Text("当前窗口 typedCount：${accessibility.typedCount}")
            Text("当前窗口 deleteCount：${accessibility.deleteCount}")
            Text("最后 delta：${accessibility.lastDelta}")
            Text("最后事件类型：${accessibility.lastEventType}")
            Text("最近操作场景：${accessibility.lastAppCategory}")
            Text("最后输入事件：${timeText(accessibility.lastEventAt)}")
            Text("上次后台读取：${timeText(accessibility.lastFlushAt)}")
        }

        DebugCard("手表/模拟手表数据") {
            Text("模拟开关：${if (watchSettings.simulationEnabled) "已开启" else "未开启"}")
            Text("模拟心率：${watchSettings.simulatedHeartRate}")
            Text("模拟呼吸：${watchSettings.simulatedBreathRate}")
            Text("模拟运动干扰：${"%.2f".format(watchSettings.simulatedMotionLevel)}")
            Text("当前参与判断心率：${watchPacket?.heartRate ?: 0}")
            Text("当前参与判断呼吸：${watchPacket?.breathRate ?: 0}")
            Text("当前参与判断运动：${"%.2f".format(watchPacket?.motionLevel ?: 0f)}")
        }

        DebugCard("后台综合评估") {
            PassiveDebugText(passive)
        }

        DebugCard("提醒通道状态") {
            Text("悬浮窗权限：${if (PassiveOverlayAlert.canShow(context)) "已开启" else "未开启"}")
            Text("待 App 内弹窗：${pendingAlert?.let { "${it.title} / ${timeText(it.createdAt)}" } ?: "无"}")
            Text("通知冷却剩余：${notificationStatus.cooldownRemainingMinutes} 分钟")
            Text("今日通知次数：${notificationStatus.countToday}/${notificationStatus.maxDaily}")
            Text("通知现在可发送：${if (notificationStatus.canNotifyNow) "是" else "否"}")
        }

        DebugCard("Agent 审计日志") {
            if (logs.isEmpty()) {
                Text("暂无后端请求记录。启动联调演示或等待后台检测后会显示在这里。")
            } else {
                logs.take(8).forEach { log ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("时间：${timeText(log.requestTime)} / 触发：${log.triggerReason}")
                        Text("HTTP：${if (log.httpSuccess) "成功" else "失败或兜底"} / 状态：${log.responseEmotion}")
                        Text("评分：${"%.2f".format(log.riskScore)} / 等级：${log.riskLevel} / 置信度：${"%.2f".format(log.confidence)}")
                        Text("Prompt：${log.promptVersion.ifBlank { "未记录" }}")
                        Text("耗时：${log.latencyMs} ms / 请求摘要：${log.requestSummary.ifBlank { "未记录" }}")
                        Text("原因：${log.mainReasons.ifBlank { "无" }}")
                        Text("fallback：${if (log.fallbackUsed) log.fallbackReason ?: "已使用" else "否"} / cache：${if (log.cacheUsed) "是" else "否"}")
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

@Composable
private fun DebugCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PassiveDebugText(snapshot: PassiveDebugSnapshot) {
    Text("最后评估：${timeText(snapshot.evaluatedAt)}")
    Text("typingSpeed：${"%.1f".format(snapshot.typingSpeed)} 字/分钟")
    Text("deleteRate：${"%.2f".format(snapshot.deleteRate)}")
    Text("pauseDuration：${"%.1f".format(snapshot.pauseDuration)} 秒")
    Text("interactionRisk：${"%.2f".format(snapshot.interactionRisk)}")
    Text("heartRate：${snapshot.heartRate}")
    Text("breathRate：${snapshot.breathRate}")
    Text("motionLevel：${"%.2f".format(snapshot.motionLevel)}")
    Text("physiologyRisk：${"%.2f".format(snapshot.physiologyRisk)}")
    Text("combinedRisk：${"%.2f".format(snapshot.combinedRisk)}")
    Text("dataQuality：${snapshot.dataQualityLevel}")
    Text("lastAppCategory：${snapshot.lastAppCategory}")
    Text("是否满足弹窗条件：${if (snapshot.alertAllowed) "是" else "否"}")
    Text("原因：${snapshot.lastReason}")
}

private fun timeText(value: Long): String =
    if (value <= 0L) "无记录" else DateFormat.format("HH:mm:ss", value).toString()
