package com.neurogarden.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neurogarden.app.algorithm.StressCalculator
import com.neurogarden.app.algorithm.TherapyPlanGenerator
import com.neurogarden.app.data.local.TherapySessionEntity
import com.neurogarden.app.data.repository.TherapyRepository
import com.neurogarden.shared.model.SensorPacket
import com.neurogarden.shared.model.StressResult
import com.neurogarden.shared.model.TherapyPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TherapyUiState(
    val startPacket: SensorPacket = SensorPacket(102, breathRate = 21, heartRateWave = 8f, motionLevel = 0.1f),
    val currentPacket: SensorPacket = startPacket,
    val result: StressResult = StressCalculator.calculate(currentPacket),
    val plan: TherapyPlan = TherapyPlanGenerator.generatePlan(result.state),
    val isRunning: Boolean = false,
    val elapsedSeconds: Int = 0,
    val saved: Boolean = false
)

class TherapyViewModel(private val repository: TherapyRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TherapyUiState())
    val uiState: StateFlow<TherapyUiState> = _uiState.asStateFlow()
    val sessions = repository.sessions
    private var therapyJob: Job? = null

    fun startFrom(packet: SensorPacket) {
        val result = StressCalculator.calculate(packet)
        _uiState.value = TherapyUiState(
            startPacket = packet,
            currentPacket = packet,
            result = result,
            plan = TherapyPlanGenerator.generatePlan(result.state),
            isRunning = true
        )
        runSimulation()
    }

    fun resume() {
        if (_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(isRunning = true)
        runSimulation()
    }

    fun pause() {
        therapyJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun finish() {
        therapyJob?.cancel()
        val state = _uiState.value
        val packet = state.startPacket.copy(heartRate = 84, breathRate = 12, heartRateWave = 3f)
        val result = StressCalculator.calculate(packet)
        _uiState.value = state.copy(
            currentPacket = packet,
            result = result,
            plan = TherapyPlanGenerator.generatePlan(result.state),
            isRunning = false,
            elapsedSeconds = maxOf(state.elapsedSeconds, 180)
        )
    }

    fun saveCurrentSession() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSession(
                TherapySessionEntity(
                    startTime = System.currentTimeMillis() - state.elapsedSeconds * 1000L,
                    endTime = System.currentTimeMillis(),
                    beforeStressScore = StressCalculator.calculate(state.startPacket).stressScore,
                    afterStressScore = state.result.stressScore,
                    beforeHeartRate = state.startPacket.heartRate,
                    afterHeartRate = state.currentPacket.heartRate,
                    beforeBreathRate = state.startPacket.breathRate,
                    afterBreathRate = state.currentPacket.breathRate,
                    therapyMode = state.plan.mode.displayName,
                    userFeedback = "模拟疗愈完成"
                )
            )
            _uiState.value = state.copy(saved = true)
        }
    }

    private fun runSimulation() {
        therapyJob?.cancel()
        therapyJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                delay(1000)
                val state = _uiState.value
                val progress = ((state.elapsedSeconds + 1) / 60f).coerceIn(0f, 1f)
                val start = state.startPacket
                val heart = lerp(start.heartRate, 84, progress)
                val breath = lerp(start.breathRate, 12, progress)
                val wave = lerpFloat(start.heartRateWave, 3f, progress)
                val packet = start.copy(heartRate = heart, breathRate = breath, heartRateWave = wave)
                val result = StressCalculator.calculate(packet)
                _uiState.value = state.copy(
                    currentPacket = packet,
                    result = result,
                    plan = TherapyPlanGenerator.generatePlan(result.state),
                    elapsedSeconds = state.elapsedSeconds + 1
                )
            }
        }
    }

    private fun lerp(start: Int, end: Int, progress: Float): Int =
        (start + (end - start) * progress).toInt()

    private fun lerpFloat(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    class Factory(private val repository: TherapyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TherapyViewModel(repository) as T
    }
}
