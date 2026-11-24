package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MockEngineState
import com.example.data.model.MockSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDiagnosticsSheet(
    isMockAppAuthorized: Boolean,
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    mockEngineState: MockEngineState,
    settings: MockSettings,
    onDismiss: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onUpdateSettings: ((MockSettings) -> MockSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Mock Location Setup & Engine",
                style = MaterialTheme.typography.headlineSmall
            )

            // 1. Developer Options Mock App Check
            DiagnosticRowCard(
                title = "System Mock Location Authorization",
                subtitle = if (isMockAppAuthorized) {
                    "PinShift is authorized as your system mock location provider."
                } else {
                    "Android requires either selecting PinShift under Developer Options → Select mock location app, or granting via ADB for QA testing:\nadb shell appops set com.aistudio.pinshift.kzxmpq android:mock_location allow"
                },
                isOk = isMockAppAuthorized,
                actionLabel = if (isMockAppAuthorized) "Verify" else "Open Dev Settings",
                onAction = {
                    if (isMockAppAuthorized) onRefreshDiagnostics() else onOpenDeveloperSettings()
                },
                testTag = "dev_options_action_button"
            )

            // 2. Fine Location Permission
            DiagnosticRowCard(
                title = "Location Permission (ACCESS_FINE_LOCATION)",
                subtitle = if (hasLocationPermission) {
                    "Granted — FusedLocationProvider & Real Location A enabled."
                } else {
                    "Required for FusedLocationProvider mock injection and reading Location A."
                },
                isOk = hasLocationPermission,
                actionLabel = if (hasLocationPermission) "Granted" else "Grant",
                onAction = onRequestLocationPermission,
                actionEnabled = !hasLocationPermission,
                testTag = "grant_location_perm_button"
            )

            // 3. Notifications (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                DiagnosticRowCard(
                    title = "Foreground Notification Permission",
                    subtitle = if (hasNotificationPermission) {
                        "Granted — persistent status bar indicator with Stop button enabled."
                    } else {
                        "Allows showing active mock coordinates in the notification shade."
                    },
                    isOk = hasNotificationPermission,
                    actionLabel = if (hasNotificationPermission) "Granted" else "Allow",
                    onAction = onRequestNotificationPermission,
                    actionEnabled = !hasNotificationPermission,
                    testTag = "grant_notif_perm_button"
                )
            }

            // Provider telemetry status
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PROVIDER ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "GPS: ${if (mockEngineState.gpsProviderActive) "ACTIVE" else "IDLE"}  •  " +
                            "NET: ${if (mockEngineState.networkProviderActive) "ACTIVE" else "IDLE"}  •  " +
                            "FUSED: ${if (mockEngineState.fusedProviderActive) "ACTIVE" else "IDLE"}",
                        style = MaterialTheme.typography.labelSmall
                    )

                    mockEngineState.verifiedDriftMeters?.let { drift ->
                        val driftText = if (drift < 1000.0) {
                            "Live verified fix drift: ${drift.toInt()} m from Pin B"
                        } else {
                            String.format(java.util.Locale.US, "Live verified fix drift: %.2f km from Pin B", drift / 1000.0)
                        }
                        Text(
                            text = driftText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (drift <= 15.0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    if (!mockEngineState.lastFusedError.isNullOrBlank()) {
                        Text(
                            text = "Fused note: ${mockEngineState.lastFusedError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Reported GPS Accuracy",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0f to "1m Pinpoint", 3.0f to "3m High", 10.0f to "10m Std").forEach { (acc, label) ->
                            FilterChip(
                                selected = settings.defaultAccuracyMeters == acc,
                                onClick = {
                                    onUpdateSettings { it.copy(defaultAccuracyMeters = acc) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(
                        text = "Mock Injection Interval",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500L to "0.5s", 1000L to "1.0s", 2000L to "2.0s").forEach { (ms, label) ->
                            FilterChip(
                                selected = settings.updateIntervalMs == ms,
                                onClick = {
                                    onUpdateSettings { it.copy(updateIntervalMs = ms) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark Cartography Map Tiles",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "High-contrast slate styling for map tiles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.darkMapTiles,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(darkMapTiles = checked) }
                            },
                            modifier = Modifier.testTag("dark_tiles_switch")
                        )
                    }

                    Text(
                        text = "Tip: If another app still shows ~1 km off, ensure that app has \"Precise\" location enabled and turn off \"Google Location Accuracy\" (Wi-Fi/cell scanning) in Android Location Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onRefreshDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("refresh_diagnostics_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Re-check Mock Authorization")
            }
        }
    }
}

@Composable
private fun DiagnosticRowCard(
    title: String,
    subtitle: String,
    isOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
    actionEnabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOk) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            1.dp,
            if (isOk) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.tertiary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionEnabled) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(testTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperMode,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(actionLabel)
                }
            }
        }
    }
}
