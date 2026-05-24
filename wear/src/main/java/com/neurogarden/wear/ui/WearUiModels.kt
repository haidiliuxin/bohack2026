package com.neurogarden.wear.ui

data class WatchVitalUiState(
    val heartRate: Int = 86,
    val breathRate: Int = 14,
    val motionLevel: Float = 0.10f,
    val motionLabel: String = "低",
    val riskState: WatchRiskState = WatchRiskState.STABLE,
    val statusLabel: String = "稳定",
    val confidence: Float = 0.62f,
    val dataQuality: WatchDataQuality = WatchDataQuality.MEDIUM,
    val heartRateSource: MeasurementSource = MeasurementSource.MOCK,
    val breathRateSource: MeasurementSource = MeasurementSource.ESTIMATED,
    val motionSource: MeasurementSource = MeasurementSource.MOCK,
    val phoneConnected: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastCommandText: String = "未收到手机指令",
    val observedClues: List<String> = emptyList(),
    val counterEvidence: List<String> = emptyList(),
    val uncertainty: String = "仅基于手表端结构化体征估算"
)

enum class WatchRiskState(val label: String) {
    STABLE("稳定"),
    OBSERVE("观察"),
    ALERT("提醒")
}

enum class MeasurementSource(val label: String) {
    REAL("真实"),
    MOCK("模拟"),
    ESTIMATED("估算"),
    UNAVAILABLE("不可用")
}

enum class WatchDataQuality(val label: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高")
}
