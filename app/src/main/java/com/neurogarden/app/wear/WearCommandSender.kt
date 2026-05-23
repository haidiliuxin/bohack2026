package com.neurogarden.app.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.neurogarden.shared.wear.WearPaths
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class WearConnectionResult(
    val connected: Boolean,
    val nodeCount: Int,
    val message: String
)

class WearCommandSender(context: Context) {
    private val nodeClient: NodeClient = Wearable.getNodeClient(context.applicationContext)
    private val messageClient: MessageClient = Wearable.getMessageClient(context.applicationContext)

    suspend fun connectAndStartMonitoring(): WearConnectionResult {
        val nodes = runCatching { connectedNodes() }.getOrElse {
            return WearConnectionResult(
                connected = false,
                nodeCount = 0,
                message = "无法读取手表连接状态。请允许附近设备/蓝牙权限，或先在系统蓝牙中完成手表配对。"
            )
        }
        if (nodes.isEmpty()) {
            return WearConnectionResult(
                connected = false,
                nodeCount = 0,
                message = "没有发现已连接的 Wear OS 手表。请先打开系统蓝牙配对，确认手表已配对并安装手表端 NeuroGarden。"
            )
        }

        var sent = 0
        nodes.forEach { node ->
            if (sendStartMessage(node.id)) sent += 1
        }
        return WearConnectionResult(
            connected = sent > 0,
            nodeCount = nodes.size,
            message = if (sent > 0) {
                "已发现 ${nodes.size} 台手表，并发送开始监测命令。请打开手表端 NeuroGarden 后点“开始检测”。"
            } else {
                "发现手表，但发送开始监测命令失败。请打开手表端 NeuroGarden 后重试。"
            }
        )
    }

    suspend fun sendBreathPattern(inhaleSeconds: Int, exhaleSeconds: Int, pattern: String): WearConnectionResult {
        val nodes = runCatching { connectedNodes() }.getOrElse {
            return WearConnectionResult(false, 0, "无法读取手表连接状态，请检查蓝牙和附近设备权限。")
        }
        if (nodes.isEmpty()) {
            return WearConnectionResult(false, 0, "没有发现已连接的 Wear OS 手表。")
        }
        val payload = JSONObject()
            .put("type", "breath_pattern")
            .put("inhaleSeconds", inhaleSeconds)
            .put("exhaleSeconds", exhaleSeconds)
            .put("pattern", pattern)
            .toString()
            .toByteArray()
        var sent = 0
        nodes.forEach { node ->
            if (sendMessage(node.id, WearPaths.BREATH_PATTERN, payload)) sent += 1
        }
        return WearConnectionResult(
            connected = sent > 0,
            nodeCount = nodes.size,
            message = if (sent > 0) {
                "已向 $sent 台手表发送呼吸节奏：${inhaleSeconds}s / ${exhaleSeconds}s。"
            } else {
                "发现手表，但呼吸节奏发送失败，请打开手表端 NeuroGarden 后重试。"
            }
        )
    }

    private suspend fun connectedNodes() = suspendCoroutine { continuation ->
        nodeClient.connectedNodes
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(emptyList()) }
    }

    private suspend fun sendStartMessage(nodeId: String): Boolean = suspendCoroutine { continuation ->
        messageClient.sendMessage(nodeId, WearPaths.START_MONITORING, ByteArray(0))
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { continuation.resume(false) }
    }

    private suspend fun sendMessage(nodeId: String, path: String, payload: ByteArray): Boolean = suspendCoroutine { continuation ->
        messageClient.sendMessage(nodeId, path, payload)
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { continuation.resume(false) }
    }
}
