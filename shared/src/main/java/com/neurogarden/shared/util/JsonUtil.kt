package com.neurogarden.shared.util

import com.neurogarden.shared.model.SensorPacket

object JsonUtil {
    fun sensorPacketJson(packet: SensorPacket): String =
        """{"type":"sensor_packet","heartRate":${packet.heartRate},"breathRate":${packet.breathRate},"heartRateWave":${packet.heartRateWave},"motionLevel":${packet.motionLevel},"timestamp":${packet.timestamp}}"""

    fun parseSensorPacketJson(json: String): SensorPacket? {
        if (!json.contains("\"type\":\"sensor_packet\"")) return null
        return runCatching {
            SensorPacket(
                heartRate = json.intValue("heartRate"),
                breathRate = json.intValue("breathRate"),
                heartRateWave = json.floatValue("heartRateWave", default = 4f),
                motionLevel = json.floatValue("motionLevel"),
                timestamp = json.longValue("timestamp")
            )
        }.getOrNull()
    }

    private fun String.intValue(name: String): Int =
        Regex(""""$name"\s*:\s*([0-9]+)""").find(this)?.groupValues?.get(1)?.toInt()
            ?: error("Missing $name")

    private fun String.longValue(name: String): Long =
        Regex(""""$name"\s*:\s*([0-9]+)""").find(this)?.groupValues?.get(1)?.toLong()
            ?: error("Missing $name")

    private fun String.floatValue(name: String, default: Float? = null): Float =
        Regex(""""$name"\s*:\s*([0-9.]+)""").find(this)?.groupValues?.get(1)?.toFloat()
            ?: default
            ?: error("Missing $name")
}
