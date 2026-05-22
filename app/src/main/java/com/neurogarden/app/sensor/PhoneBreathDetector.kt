package com.neurogarden.app.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PhoneBreathDetector {
    private val _breathRate = MutableStateFlow(12)
    val breathRate: StateFlow<Int> = _breathRate

    fun updateGuidedRate(rate: Int) {
        _breathRate.value = rate
    }
}
