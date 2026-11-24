package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.data.local.SavedLocationDao
import com.example.data.local.SavedLocationEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.GeoCoordinate
import com.example.data.model.GeocodingResult
import com.example.data.model.MockSettings
import com.example.data.remote.NominatimApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(
    private val context: Context,
    private val savedLocationDao: SavedLocationDao,
    private val settingsDataStore: SettingsDataStore,
    private val nominatimApi: NominatimApi
) {
    val savedLocations: Flow<List<SavedLocationEntity>> = savedLocationDao.getAllLocations()
    val settingsFlow: Flow<MockSettings> = settingsDataStore.settingsFlow
    val lastTargetCoordinateFlow: Flow<GeoCoordinate?> = settingsDataStore.lastTargetCoordinateFlow

    suspend fun saveLocation(
        label: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        addressHint: String
    ) {
        savedLocationDao.insertLocation(
            SavedLocationEntity(
                label = label.trim().ifEmpty { formatShortCoord(latitude, longitude) },
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                addressHint = addressHint
            )
        )
    }

    suspend fun deleteSavedLocation(id: Int) {
        savedLocationDao.deleteLocationById(id)
    }

    suspend fun loadGlobalTestPresets() {
        val presets = listOf(
            SavedLocationEntity(
                label = "San Francisco • Ferry Bldg",
                latitude = 37.7955,
                longitude = -122.3937,
                altitudeMeters = 12.0,
                addressHint = "San Francisco, CA, USA"
            ),
            SavedLocationEntity(
                label = "New York • Times Square",
                latitude = 40.7580,
                longitude = -73.9855,
                altitudeMeters = 18.0,
                addressHint = "Manhattan, New York, USA"
            ),
            SavedLocationEntity(
                label = "London • Westminster",
                latitude = 51.5007,
                longitude = -0.1246,
                altitudeMeters = 15.0,
                addressHint = "London, United Kingdom"
            ),
            SavedLocationEntity(
                label = "Tokyo • Shibuya Crossing",
                latitude = 35.6595,
                longitude = 139.7004,
                altitudeMeters = 28.0,
                addressHint = "Shibuya, Tokyo, Japan"
            )
        )
        presets.forEach { savedLocationDao.insertLocation(it) }
    }

    suspend fun saveSettings(settings: MockSettings) {
        settingsDataStore.saveSettings(settings)
    }

    suspend fun saveLastTargetCoordinate(latitude: Double, longitude: Double) {
        settingsDataStore.saveLastTargetCoordinate(latitude, longitude)
    }

    suspend fun searchPlaces(query: String): Result<List<GeocodingResult>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        // Check if the user pasted direct "lat, lng" coordinates in the search box
        parseCoordinateInput(trimmed)?.let { (lat, lng) ->
            return Result.success(
                listOf(
                    GeocodingResult(
                        displayName = "Direct Coordinates (${formatShortCoord(lat, lng)})",
                        latitude = lat,
                        longitude = lng,
                        category = "coordinates"
                    )
                )
            )
        }

        return try {
            val dtos = nominatimApi.searchPlaces(trimmed)
            val mapped = dtos.mapNotNull { dto ->
                val lat = dto.lat.toDoubleOrNull()
                val lon = dto.lon.toDoubleOrNull()
                if (lat != null && lon != null) {
                    GeocodingResult(
                        displayName = dto.displayName,
                        latitude = lat,
                        longitude = lon,
                        category = dto.type.orEmpty()
                    )
                } else null
            }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return try {
            val result = nominatimApi.reverseGeocode(latitude, longitude)
            result.displayName
        } catch (_: Exception) {
            null
        }
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentRealLocation(): GeoCoordinate? {
        if (!hasFineLocationPermission()) return null

        // First try FusedLocationProviderClient current location, fallback to LocationManager
        val fused = suspendCancellableCoroutine<GeoCoordinate?> { cont ->
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }

                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null && !isFromMock(loc)) {
                            cont.resume(
                                GeoCoordinate(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    altitudeMeters = loc.altitude,
                                    accuracyMeters = loc.accuracy
                                )
                            )
                        } else {
                            cont.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            } catch (_: Exception) {
                cont.resume(null)
            }
        }
        if (fused != null) return fused

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(gpsLoc, netLoc).firstOrNull { !isFromMock(it) }
            best?.let {
                GeoCoordinate(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitudeMeters = it.altitude,
                    accuracyMeters = it.accuracy
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isFromMock(location: android.location.Location): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    companion object {
        fun parseCoordinateInput(input: String): Pair<Double, Double>? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // Support geo:lat,lng or Google Maps @lat,lng / ?q=lat,lng links
            val uriCoordMatch = Regex("""(?:@|q=|geo:)(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
                .find(trimmed)
            if (uriCoordMatch != null) {
                val lat = uriCoordMatch.groupValues[1].toDoubleOrNull()
                val lng = uriCoordMatch.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return lat to lng
                }
            }

            // Support "28.6139° N, 77.2090° E" style hemisphere suffixes
            val hemiMatch = Regex(
                """^\s*(-?\d+(?:\.\d+)?)\s*°?\s*([NS])?\s*[,;\s]+\s*(-?\d+(?:\.\d+)?)\s*°?\s*([EW])?\s*$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed.replace("(", "").replace(")", ""))
            if (hemiMatch != null) {
                var lat = hemiMatch.groupValues[1].toDoubleOrNull() ?: return null
                val ns = hemiMatch.groupValues[2].uppercase()
                var lng = hemiMatch.groupValues[3].toDoubleOrNull() ?: return null
                val ew = hemiMatch.groupValues[4].uppercase()
                if (ns == "S" && lat > 0) lat = -lat
                if (ew == "W" && lng > 0) lng = -lng
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return lat to lng
                }
            }

            val cleaned = trimmed
                .replace("(", "")
                .replace(")", "")
                .replace("°", "")
            val parts = cleaned.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lng = parts[1].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return lat to lng
        }

        fun formatShortCoord(lat: Double, lng: Double): String {
            return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng)
        }

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            return distanceKm(lat1, lon1, lat2, lon2) * 1000.0
        }
    }
}
