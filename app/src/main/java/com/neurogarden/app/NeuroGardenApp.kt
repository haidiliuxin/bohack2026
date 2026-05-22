package com.neurogarden.app

import android.app.Application
import com.neurogarden.app.agent.GuardianAgentFallback
import com.neurogarden.app.agent.MockGuardianAgentApi
import com.neurogarden.app.agent.RealGuardianAgentApi
import com.neurogarden.app.data.datastore.CareModeStore
import com.neurogarden.app.data.local.NeuroGardenDatabase
import com.neurogarden.app.data.repository.HabitRepository
import com.neurogarden.app.data.repository.RiskEventRepository
import com.neurogarden.app.data.repository.TherapyRepository
import com.neurogarden.app.data.repository.WeatherRepository

class NeuroGardenApp : Application() {
    val database by lazy { NeuroGardenDatabase.create(this) }
    val repository by lazy { TherapyRepository(database.therapyDao()) }
    val habitRepository by lazy { HabitRepository(database.habitDao()) }
    val riskEventRepository by lazy { RiskEventRepository(database.riskEventDao()) }
    val weatherRepository by lazy { WeatherRepository(this) }
    val careModeStore by lazy { CareModeStore(this) }
    val guardianAgentApi by lazy { GuardianAgentFallback(RealGuardianAgentApi(), MockGuardianAgentApi()) }
}
