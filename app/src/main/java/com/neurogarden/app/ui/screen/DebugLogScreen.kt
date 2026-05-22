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
import com.neurogarden.app.passive.AccessibilitySignalStore
import com.neurogarden.app.passive.PassiveDebugSnapshot
import com.neurogarden.app.passive.PassiveDebugStore
import com.neurogarden.app.passive.WatchSignalStore
import kotlinx.coroutines.delay

@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var passive by remember { mutableStateOf(PassiveDebugStore.read(context)) }
    var accessibility by remember { mutableStateOf(AccessibilitySignalStore.debugSnapshot(context)) }
    var watchSettings by remember { mutableStateOf(WatchSignalStore.readSettings(context)) }
    var watchPacket by remember { mutableStateOf(WatchSignalStore.currentPacket(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            passive = PassiveDebugStore.read(context)
            accessibility = AccessibilitySignalStore.debugSnapshot(context)
            watchSettings = WatchSignalStore.readSettings(context)
            watchPacket = WatchSignalStore.currentPacket(context)
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
        Text("这个页面每秒刷新，用来确认无障碍输入、模拟手表数据和后台综合判断是否闭环。")

        DebugCard("无障碍输入事件") {
            Text("当前窗口 typedCount：${accessibility.typedCount}")
            Text("当前窗口 deleteCount：${accessibility.deleteCount}")
            Text("最后 delta：${accessibility.lastDelta}")
            Text("最后事件类型：${accessibility.lastEventType}")
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
    Text("是否满足弹窗条件：${if (snapshot.alertAllowed) "是" else "否"}")
    Text("原因：${snapshot.lastReason}")
}

private fun timeText(value: Long): String =
    if (value <= 0L) "无记录" else DateFormat.format("HH:mm:ss", value).toString()
