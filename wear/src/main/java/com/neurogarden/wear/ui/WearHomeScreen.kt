package com.neurogarden.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun WearHomeScreen(
    heartRate: Int?,
    status: String,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF071316)).padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NeuroGarden", style = MaterialTheme.typography.title2)
        Text("当前心率：${heartRate?.toString() ?: "--"} BPM")
        Text("状态：$status")
        Text("点击后会尝试采集并同步到手机")
        Text("失败时请检查蓝牙、配对和权限")
        Button(onClick = onStart) {
            Text("采集并同步")
        }
    }
}
