package com.neurogarden.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TherapySessionEntity::class,
        SensorRecordEntity::class,
        UserHabitBaselineEntity::class,
        HabitSampleEntity::class,
        ThresholdProfileEntity::class,
        FeedbackRecordEntity::class,
        ConversationSummaryEntity::class,
        RiskEventEntity::class,
        AgentAuditLogEntity::class,
        EmotionEvaluationRecordEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class NeuroGardenDatabase : RoomDatabase() {
    abstract fun therapyDao(): TherapyDao
    abstract fun habitDao(): HabitDao
    abstract fun riskEventDao(): RiskEventDao
    abstract fun agentAuditLogDao(): AgentAuditLogDao

    companion object {
        fun create(context: Context): NeuroGardenDatabase =
            Room.databaseBuilder(context, NeuroGardenDatabase::class.java, "neurogarden.db")
                // Demo stage accepts destructive migration for fast schema iteration.
                // Production must provide explicit migrations to preserve habit memory, risk events, and audit logs.
                .fallbackToDestructiveMigration()
                .build()
    }
}
