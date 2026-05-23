package com.neurogarden.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insertSample(sample: HabitSampleEntity): Long

    @Query("SELECT * FROM habit_samples ORDER BY timestamp DESC")
    fun observeSamples(): Flow<List<HabitSampleEntity>>

    @Query("SELECT * FROM habit_samples WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSamplesSince(since: Long): List<HabitSampleEntity>

    @Query("SELECT * FROM habit_samples WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSamplesSince(since: Long): Flow<List<HabitSampleEntity>>

    @Query("SELECT * FROM habit_samples ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSamples(limit: Int): List<HabitSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: UserHabitBaselineEntity): Long

    @Query("SELECT * FROM user_habit_baselines ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestBaseline(): Flow<UserHabitBaselineEntity?>

    @Query("SELECT * FROM user_habit_baselines ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestBaseline(): UserHabitBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThresholdProfile(profile: ThresholdProfileEntity): Long

    @Query("SELECT * FROM threshold_profiles ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestThresholdProfile(): Flow<ThresholdProfileEntity?>

    @Query("SELECT * FROM threshold_profiles ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentThresholdProfiles(limit: Int = 12): Flow<List<ThresholdProfileEntity>>

    @Query("SELECT * FROM threshold_profiles ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestThresholdProfile(): ThresholdProfileEntity?

    @Insert
    suspend fun insertFeedback(record: FeedbackRecordEntity): Long

    @Query("SELECT * FROM feedback_records ORDER BY timestamp DESC")
    fun observeFeedbackRecords(): Flow<List<FeedbackRecordEntity>>

    @Query("SELECT * FROM feedback_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFeedbackRecords(limit: Int): List<FeedbackRecordEntity>

    @Query("SELECT COUNT(*) FROM feedback_records")
    suspend fun getFeedbackCount(): Int

    @Query("SELECT COUNT(*) FROM feedback_records WHERE helpful = 1")
    suspend fun getHelpfulFeedbackCount(): Int

    @Query("DELETE FROM habit_samples")
    suspend fun clearSamples()

    @Query("DELETE FROM user_habit_baselines")
    suspend fun clearBaselines()

    @Query("DELETE FROM threshold_profiles")
    suspend fun clearThresholdProfiles()

    @Query("DELETE FROM feedback_records")
    suspend fun clearFeedbackRecords()

    @Insert
    suspend fun insertConversationSummary(summary: ConversationSummaryEntity): Long

    @Query("SELECT * FROM conversation_summaries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversationSummaries(limit: Int): List<ConversationSummaryEntity>

    @Query("DELETE FROM conversation_summaries")
    suspend fun clearConversationSummaries()
}
