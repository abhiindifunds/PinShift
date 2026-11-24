package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.GeocodingResult
import com.example.data.model.MockSettings
import com.example.data.model.MockStatus
import com.example.data.repository.LocationRepository
import com.example.ui.components.BottomTelemetryDock
import com.example.ui.components.CenterCrosshairReticle
import com.example.ui.components.CoordinateEntrySheet
import com.example.ui.components.JoystickOverlay
import com.example.ui.components.MapToolButton
import com.example.ui.components.OsmMapView
import com.example.ui.components.SavedLocationsSheet
import com.example.ui.components.SetupDiagnosticsSheet
import com.example.ui.components.TopSearchCommandBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PinShiftScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedLocations by viewModel.savedLocations.collectAsStateWithLifecycle()

    // Collect only boolean active state here so 1-second mock ticks never recompose the map screen
    val isMockActive by remember {
        viewModel.mockEngineState.map { it.status is MockStatus.Active }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var zoomStepCommand by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var pendingMockStartAfterPermission by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
        if (granted && pendingMockStartAfterPermission) {
            pendingMockStartAfterPermission = false
            viewModel.startOrUpdateMock()
        } else {
            pendingMockStartAfterPermission = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    BackHandler(enabled = uiState.searchResults.isNotEmpty() || uiState.searchError != null) {
        focusManager.clearFocus()
        viewModel.clearSearch()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndDiagnostics()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    val triggerMockToggle = remember(isMockActive, uiState.hasLocationPermission, hasNotificationPermission) {
        {
            if (isMockActive) {
                viewModel.stopMock()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!uiState.hasLocationPermission) {
                    pendingMockStartAfterPermission = true
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    viewModel.startOrUpdateMock()
                }
            }
        }
    }

    val triggerCopyCoords = remember(uiState.targetPin) {
        {
            val coordText = String.format(
                Locale.US,
                "%.6f, %.6f",
                uiState.targetPin.latitude,
                uiState.targetPin.longitude
            )
            clipboardManager.setText(AnnotatedString(coordText))
            scope.launch {
                snackbarHostState.showSnackbar("Copied $coordText")
            }
            Unit
        }
    }

    val triggerRealLocation = remember(uiState.hasLocationPermission) {
        {
            if (uiState.hasLocationPermission) {
                viewModel.refreshRealLocation(centerMap = true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLayout = maxWidth >= 720.dp

            if (isWideLayout) {
                // High-End Tablet / Landscape / Unfolded Two-Pane Tactical Console
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TopSearchCommandBar(
                            query = uiState.searchQuery,
                            isSearching = uiState.isSearching,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            onSearchSubmit = {
                                focusManager.clearFocus()
                                viewModel.submitSearch()
                            },
                            onClear = viewModel::clearSearch,
                            onOpenSaved = {
                                focusManager.clearFocus()
                                viewModel.openSheet(ActiveSheet.SAVED_LOCATIONS)
                            },
                            onOpenSetup = {
                                focusManager.clearFocus()
                                viewModel.openSheet(ActiveSheet.SETUP_DIAGNOSTICS)
                            }
                        )

                        SearchResultsPanel(
                            results = uiState.searchResults,
                            searchError = uiState.searchError,
                            onSelect = { result ->
                                focusManager.clearFocus()
                                viewModel.selectSearchResult(result)
                            }
                        )

                        BottomTelemetryDock(
                            targetPin = uiState.targetPin,
                            realLocation = uiState.realLocation,
                            pinAddressHint = uiState.pinAddressHint,
                            mockEngineStateFlow = viewModel.mockEngineState,
                            onOpenCoordEntry = { viewModel.openSheet(ActiveSheet.COORDINATE_ENTRY) },
                            onOpenBookmarks = { viewModel.openSheet(ActiveSheet.SAVED_LOCATIONS) },
                            onOpenSetup = { viewModel.openSheet(ActiveSheet.SETUP_DIAGNOSTICS) },
                            onCopyCoords = triggerCopyCoords,
                            onCenterOnPinB = viewModel::centerCameraOnTargetPin,
                            onToggleMock = triggerMockToggle
                        )

                        AnimatedVisibility(
                            visible = settings.showJoystick,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            JoystickOverlay(
                                onNudge = viewModel::nudgeTarget,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        OsmMapView(
                            targetPin = uiState.targetPin,
                            realLocation = uiState.realLocation,
                            isMockActive = isMockActive,
                            darkMapTiles = settings.darkMapTiles,
                            crosshairLockEnabled = settings.crosshairLockEnabled,
                            cameraCommand = uiState.mapCameraCommand,
                            zoomStepCommand = zoomStepCommand,
                            onMapTapped = { lat, lng ->
                                focusManager.clearFocus()
                                viewModel.selectCoordinate(latitude = lat, longitude = lng)
                            },
                            onCrosshairCenterChanged = { lat, lng ->
                                viewModel.selectCoordinate(latitude = lat, longitude = lng)
                            },
                            onCameraCommandConsumed = viewModel::consumeCameraCommand,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("osm_map_view")
                        )

                        if (settings.crosshairLockEnabled) {
                            CenterCrosshairReticle(
                                isMockActive = isMockActive,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        MapControlsRail(
                            settings = settings,
                            onToggleCrosshair = viewModel::toggleCrosshairLock,
                            onRealLocation = triggerRealLocation,
                            onZoomIn = { zoomStepCommand = System.nanoTime() to 1.0 },
                            onZoomOut = { zoomStepCommand = System.nanoTime() to -1.0 },
                            onToggleJoystick = {
                                viewModel.updateSettings { it.copy(showJoystick = !it.showJoystick) }
                            },
                            onToggleMapTheme = {
                                viewModel.updateSettings { it.copy(darkMapTiles = !it.darkMapTiles) }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                        )
                    }
                }
            } else {
                // Flagship Edge-to-Edge Portrait Phone Layout
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmMapView(
                        targetPin = uiState.targetPin,
                        realLocation = uiState.realLocation,
                        isMockActive = isMockActive,
                        darkMapTiles = settings.darkMapTiles,
                        crosshairLockEnabled = settings.crosshairLockEnabled,
                        cameraCommand = uiState.mapCameraCommand,
                        zoomStepCommand = zoomStepCommand,
                        onMapTapped = { lat, lng ->
                            focusManager.clearFocus()
                            viewModel.selectCoordinate(latitude = lat, longitude = lng)
                        },
                        onCrosshairCenterChanged = { lat, lng ->
                            viewModel.selectCoordinate(latitude = lat, longitude = lng)
                        },
                        onCameraCommandConsumed = viewModel::consumeCameraCommand,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("osm_map_view")
                    )

                    if (settings.crosshairLockEnabled) {
                        CenterCrosshairReticle(
                            isMockActive = isMockActive,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopSearchCommandBar(
                            query = uiState.searchQuery,
                            isSearching = uiState.isSearching,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            onSearchSubmit = {
                                focusManager.clearFocus()
                                viewModel.submitSearch()
                            },
                            onClear = viewModel::clearSearch,
                            onOpenSaved = {
                                focusManager.clearFocus()
                                viewModel.openSheet(ActiveSheet.SAVED_LOCATIONS)
                            },
                            onOpenSetup = {
                                focusManager.clearFocus()
                                viewModel.openSheet(ActiveSheet.SETUP_DIAGNOSTICS)
                            }
                        )

                        SearchResultsPanel(
                            results = uiState.searchResults,
                            searchError = uiState.searchError,
                            onSelect = { result ->
                                focusManager.clearFocus()
                                viewModel.selectSearchResult(result)
                            }
                        )
                    }

                    MapControlsRail(
                        settings = settings,
                        onToggleCrosshair = viewModel::toggleCrosshairLock,
                        onRealLocation = triggerRealLocation,
                        onZoomIn = { zoomStepCommand = System.nanoTime() to 1.0 },
                        onZoomOut = { zoomStepCommand = System.nanoTime() to -1.0 },
                        onToggleJoystick = {
                            viewModel.updateSettings { it.copy(showJoystick = !it.showJoystick) }
                        },
                        onToggleMapTheme = {
                            viewModel.updateSettings { it.copy(darkMapTiles = !it.darkMapTiles) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = settings.showJoystick,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            JoystickOverlay(onNudge = viewModel::nudgeTarget)
                        }

                        BottomTelemetryDock(
                            targetPin = uiState.targetPin,
                            realLocation = uiState.realLocation,
                            pinAddressHint = uiState.pinAddressHint,
                            mockEngineStateFlow = viewModel.mockEngineState,
                            onOpenCoordEntry = { viewModel.openSheet(ActiveSheet.COORDINATE_ENTRY) },
                            onOpenBookmarks = { viewModel.openSheet(ActiveSheet.SAVED_LOCATIONS) },
                            onOpenSetup = { viewModel.openSheet(ActiveSheet.SETUP_DIAGNOSTICS) },
                            onCopyCoords = triggerCopyCoords,
                            onCenterOnPinB = viewModel::centerCameraOnTargetPin,
                            onToggleMock = triggerMockToggle
                        )
                    }
                }
            }
        }
    }

    when (uiState.activeSheet) {
        ActiveSheet.COORDINATE_ENTRY -> {
            CoordinateEntrySheet(
                currentPin = uiState.targetPin,
                onDismiss = viewModel::closeSheet,
                onApplyOnly = { lat, lng, alt, acc ->
                    viewModel.closeSheet()
                    viewModel.selectCoordinate(
                        latitude = lat,
                        longitude = lng,
                        altitudeMeters = alt,
                        accuracyMeters = acc,
                        moveCamera = true,
                        zoom = 17.5
                    )
                },
                onApplyAndMock = { lat, lng, alt, acc ->
                    viewModel.closeSheet()
                    viewModel.selectCoordinate(
                        latitude = lat,
                        longitude = lng,
                        altitudeMeters = alt,
                        accuracyMeters = acc,
                        moveCamera = true,
                        zoom = 17.5
                    )
                    if (!uiState.hasLocationPermission) {
                        pendingMockStartAfterPermission = true
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.startOrUpdateMock()
                    }
                }
            )
        }

        ActiveSheet.SAVED_LOCATIONS, ActiveSheet.BOOKMARK_PIN -> {
            SavedLocationsSheet(
                savedLocations = savedLocations,
                currentPin = uiState.targetPin,
                currentAddressHint = uiState.pinAddressHint,
                onDismiss = viewModel::closeSheet,
                onSaveCurrentPin = viewModel::bookmarkCurrentPin,
                onSelectSaved = { item, startMock ->
                    if (startMock && !uiState.hasLocationPermission) {
                        viewModel.selectSavedLocation(item, startMockImmediately = false)
                        pendingMockStartAfterPermission = true
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.selectSavedLocation(item, startMockImmediately = startMock)
                    }
                },
                onDeleteSaved = viewModel::deleteSavedLocation,
                onLoadGlobalPresets = viewModel::loadGlobalTestPresets
            )
        }

        ActiveSheet.SETUP_DIAGNOSTICS -> {
            val mockState by viewModel.mockEngineState.collectAsStateWithLifecycle()
            SetupDiagnosticsSheet(
                isMockAppAuthorized = uiState.isMockAppAuthorized,
                hasLocationPermission = uiState.hasLocationPermission,
                hasNotificationPermission = hasNotificationPermission,
                mockEngineState = mockState,
                settings = settings,
                onDismiss = viewModel::closeSheet,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenDeveloperSettings = viewModel::openDeveloperSettings,
                onRefreshDiagnostics = viewModel::refreshPermissionsAndDiagnostics,
                onUpdateSettings = viewModel::updateSettings
            )
        }

        null -> Unit
    }
}

@Composable
private fun SearchResultsPanel(
    results: List<GeocodingResult>,
    searchError: String?,
    onSelect: (GeocodingResult) -> Unit
) {
    AnimatedVisibility(
        visible = results.isNotEmpty() || searchError != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (searchError != null && results.isEmpty()) {
                Text(
                    text = searchError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp)
                ) {
                    items(results) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(result) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = LocationRepository.formatShortCoord(
                                        result.latitude,
                                        result.longitude
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapControlsRail(
    settings: MockSettings,
    onToggleCrosshair: () -> Unit,
    onRealLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleJoystick: () -> Unit,
    onToggleMapTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MapToolButton(
            icon = Icons.Default.CenterFocusStrong,
            contentDescription = stringResource(R.string.cd_center_crosshair),
            active = settings.crosshairLockEnabled,
            onClick = onToggleCrosshair,
            testTag = "crosshair_toggle_button"
        )

        MapToolButton(
            icon = Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.cd_real_location),
            active = false,
            onClick = onRealLocation,
            testTag = "real_location_button"
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Column {
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_zoom_in)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .width(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.cd_zoom_out)
                    )
                }
            }
        }

        MapToolButton(
            icon = Icons.Default.ControlCamera,
            contentDescription = stringResource(R.string.cd_joystick),
            active = settings.showJoystick,
            onClick = onToggleJoystick,
            testTag = "joystick_toggle_button"
        )

        MapToolButton(
            icon = if (settings.darkMapTiles) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = stringResource(R.string.cd_map_style),
            active = false,
            onClick = onToggleMapTheme,
            testTag = "map_theme_toggle_button"
        )
    }
}
