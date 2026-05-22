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
        ConversationSummaryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NeuroGardenDatabase : RoomDatabase() {
    abstract fun therapyDao(): TherapyDao
    abstract fun habitDao(): HabitDao

    companion object {
        fun create(context: Context): NeuroGardenDatabase =
            Room.databaseBuilder(context, NeuroGardenDatabase::class.java, "neurogarden.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
