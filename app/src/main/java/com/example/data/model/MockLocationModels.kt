package com.example.data.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 35.0,
    val accuracyMeters: Float = 1.0f
)

sealed class MockStatus {
    data object Idle : MockStatus()
    data object Active : MockStatus()
    data class Error(
        val message: String,
        val requiresDevOptions: Boolean = false
    ) : MockStatus()
}

data class MockEngineState(
    val status: MockStatus = MockStatus.Idle,
    val activeCoordinate: GeoCoordinate? = null,
    val updatesPushed: Long = 0L,
    val lastPushTimestampMs: Long = 0L,
    val gpsProviderActive: Boolean = false,
    val networkProviderActive: Boolean = false,
    val fusedProviderActive: Boolean = false,
    val verifiedDriftMeters: Double? = null,
    val lastFusedError: String? = null
)

data class MockSettings(
    val updateIntervalMs: Long = 500L,
    val defaultAltitudeMeters: Double = 35.0,
    val defaultAccuracyMeters: Float = 1.0f,
    val darkMapTiles: Boolean = false,
    val crosshairLockEnabled: Boolean = false,
    val showJoystick: Boolean = false
)

data class GeocodingResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = ""
)
