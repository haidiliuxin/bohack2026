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
            title = "被动守护",
            body = "启动后以前台服务运行，通知栏会持续显示。系统只在本地汇总必要特征，用于判断是否偏离你的日常节奏。"
        )
        PrivacyItem(
            title = "无障碍服务",
            body = "仅统计打字速度、删除频率和停顿时长，不保存、不展示、不上传第三方 App 的输入文本内容。你可以随时在系统设置中关闭。"
        )
        PrivacyItem(
            title = "Wear OS 手表",
            body = "手表端会向手机端发送心率、估算呼吸节奏和运动干扰等特征。手机和手表需要先在系统中完成配对。"
        )
        PrivacyItem(
            title = "AI 对话",
            body = "只有你主动在关怀对话中输入的内容，才会用于生成回复。系统会尽量保存摘要和标签，而不是保存敏感全文。"
        )
        PrivacyItem(
            title = "守护联系人",
            body = "联系人电话只用于打开系统拨号盘。应用不会后台自动拨号，也不会自动发送短信。是否拨出由你在系统拨号界面确认。"
        )
        PrivacyItem(
            title = "本地数据删除",
            body = "你可以在守护模式页面清除本地习惯记忆，包括样本、基线、阈值和反馈记录。清除后系统会重新学习。"
        )
        PrivacyItem(
            title = "边界",
            body = "NeuroGarden 不做医学诊断，不预测极端行为，不使用前置摄像头。所有提醒都以状态偏离和关怀建议表达。"
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
