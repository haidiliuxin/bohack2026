package com.neurogarden.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.neurogarden.app.algorithm.CareMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CareModeStore(private val context: Context) {
    val currentMode: Flow<CareMode> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[CareModeKey]?.let { stored ->
            runCatching { CareMode.valueOf(stored) }.getOrNull()
        } ?: CareMode.SELF_MONITORING
    }

    suspend fun setMode(mode: CareMode) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CareModeKey] = mode.name
        }
    }

    suspend fun currentModeSnapshot(): CareMode = currentMode.first()

    private companion object {
        val CareModeKey = stringPreferencesKey("care_mode")
    }
}
