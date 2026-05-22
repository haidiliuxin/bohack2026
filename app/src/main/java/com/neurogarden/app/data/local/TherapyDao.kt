package com.neurogarden.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TherapyDao {
    @Insert
    suspend fun insertSession(session: TherapySessionEntity)

    @Query("SELECT * FROM therapy_sessions ORDER BY startTime DESC")
    fun observeSessions(): Flow<List<TherapySessionEntity>>

    @Insert
    suspend fun insertSensorRecord(record: SensorRecordEntity)

    @Query("SELECT * FROM sensor_records WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSensorRecordsSince(since: Long): Flow<List<SensorRecordEntity>>

    @Query("DELETE FROM sensor_records")
    suspend fun clearSensorRecords()
}
