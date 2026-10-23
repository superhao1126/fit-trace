package com.fittrace.core.model

enum class BiometricCapability {
    AVAILABLE,
    NOT_ENROLLED,
    UNSUPPORTED,
    TEMPORARILY_UNAVAILABLE,
}

data class DeviceBinding(
    val id: String,
    val userId: String,
    val email: String,
    val alias: String,
    val pending: Boolean = false,
)

data class BiometricState(
    val capability: BiometricCapability,
    val binding: DeviceBinding? = null,
    val enabled: Boolean = false,
    val invalidated: Boolean = false,
)
