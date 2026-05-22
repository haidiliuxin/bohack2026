package com.neurogarden.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TherapyControlPanel(
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = if (isRunning) onPause else onStart) {
            Text(if (isRunning) "暂停" else "开始")
        }
        OutlinedButton(onClick = onFinish) {
            Text("结束")
        }
    }
}
