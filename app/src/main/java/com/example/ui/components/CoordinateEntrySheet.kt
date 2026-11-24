package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.GeoCoordinate
import com.example.data.repository.LocationRepository
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateEntrySheet(
    currentPin: GeoCoordinate,
    onDismiss: () -> Unit,
    onApplyOnly: (Double, Double, Double, Float) -> Unit,
    onApplyAndMock: (Double, Double, Double, Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var latText by remember {
        mutableStateOf(String.format(Locale.US, "%.6f", currentPin.latitude))
    }
    var lngText by remember {
        mutableStateOf(String.format(Locale.US, "%.6f", currentPin.longitude))
    }
    var altText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentPin.altitudeMeters))
    }
    var accText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentPin.accuracyMeters))
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun parseAndValidate(): GeoCoordinate? {
        val lat = latText.trim().replace(',', '.').toDoubleOrNull()
        val lng = lngText.trim().replace(',', '.').toDoubleOrNull()
        val alt = altText.trim().replace(',', '.').toDoubleOrNull() ?: 35.0
        val acc = accText.trim().replace(',', '.').toFloatOrNull() ?: 1.0f

        if (lat == null || lat !in -90.0..90.0) {
            validationError = "Latitude must be between -90.0 and 90.0"
            return null
        }
        if (lng == null || lng !in -180.0..180.0) {
            validationError = "Longitude must be between -180.0 and 180.0"
            return null
        }
        validationError = null
        return GeoCoordinate(
            latitude = lat,
            longitude = lng,
            altitudeMeters = alt,
            accuracyMeters = acc.coerceIn(1f, 500f)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Manual Coordinates",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Pinpoint exact Target Pin B values",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        val clipText = clipboard.getText()?.text.orEmpty()
                        val parsed = LocationRepository.parseCoordinateInput(clipText)
                        if (parsed != null) {
                            latText = String.format(Locale.US, "%.6f", parsed.first)
                            lngText = String.format(Locale.US, "%.6f", parsed.second)
                            validationError = null
                        } else {
                            validationError = "Clipboard didn't contain \"lat, lng\" coordinates"
                        }
                    },
                    modifier = Modifier.testTag("paste_coordinates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Paste")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        validationError = null
                    },
                    label = { Text("Latitude (-90..90)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("latitude_input")
                )

                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        validationError = null
                    },
                    label = { Text("Longitude (-180..180)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("longitude_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = altText,
                    onValueChange = { altText = it },
                    label = { Text("Altitude (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("altitude_input")
                )

                OutlinedTextField(
                    value = accText,
                    onValueChange = { accText = it },
                    label = { Text("Accuracy (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("accuracy_input")
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        parseAndValidate()?.let {
                            onApplyOnly(it.latitude, it.longitude, it.altitudeMeters, it.accuracyMeters)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("apply_coordinates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Set Pin")
                }

                Button(
                    onClick = {
                        parseAndValidate()?.let {
                            onApplyAndMock(it.latitude, it.longitude, it.altitudeMeters, it.accuracyMeters)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .testTag("apply_and_mock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Set & Mock")
                }
            }
        }
    }
}
