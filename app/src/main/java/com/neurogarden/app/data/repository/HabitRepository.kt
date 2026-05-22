package com.neurogarden.app.data.repository

import com.neurogarden.app.data.local.HabitDao
import com.neurogarden.app.data.local.HabitSampleEntity
import com.neurogarden.app.data.local.FeedbackRecordEntity
import com.neurogarden.app.data.local.ConversationSummaryEntity
import com.neurogarden.app.data.local.ThresholdProfileEntity
import com.neurogarden.app.data.local.UserHabitBaselineEntity

class HabitRepository(private val dao: HabitDao) {
    val samples = dao.observeSamples()
    val latestBaseline = dao.observeLatestBaseline()
    val latestThresholdProfile = dao.observeLatestThresholdProfile()
    val feedbackRecords = dao.observeFeedbackRecords()

    suspend fun saveSample(sample: HabitSampleEntity): Long = dao.insertSample(sample)

    suspend fun getRecentSamples(limit: Int = 30): List<HabitSampleEntity> =
        dao.getRecentSamples(limit)

    suspend fun getSamplesSince(since: Long): List<HabitSampleEntity> =
        dao.getSamplesSince(since)

    fun observeSamplesSince(since: Long) = dao.observeSamplesSince(since)

    suspend fun saveBaseline(baseline: UserHabitBaselineEntity): Long =
        dao.insertBaseline(baseline)

    suspend fun getLatestBaseline(): UserHabitBaselineEntity? =
        dao.getLatestBaseline()

    suspend fun saveThresholdProfile(profile: ThresholdProfileEntity): Long =
        dao.insertThresholdProfile(profile)

    suspend fun getLatestThresholdProfile(): ThresholdProfileEntity? =
        dao.getLatestThresholdProfile()

    suspend fun saveFeedback(record: FeedbackRecordEntity): Long =
        dao.insertFeedback(record)

    suspend fun getRecentFeedbackRecords(limit: Int = 30): List<FeedbackRecordEntity> =
        dao.getRecentFeedbackRecords(limit)

    suspend fun getFeedbackAccuracySummary(): FeedbackAccuracySummary {
        val total = dao.getFeedbackCount()
        val helpful = dao.getHelpfulFeedbackCount()
        return FeedbackAccuracySummary(
            total = total,
            helpful = helpful,
            helpfulRate = if (total == 0) 0f else helpful.toFloat() / total.toFloat()
        )
    }

    suspend fun saveConversationSummary(summary: ConversationSummaryEntity): Long =
        dao.insertConversationSummary(summary)

    suspend fun getRecentConversationSummaries(limit: Int = 10): List<ConversationSummaryEntity> =
        dao.getRecentConversationSummaries(limit)

    suspend fun clearHabitMemory() {
        dao.clearSamples()
        dao.clearBaselines()
        dao.clearThresholdProfiles()
        dao.clearFeedbackRecords()
        dao.clearConversationSummaries()
    }
}

data class FeedbackAccuracySummary(
    val total: Int,
    val helpful: Int,
    val helpfulRate: Float
)
