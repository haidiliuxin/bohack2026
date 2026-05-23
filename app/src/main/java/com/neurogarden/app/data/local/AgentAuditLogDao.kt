package com.neurogarden.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentAuditLogDao {
    @Insert
    suspend fun insert(log: AgentAuditLogEntity): Long

    @Query("SELECT * FROM agent_audit_logs ORDER BY requestTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<AgentAuditLogEntity>>

    @Query("DELETE FROM agent_audit_logs")
    suspend fun deleteAll()
}
