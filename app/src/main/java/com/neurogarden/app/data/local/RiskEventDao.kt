package com.neurogarden.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskEventDao {
    @Insert
    suspend fun insertRiskEvent(event: RiskEventEntity): Long

    @Query(
        """
        SELECT * FROM risk_events
        WHERE startTime >= :dayStart AND startTime < :dayEnd
        ORDER BY startTime DESC
        """
    )
    fun observeTodayRiskEvents(dayStart: Long, dayEnd: Long): Flow<List<RiskEventEntity>>

    @Query(
        """
        SELECT * FROM risk_events
        WHERE startTime >= :since
        ORDER BY startTime DESC
        """
    )
    fun observeRecentRiskEvents(since: Long): Flow<List<RiskEventEntity>>

    @Query("SELECT * FROM risk_events WHERE id = :id LIMIT 1")
    fun observeRiskEventById(id: Long): Flow<RiskEventEntity?>

    @Query("SELECT * FROM risk_events WHERE id = :id LIMIT 1")
    suspend fun getRiskEventById(id: Long): RiskEventEntity?

    @Query(
        """
        SELECT * FROM risk_events
        WHERE startTime >= :since AND riskLevel = :riskLevel
        ORDER BY startTime DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSimilarCandidate(since: Long, riskLevel: String): RiskEventEntity?

    @Query(
        """
        UPDATE risk_events
        SET endTime = :endTime,
            riskScore = :riskScore,
            confidence = :confidence,
            mainReasons = :mainReasons,
            metricDeviationPercent = :metricDeviationPercent,
            agentAnalysis = :agentAnalysis,
            suggestedAction = :suggestedAction,
            guardianNotified = :guardianNotified
        WHERE id = :id
        """
    )
    suspend fun mergeRiskEvent(
        id: Long,
        endTime: Long,
        riskScore: Float,
        confidence: Float,
        mainReasons: String,
        metricDeviationPercent: String,
        agentAnalysis: String,
        suggestedAction: String,
        guardianNotified: Boolean
    )

    @Query(
        """
        UPDATE risk_events
        SET guardianFeedback = :feedback,
            isFalseAlarm = :isFalseAlarm
        WHERE id = :id
        """
    )
    suspend fun updateGuardianFeedback(
        id: Long,
        feedback: String,
        isFalseAlarm: Boolean
    )

    @Query(
        """
        UPDATE risk_events
        SET guardianFeedback = :feedback,
            isFalseAlarm = 1
        WHERE id = :id
        """
    )
    suspend fun markFalseAlarm(id: Long, feedback: String = "标记误报")

    @Query("DELETE FROM risk_events")
    suspend fun deleteAllRiskEvents()
}
