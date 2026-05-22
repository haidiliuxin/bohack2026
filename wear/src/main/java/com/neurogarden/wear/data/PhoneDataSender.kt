package com.neurogarden.wear.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.util.JsonUtil
import com.neurogarden.shared.wear.WearPaths
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PhoneDataSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun send(packet: SensorPacket): Boolean {
        val request = PutDataMapRequest.create(WearPaths.SENSOR_PACKET).apply {
            dataMap.putString(WearPaths.PAYLOAD, JsonUtil.sensorPacketJson(packet))
            dataMap.putLong("sentAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        return suspendCoroutine { continuation ->
            dataClient.putDataItem(request)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { continuation.resume(false) }
        }
    }

    suspend fun sendPassivePayload(
        heartRate: Int,
        breathRate: Int,
        motionLevel: Float,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean =
        send(
            SensorPacket(
                heartRate = heartRate,
                breathRate = breathRate,
                heartRateWave = 4f,
                motionLevel = motionLevel,
                timestamp = timestamp
            )
        )
}
