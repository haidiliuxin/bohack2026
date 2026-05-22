package com.neurogarden.app.ui.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyGuideScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("隐私与权限说明", style = MaterialTheme.typography.headlineMedium)
        PrivacyItem(
            title = "产品边界",
            body = "NeuroGarden 不提供医疗诊断，也不能替代医生、心理咨询师或专业机构。系统仅根据用户授权采集的结构化数据，辅助观察当前状态是否持续偏离个人日常节奏，并在需要时提供本地提醒或授权守护确认。"
        )
        PrivacyItem(
            title = "不会保存的数据",
            body = "不保存用户输入原文、聊天内容、密码内容、原始文本或完整敏感对话。被动采集不会读取具体语义文本。"
        )
        PrivacyItem(
            title = "会保存的数据",
            body = "在用户授权后，仅保存心率、呼吸频率、运动状态、打字速度、删除频率、停顿时长、天气、时间段、风险评分、异常事件摘要和监护人反馈。"
        )
        PrivacyItem(
            title = "健康数据权限",
            body = "用于读取或接收心率、呼吸、运动状态和 Wear OS 数据，帮助判断状态是否相对个人基线发生持续偏离。"
        )
        PrivacyItem(
            title = "无障碍服务权限",
            body = "仅用于统计打字速度、删除频率、停顿时长和输入节奏变化，不用于读取用户输入内容。你可以随时在系统设置中关闭。"
        )
        PrivacyItem(
            title = "通知与网络权限",
            body = "通知用于本地状态波动提醒、守护确认提醒和照护确认提醒。网络用于天气数据、Agent API 和可选远程分析；网络失败时会使用本地兜底逻辑。"
        )
        PrivacyItem(
            title = "Agent 数据边界",
            body = "Agent 默认只接收指标偏离百分比、风险等级、数据可信度、时间段、天气和反馈摘要。主动关怀对话只处理用户主动输入的当前对话内容，不接收被动采集的聊天原文。"
        )
        PrivacyItem(
            title = "本地删除",
            body = "你可以在设置页清除本地习惯记忆，包括样本、基线、阈值、风险事件和反馈记录。清除后系统会重新学习。"
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

@Composable
private fun PrivacyItem(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
