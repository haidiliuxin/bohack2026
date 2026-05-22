package com.neurogarden.wear.data

data class BreathPatternCommand(
    val inhaleSeconds: Int,
    val exhaleSeconds: Int,
    val pattern: String
)

class PhoneCommandReceiver {
    fun onCommand(command: BreathPatternCommand) {
        // TODO: Receive breath_pattern messages and start BreathHapticController.
    }
}
