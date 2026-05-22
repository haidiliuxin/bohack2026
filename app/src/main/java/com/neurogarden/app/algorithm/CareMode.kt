package com.neurogarden.app.algorithm

enum class CareMode {
    SELF_MONITORING,
    FAMILY_GUARDIAN,
    SPECIAL_CARE
}

data class CareModePolicy(
    val mode: CareMode,
    val riskSensitivity: Float,
    val guardianEnabledByDefault: Boolean,
    val notificationThreshold: Float,
    val maxDailyGuardianAlerts: Int,
    val requireGuardianFeedback: Boolean,
    val privacyLevel: String,
    val riskLabelStyle: String
)

object CareModePolicies {
    fun policyFor(mode: CareMode): CareModePolicy = when (mode) {
        CareMode.SELF_MONITORING -> CareModePolicy(
            mode = mode,
            riskSensitivity = 0.88f,
            guardianEnabledByDefault = false,
            notificationThreshold = 0.86f,
            maxDailyGuardianAlerts = 0,
            requireGuardianFeedback = false,
            privacyLevel = "local_private",
            riskLabelStyle = "gentle_self"
        )

        CareMode.FAMILY_GUARDIAN -> CareModePolicy(
            mode = mode,
            riskSensitivity = 1.00f,
            guardianEnabledByDefault = true,
            notificationThreshold = 0.78f,
            maxDailyGuardianAlerts = 3,
            requireGuardianFeedback = true,
            privacyLevel = "guardian_authorized",
            riskLabelStyle = "guardian_check"
        )

        CareMode.SPECIAL_CARE -> CareModePolicy(
            mode = mode,
            riskSensitivity = 1.14f,
            guardianEnabledByDefault = true,
            notificationThreshold = 0.68f,
            maxDailyGuardianAlerts = 2,
            requireGuardianFeedback = true,
            privacyLevel = "strict_care",
            riskLabelStyle = "care_deviation"
        )
    }
}
