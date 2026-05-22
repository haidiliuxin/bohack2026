package com.neurogarden.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    connectionStatus: String,
    onConnectWear: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onContinueMock: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设备连接", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("手表连接状态：$connectionStatus")
                Text("心率权限：用于识别相对个人基线的偏离")
                Text("身体运动状态：用于降低运动干扰误判")
                Text("打字节奏特征：仅记录速度、删除频率和停顿时长")
                Text("摄像头：不使用")
            }
        }
        Text("为了建立你的个人节奏基线，系统会在本地保存必要的特征值。不会采集前置摄像头画面，不上传原始音频或完整隐私文本，所有守护提醒都需要你提前授权。")
        Button(onClick = onConnectWear, modifier = Modifier.fillMaxWidth()) {
            Text("连接 Wear OS 手表")
        }
        OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
            Text("打开系统蓝牙配对")
        }
        OutlinedButton(onClick = onContinueMock, modifier = Modifier.fillMaxWidth()) {
            Text("继续模拟体验")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回首页")
        }
    }
}
