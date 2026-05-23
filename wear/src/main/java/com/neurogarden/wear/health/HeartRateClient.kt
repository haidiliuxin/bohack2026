package com.neurogarden.wear.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

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

class RealHeartRateClient(context: Context) : HeartRateClient {
    private val appContext = context.applicationContext
    private val measureClient: MeasureClient by lazy {
        HealthServices.getClient(appContext).measureClient
    }
    private val _heartRateFlow = MutableStateFlow(0)
    override val heartRateFlow: Flow<Int> = _heartRateFlow.asStateFlow()
    private val _statusFlow = MutableStateFlow("等待 Health Services 权限")
    override val statusFlow: Flow<String> = _statusFlow.asStateFlow()
    private var registered = false
    private val callback = object : MeasureCallback {
        override fun onRegistered() {
            registered = true
            _statusFlow.value = "Health Services 心率监听已启动"
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            registered = false
            _statusFlow.value = "真实心率注册失败：${throwable.message ?: "未知错误"}"
        }

        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM) {
                _statusFlow.value = when (availability) {
                    DataTypeAvailability.AVAILABLE -> "真实心率可用"
                    DataTypeAvailability.ACQUIRING -> "正在获取真实心率"
                    DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "手表未贴合，心率不可用"
                    DataTypeAvailability.UNAVAILABLE -> "真实心率暂不可用"
                    else -> "真实心率状态：$availability"
                }
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val latest = data.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
                ?.value
                ?.roundToInt()
                ?.takeIf { it in 35..220 }
                ?: return
            _heartRateFlow.value = latest
            _statusFlow.value = "真实心率：$latest BPM"
        }
    }

    override suspend fun start() {
        _statusFlow.value = "检查 Health Services 心率能力"
        val capabilities = runCatching { measureClient.getCapabilitiesAsync().await() }
            .getOrElse {
                _statusFlow.value = "Health Services 不可用：${it.message ?: "无法读取能力"}"
                return
            }
        if (DataType.HEART_RATE_BPM !in capabilities.supportedDataTypesMeasure) {
            _statusFlow.value = "当前设备不支持 Health Services 心率"
            return
        }
        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        }.onFailure {
            registered = false
            _statusFlow.value = "真实心率启动失败：${it.message ?: "未知错误"}"
        }
    }

    override suspend fun stop() {
        if (registered) {
            runCatching {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback).await()
            }
        }
        registered = false
        _statusFlow.value = "真实心率监听已停止"
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                runCatching { get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            { command -> command.run() }
        )
        continuation.invokeOnCancellation { cancel(true) }
    }
