package com.neurogarden.wear.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface HeartRateClient {
    val heartRateFlow: Flow<Int>
    val statusFlow: Flow<String>
    suspend fun start()
    suspend fun stop()
}

class MockHeartRateClient : HeartRateClient {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _heartRateFlow = MutableStateFlow(86)
    override val heartRateFlow: Flow<Int> = _heartRateFlow.asStateFlow()
    private val _statusFlow = MutableStateFlow("Mock 心率")
    override val statusFlow: Flow<String> = _statusFlow.asStateFlow()
    private var job: Job? = null

    override suspend fun start() {
        job?.cancel()
        job = scope.launch {
            var value = 96
            while (true) {
                _heartRateFlow.value = value
                value = if (value <= 78) 96 else value - 1
                delay(1200)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
    }

    fun stopSync() {
        job?.cancel()
    }
}

class RealHeartRateClient : HeartRateClient {
    private val _heartRateFlow = MutableStateFlow(0)
    override val heartRateFlow: Flow<Int> = _heartRateFlow.asStateFlow()
    private val _statusFlow = MutableStateFlow("等待 Health Services 权限")
    override val statusFlow: Flow<String> = _statusFlow.asStateFlow()

    override suspend fun start() {
        _statusFlow.value = "真实心率接入预留：需要 Health Services MeasureClient"
        // TODO: Connect Health Services MeasureClient and subscribe to DataType.HEART_RATE_BPM.
        // TODO: When passive monitoring is enabled, forward SensorPacket with PhoneDataSender.
    }

    override suspend fun stop() {
        _statusFlow.value = "真实心率监听已停止"
        // TODO: Remove Health Services heart-rate callback.
    }
}
