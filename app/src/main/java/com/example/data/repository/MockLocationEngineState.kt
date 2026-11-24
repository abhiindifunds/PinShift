package com.example.data.repository

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.example.data.model.GeoCoordinate
import com.example.data.model.MockEngineState
import com.example.data.model.MockStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MockLocationEngineState {
    private val _state = MutableStateFlow(MockEngineState())
    val state: StateFlow<MockEngineState> = _state.asStateFlow()

    fun setRunning(
        coordinate: GeoCoordinate,
        gpsActive: Boolean,
        networkActive: Boolean,
        fusedActive: Boolean,
        verifiedDriftMeters: Double? = null,
        lastFusedError: String? = null
    ) {
        _state.update { current ->
            current.copy(
                status = MockStatus.Active,
                activeCoordinate = coordinate,
                updatesPushed = current.updatesPushed + 1L,
                lastPushTimestampMs = System.currentTimeMillis(),
                gpsProviderActive = gpsActive,
                networkProviderActive = networkActive,
                fusedProviderActive = fusedActive,
                verifiedDriftMeters = verifiedDriftMeters,
                lastFusedError = lastFusedError
            )
        }
    }

    fun setStopped() {
        _state.value = MockEngineState(status = MockStatus.Idle)
    }

    fun setError(message: String, requiresDevOptions: Boolean) {
        _state.update {
            it.copy(
                status = MockStatus.Error(message, requiresDevOptions),
                gpsProviderActive = false,
                networkProviderActive = false,
                fusedProviderActive = false,
                verifiedDriftMeters = null
            )
        }
    }

    fun isMockLocationAllowed(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }
}
