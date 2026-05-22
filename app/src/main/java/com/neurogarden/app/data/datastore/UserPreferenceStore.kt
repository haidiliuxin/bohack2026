package com.neurogarden.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.userPreferencesDataStore by preferencesDataStore("user_preferences")

object UserPreferenceStore {
    val BaselineHeartRate = intPreferencesKey("baseline_heart_rate")
}
