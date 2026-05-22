package com.neurogarden.app.wear

import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.util.JsonUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class WearDataReceiver {
    private val _packets = MutableSharedFlow<SensorPacket>()
    val packets: SharedFlow<SensorPacket> = _packets

    suspend fun onMockPacket(packet: SensorPacket) {
        _packets.emit(packet)
    }

    suspend fun onPayload(payload: String): Boolean {
        val packet = JsonUtil.parseSensorPacketJson(payload) ?: return false
        _packets.emit(packet)
        return true
    }

    // TODO: Implement DataClient.OnDataChangedListener for real Wearable Data Layer packets.
}
