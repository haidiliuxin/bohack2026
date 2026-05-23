package com.neurogarden.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.neurogarden.app.passive.WatchSignalStore
import com.neurogarden.shared.util.JsonUtil
import com.neurogarden.shared.wear.WearPaths

class WearSensorListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearPaths.SENSOR_PACKET) return@forEach
            val payload = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearPaths.PAYLOAD)
            val packet = payload?.let { JsonUtil.parseSensorPacketJson(it) } ?: return@forEach
            WatchSignalStore.saveRealPacket(this, packet)
        }
    }
}
