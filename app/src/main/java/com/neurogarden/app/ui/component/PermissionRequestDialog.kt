package com.neurogarden.app.ui.component

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * 权限说明数据类
 */
data class PermissionInfo(
    val permission: String,
    val title: String,
    val description: String,
    val isRequired: Boolean = true
)

/**
 * 权限申请弹窗组件
 * 首次启动时显示，说明应用需要的权限
 */
@Composable
fun PermissionRequestDialog(
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current

    // 需要申请的权限列表
    val permissionsToRequest = remember {
        buildList {
            // 通知权限 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionInfo(
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        title = "通知权限",
                        description = "用于发送状态提醒、守护确认和照护提醒。",
                        isRequired = false
                    )
                )
            }
            // 蓝牙连接权限 (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    PermissionInfo(
                        permission = Manifest.permission.BLUETOOTH_CONNECT,
                        title = "蓝牙连接权限",
                        description = "用于连接 Wear OS 手表，同步心率和运动数据。",
                        isRequired = false
                    )
                )
            }
            // 悬浮窗权限
            add(
                PermissionInfo(
                    permission = "SYSTEM_ALERT_WINDOW",
                    title = "悬浮窗权限",
                    description = "用于在屏幕上显示守护提醒弹窗。可以在设置中手动开启。",
                    isRequired = false
                )
            )
            // 无障碍权限
            add(
                PermissionInfo(
                    permission = "ACCESSIBILITY_SERVICE",
                    title = "无障碍权限（可选）",
                    description = "用于采集打字节奏特征（速度、删除率、停顿时长），仅用于统计特征，不保存输入内容。",
                    isRequired = false
                )
            )
        }
    }

    // 检查每个权限的状态
    var permissionStates by remember { mutableStateOf(mapOf<String, Boolean>()) }

    LaunchedEffect(permissionsToRequest) {
        permissionStates = permissionsToRequest.associate { permInfo ->
            val granted = when (permInfo.permission) {
                "SYSTEM_ALERT_WINDOW" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else true
                }
                "ACCESSIBILITY_SERVICE" -> {
                    // 检查无障碍服务是否启用
                    val enabledServices = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
                    enabledServices?.contains(context.packageName) == true
                }
                else -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        permInfo.permission
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            permInfo.permission to granted
        }
    }

    // 是否所有必要权限都已授权
    val allRequiredGranted = permissionStates.filter { (_, granted) -> granted }.keys.size ==
            permissionsToRequest.filter { it.isRequired }.size

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "欢迎使用 NeuroGarden",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "为了提供更好的守护服务，需要以下权限：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                permissionsToRequest.forEach { permInfo ->
                    val isGranted = permissionStates[permInfo.permission] == true
                    PermissionCard(
                        permissionInfo = permInfo,
                        isGranted = isGranted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "隐私说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 不保存用户输入的原始文字内容\n" +
                                    "• 不上传任何聊天记录或消息\n" +
                                    "• 只采集打字速度、删除率等统计特征\n" +
                                    "• 所有数据仅本地处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRequestPermissions) {
                Text("授权必要权限")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        }
    )
}

@Composable
private fun PermissionCard(
    permissionInfo: PermissionInfo,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permissionInfo.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = permissionInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(
                checked = isGranted,
                onCheckedChange = null,
                enabled = false
            )
        }
    }
}

/**
 * 权限状态检查工具
 */
object PermissionChecker {

    fun hasNotificationPermission(context: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBluetoothPermission(context: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasOverlayPermission(context: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasAccessibilityPermission(context: android.content.Context): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(context.packageName) == true
    }

    /**
     * 获取需要申请的权限列表
     */
    fun getRequiredPermissions(context: android.content.Context): List<String> {
        val permissions = mutableListOf<String>()

        if (!hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!hasBluetoothPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.toList()
    }
}
