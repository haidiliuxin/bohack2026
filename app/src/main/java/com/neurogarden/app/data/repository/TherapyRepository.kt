package com.neurogarden.app.data.repository

import com.neurogarden.app.data.local.SensorRecordEntity
import com.neurogarden.app.data.local.TherapyDao
import com.neurogarden.app.data.local.TherapySessionEntity

class TherapyRepository(private val dao: TherapyDao) {
    val sessions = dao.observeSessions()

    suspend fun saveSession(session: TherapySessionEntity) = dao.insertSession(session)
    suspend fun saveSensorRecord(record: SensorRecordEntity) = dao.insertSensorRecord(record)
    fun observeSensorRecordsSince(since: Long) = dao.observeSensorRecordsSince(since)
    suspend fun clearSensorRecords() = dao.clearSensorRecords()
}
