package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PinShiftApplication
import com.example.data.local.SavedLocationEntity
import com.example.data.model.GeoCoordinate
import com.example.data.model.GeocodingResult
import com.example.data.model.MockEngineState
import com.example.data.model.MockSettings
import com.example.data.model.MockStatus
import com.example.data.repository.LocationRepository
import com.example.data.repository.MockLocationEngineState
import com.example.service.MockLocationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos

enum class ActiveSheet {
    COORDINATE_ENTRY,
    SAVED_LOCATIONS,
    SETUP_DIAGNOSTICS,
    BOOKMARK_PIN
}

data class MapCameraCommand(
    val id: Long = System.nanoTime(),
    val latitude: Double,
    val longitude: Double,
    val zoom: Double? = null
)

data class MainUiState(
    val targetPin: GeoCoordinate = GeoCoordinate(latitude = 37.7955, longitude = -122.3937),
    val realLocation: GeoCoordinate? = null,
    val pinAddressHint: String = "Tap anywhere on the map or enter coordinates",
    val searchQuery: String = "",
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val isMockAppAuthorized: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val activeSheet: ActiveSheet? = null,
    val mapCameraCommand: MapCameraCommand? = null,
    val statusBannerMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository =
        (application as PinShiftApplication).locationRepository

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val mockEngineState: StateFlow<MockEngineState> = MockLocationEngineState.state

    val settings: StateFlow<MockSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MockSettings()
    )

    val savedLocations: StateFlow<List<SavedLocationEntity>> = repository.savedLocations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var reverseGeocodeJob: Job? = null
    private var searchDebounceJob: Job? = null

    init {
        refreshPermissionsAndDiagnostics()
        viewModelScope.launch {
            val savedTarget = repository.lastTargetCoordinateFlow.firstOrNull()
            val activeEngineCoord = mockEngineState.value.activeCoordinate
            val initialCoord = activeEngineCoord ?: savedTarget
            if (initialCoord != null) {
                _uiState.update {
                    it.copy(
                        targetPin = initialCoord,
                        mapCameraCommand = MapCameraCommand(
                            latitude = initialCoord.latitude,
                            longitude = initialCoord.longitude,
                            zoom = 14.0
                        )
                    )
                }
                scheduleReverseGeocode(initialCoord.latitude, initialCoord.longitude)
            }
        }
        viewModelScope.launch {
            if (repository.hasFineLocationPermission() &&
                mockEngineState.value.status !is MockStatus.Active
            ) {
                val real = repository.fetchCurrentRealLocation()
                if (real != null) {
                    _uiState.update { it.copy(realLocation = real) }
                }
            }
        }
    }

    private var pendingAutoStartFromDevSettings = false

    fun refreshPermissionsAndDiagnostics() {
        val context = getApplication<Application>()
        val hasPerm = repository.hasFineLocationPermission()
        val isMockAllowed = MockLocationEngineState.isMockLocationAllowed(context)
        _uiState.update {
            it.copy(
                hasLocationPermission = hasPerm,
                isMockAppAuthorized = isMockAllowed
            )
        }
        if (isMockAllowed) {
            val currentStatus = mockEngineState.value.status
            if (currentStatus is MockStatus.Error && currentStatus.requiresDevOptions) {
                MockLocationEngineState.setStopped()
            }
            if (pendingAutoStartFromDevSettings && hasPerm) {
                pendingAutoStartFromDevSettings = false
                startOrUpdateMock()
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            refreshRealLocation(centerMap = false)
        }
    }

    fun refreshRealLocation(centerMap: Boolean = true) {
        if (mockEngineState.value.status is MockStatus.Active) {
            _uiState.update {
                it.copy(statusBannerMessage = "Stop mocking first to read your real Location A.")
            }
            return
        }
        viewModelScope.launch {
            val real = repository.fetchCurrentRealLocation()
            if (real != null) {
                _uiState.update { state ->
                    state.copy(
                        realLocation = real,
                        mapCameraCommand = if (centerMap) {
                            MapCameraCommand(
                                latitude = real.latitude,
                                longitude = real.longitude,
                                zoom = 15.0
                            )
                        } else {
                            state.mapCameraCommand
                        }
                    )
                }
            } else if (centerMap) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Could not acquire real GPS fix yet.")
                }
            }
        }
    }

    fun selectCoordinate(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = settings.value.defaultAltitudeMeters,
        accuracyMeters: Float = settings.value.defaultAccuracyMeters,
        moveCamera: Boolean = false,
        zoom: Double? = null,
        addressLabel: String? = null
    ) {
        val clampedLat = latitude.coerceIn(-90.0, 90.0)
        val clampedLng = longitude.coerceIn(-180.0, 180.0)
        val newPin = GeoCoordinate(
            latitude = clampedLat,
            longitude = clampedLng,
            altitudeMeters = altitudeMeters,
            accuracyMeters = accuracyMeters
        )

        _uiState.update { state ->
            state.copy(
                targetPin = newPin,
                pinAddressHint = addressLabel ?: state.pinAddressHint,
                mapCameraCommand = if (moveCamera) {
                    MapCameraCommand(
                        latitude = clampedLat,
                        longitude = clampedLng,
                        zoom = zoom
                    )
                } else {
                    state.mapCameraCommand
                }
            )
        }

        viewModelScope.launch {
            repository.saveLastTargetCoordinate(clampedLat, clampedLng)
        }

        if (addressLabel == null) {
            scheduleReverseGeocode(clampedLat, clampedLng)
        }

        // If already mocking, immediately update the active mock location service!
        if (mockEngineState.value.status is MockStatus.Active) {
            MockLocationService.startOrUpdate(
                context = getApplication(),
                coordinate = newPin,
                intervalMs = settings.value.updateIntervalMs
            )
        }
    }

    fun nudgeTarget(deltaNorthMeters: Double, deltaEastMeters: Double) {
        val current = _uiState.value.targetPin
        val dLat = deltaNorthMeters / 111_320.0
        val cosLat = cos(Math.toRadians(current.latitude)).coerceAtLeast(0.05)
        val dLng = deltaEastMeters / (111_320.0 * cosLat)
        selectCoordinate(
            latitude = current.latitude + dLat,
            longitude = current.longitude + dLng,
            altitudeMeters = current.altitudeMeters,
            accuracyMeters = current.accuracyMeters,
            moveCamera = true
        )
    }

    fun centerCameraOnTargetPin() {
        val pin = _uiState.value.targetPin
        _uiState.update {
            it.copy(
                mapCameraCommand = MapCameraCommand(
                    latitude = pin.latitude,
                    longitude = pin.longitude,
                    zoom = 17.0
                )
            )
        }
    }

    fun toggleCrosshairLock() {
        val nextEnabled = !settings.value.crosshairLockEnabled
        updateSettings { it.copy(crosshairLockEnabled = nextEnabled) }
        if (nextEnabled) {
            val pin = _uiState.value.targetPin
            _uiState.update {
                it.copy(
                    mapCameraCommand = MapCameraCommand(
                        latitude = pin.latitude,
                        longitude = pin.longitude,
                        zoom = 17.0
                    )
                )
            }
        }
    }

    fun startOrUpdateMock() {
        refreshPermissionsAndDiagnostics()
        val pin = _uiState.value.targetPin
        MockLocationService.startOrUpdate(
            context = getApplication(),
            coordinate = pin,
            intervalMs = settings.value.updateIntervalMs
        )
    }

    fun stopMock() {
        MockLocationService.stop(getApplication())
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(450L)
            executeSearch(query)
        }
    }

    fun submitSearch() {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return
        searchDebounceJob?.cancel()
        viewModelScope.launch {
            executeSearch(query)
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        val result = repository.searchPlaces(query)
        result.fold(
            onSuccess = { list ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = list,
                        searchError = if (list.isEmpty()) "No matching locations found" else null
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = "Search unavailable offline — tap map or enter coordinates manually"
                    )
                }
            }
        )
    }

    fun selectSearchResult(result: GeocodingResult) {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null
            )
        }
        selectCoordinate(
            latitude = result.latitude,
            longitude = result.longitude,
            moveCamera = true,
            zoom = 17.5,
            addressLabel = result.displayName
        )
    }

    fun clearSearch() {
        searchDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
    }

    fun openSheet(sheet: ActiveSheet) {
        if (sheet == ActiveSheet.SETUP_DIAGNOSTICS) {
            refreshPermissionsAndDiagnostics()
        }
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = null) }
    }

    fun bookmarkCurrentPin(label: String) {
        val pin = _uiState.value.targetPin
        val hint = _uiState.value.pinAddressHint
        viewModelScope.launch {
            repository.saveLocation(
                label = label,
                latitude = pin.latitude,
                longitude = pin.longitude,
                altitudeMeters = pin.altitudeMeters,
                addressHint = hint
            )
            _uiState.update {
                it.copy(
                    activeSheet = null,
                    statusBannerMessage = "Saved \"${label.ifBlank { "Pin B" }}\" to bookmarks"
                )
            }
        }
    }

    fun selectSavedLocation(item: SavedLocationEntity, startMockImmediately: Boolean) {
        _uiState.update { it.copy(activeSheet = null) }
        selectCoordinate(
            latitude = item.latitude,
            longitude = item.longitude,
            altitudeMeters = item.altitudeMeters,
            moveCamera = true,
            zoom = 17.5,
            addressLabel = item.addressHint.ifBlank { item.label }
        )
        if (startMockImmediately) {
            startOrUpdateMock()
        }
    }

    fun deleteSavedLocation(id: Int) {
        viewModelScope.launch {
            repository.deleteSavedLocation(id)
        }
    }

    fun loadGlobalTestPresets() {
        viewModelScope.launch {
            repository.loadGlobalTestPresets()
        }
    }

    fun updateSettings(transform: (MockSettings) -> MockSettings) {
        val updated = transform(settings.value)
        viewModelScope.launch {
            repository.saveSettings(updated)
            if (mockEngineState.value.status is MockStatus.Active) {
                MockLocationService.startOrUpdate(
                    context = getApplication(),
                    coordinate = _uiState.value.targetPin.copy(
                        altitudeMeters = updated.defaultAltitudeMeters,
                        accuracyMeters = updated.defaultAccuracyMeters
                    ),
                    intervalMs = updated.updateIntervalMs
                )
            }
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun consumeCameraCommand() {
        _uiState.update { it.copy(mapCameraCommand = null) }
    }

    fun openDeveloperSettings() {
        pendingAutoStartFromDevSettings = true
        val context = getApplication<Application>()
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(devIntent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openAppDetailsSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun scheduleReverseGeocode(latitude: Double, longitude: Double) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch {
            delay(550L)
            val resolved = repository.reverseGeocode(latitude, longitude)
            if (!resolved.isNullOrBlank()) {
                _uiState.update { it.copy(pinAddressHint = resolved) }
            }
        }
    }
}
