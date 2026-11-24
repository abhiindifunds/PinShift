package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.GeoCoordinate
import com.example.data.model.MockEngineState
import com.example.data.model.MockStatus
import com.example.data.repository.LocationRepository
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun TopSearchCommandBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSetup: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_location_input")
                )
            }

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_clear_search)
                    )
                }
            }

            IconButton(
                onClick = onOpenSaved,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("saved_locations_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = stringResource(R.string.cd_menu_saved),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onOpenSetup,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("setup_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.cd_menu_setup),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MapToolButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun CenterCrosshairReticle(
    isMockActive: Boolean,
    modifier: Modifier = Modifier
) {
    val reticleColor = if (isMockActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Canvas(modifier = modifier.size(56.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 16.dp.toPx()
        drawCircle(
            color = reticleColor.copy(alpha = 0.2f),
            radius = radius
        )
        drawCircle(
            color = reticleColor,
            radius = radius,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = reticleColor,
            radius = 3.dp.toPx()
        )
        val arm = 8.dp.toPx()
        drawLine(reticleColor, Offset(center.x - radius - arm, center.y), Offset(center.x - radius + 4f, center.y), 2.dp.toPx())
        drawLine(reticleColor, Offset(center.x + radius - 4f, center.y), Offset(center.x + radius + arm, center.y), 2.dp.toPx())
        drawLine(reticleColor, Offset(center.x, center.y - radius - arm), Offset(center.x, center.y - radius + 4f), 2.dp.toPx())
        drawLine(reticleColor, Offset(center.x, center.y + radius - 4f), Offset(center.x, center.y + radius + arm), 2.dp.toPx())
    }
}

@Composable
private fun StatusPulseDot(
    isMockActive: Boolean,
    dotColor: Color
) {
    if (isMockActive) {
        val pulseTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = pulseAlpha }
                .background(dotColor, CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
    }
}

@Composable
fun BottomTelemetryDock(
    targetPin: GeoCoordinate,
    realLocation: GeoCoordinate?,
    pinAddressHint: String,
    mockEngineStateFlow: StateFlow<MockEngineState>,
    onOpenCoordEntry: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSetup: () -> Unit,
    onCopyCoords: () -> Unit,
    onCenterOnPinB: () -> Unit,
    onToggleMock: () -> Unit
) {
    val mockState by mockEngineStateFlow.collectAsStateWithLifecycle()
    val mockStatus = mockState.status
    val isMockActive = mockStatus is MockStatus.Active

    val distanceLabel = remember(realLocation, targetPin) {
        val real = realLocation ?: return@remember null
        val km = LocationRepository.distanceKm(
            real.latitude,
            real.longitude,
            targetPin.latitude,
            targetPin.longitude
        )
        if (km < 1.0) {
            String.format(Locale.US, "A → B: %.0f m", km * 1000.0)
        } else {
            String.format(Locale.US, "A → B: %.1f km", km)
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isMockActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val drift = mockState.verifiedDriftMeters
                    val dotColor = when {
                        mockStatus is MockStatus.Active && drift != null && drift > 25.0 ->
                            MaterialTheme.colorScheme.tertiary
                        mockStatus is MockStatus.Active ->
                            MaterialTheme.colorScheme.primary
                        mockStatus is MockStatus.Error ->
                            MaterialTheme.colorScheme.error
                        else ->
                            MaterialTheme.colorScheme.secondary
                    }
                    StatusPulseDot(isMockActive = isMockActive, dotColor = dotColor)
                    Text(
                        text = when (mockStatus) {
                            is MockStatus.Active -> {
                                val providers = buildList {
                                    if (mockState.gpsProviderActive) add("GPS")
                                    if (mockState.networkProviderActive) add("NET")
                                    if (mockState.fusedProviderActive) add("FUSED")
                                }.joinToString("+").ifEmpty { "GPS" }
                                val lockTag = when {
                                    drift == null -> "#${mockState.updatesPushed}"
                                    drift <= 15.0 -> "±${drift.toInt().coerceAtLeast(1)}m LOCK"
                                    drift < 1000.0 -> "DRIFT ${drift.toInt()}m"
                                    else -> String.format(Locale.US, "DRIFT %.1fkm", drift / 1000.0)
                                }
                                "ACTIVE ($providers) • $lockTag"
                            }
                            is MockStatus.Error -> "AUTHORIZATION NEEDED"
                            MockStatus.Idle -> "PIN B TARGET READY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = dotColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (distanceLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = pinAddressHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCoordEntry)
                    .testTag("coordinate_readout_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "LAT  %.6f",
                                targetPin.latitude
                            ),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "LNG  %.6f",
                                targetPin.longitude
                            ),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onOpenCoordEntry,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_coordinates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocationAlt,
                            contentDescription = stringResource(R.string.cd_edit_coords),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onCopyCoords,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("copy_coordinates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_coords),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onOpenBookmarks,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("bookmark_pin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.cd_bookmark_pin),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (mockStatus is MockStatus.Error) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = mockStatus.requiresDevOptions, onClick = onOpenSetup),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = mockStatus.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (mockStatus.requiresDevOptions) {
                        Text(
                            text = "Open Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onCenterOnPinB,
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("center_pin_b_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Center map on Pin B"
                    )
                }

                Button(
                    onClick = onToggleMock,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMockActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isMockActive) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("toggle_mock_button")
                ) {
                    Icon(
                        imageVector = if (isMockActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isMockActive) R.string.stop_mocking else R.string.start_mocking
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}
