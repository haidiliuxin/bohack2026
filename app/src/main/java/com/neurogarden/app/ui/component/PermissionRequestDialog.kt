package com.neurogarden.app.ui.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PermissionRequestDialog(
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    NeuroAlertDialog(
        title = "开始使用 NeuroGarden",
        confirmText = "我知道了",
        dismissText = "稍后再说",
        onConfirm = {
            onRequestPermissions()
            onDismiss()
        },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "为了完成后台守护演示，目前只需要开启两个可用权限入口。",
                style = MaterialTheme.typography.bodyMedium
            )
            PermissionActionCard(
                title = "无障碍权限",
                description = "用于统计打字速度、删除频率和停顿时长，不读取输入原文。",
                action = "去开启无障碍权限",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
            PermissionActionCard(
                title = "显示在其他应用上",
                description = "用于展示悬浮提醒、守护提示或紧急状态信息。",
                action = "去开启显示在其他应用上",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            )
            Text(
                text = "系统不会保存被动采集到的输入原文，也不提供医学诊断。",
                style = MaterialTheme.typography.bodySmall,
                color = NeuroColors.TextMuted
            )
        }
    }
}

@Composable
private fun PermissionActionCard(
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit
) {
    NeuroCard(modifier = Modifier.fillMaxWidth(), containerColor = NeuroColors.CardSoft) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = NeuroColors.TextSecondary)
            NeuroPrimaryButton(action, onClick, Modifier.fillMaxWidth())
        }
    }
}

object PermissionChecker {
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(context.packageName) == true
    }

    fun getRequiredPermissions(context: Context): List<String> {
        return emptyList()
    }
}
