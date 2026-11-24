package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.GeoCoordinate
import com.example.data.model.MockSettings
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pinshift_prefs")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val UPDATE_INTERVAL_MS = longPreferencesKey("update_interval_ms")
        val DEFAULT_ALTITUDE = doublePreferencesKey("default_altitude")
        val DEFAULT_ACCURACY = floatPreferencesKey("default_accuracy")
        val DARK_MAP_TILES = booleanPreferencesKey("dark_map_tiles")
        val CROSSHAIR_LOCK = booleanPreferencesKey("crosshair_lock")
        val SHOW_JOYSTICK = booleanPreferencesKey("show_joystick")
        val LAST_TARGET_LAT = doublePreferencesKey("last_target_lat")
        val LAST_TARGET_LNG = doublePreferencesKey("last_target_lng")
    }

    private val safeDataFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val settingsFlow: Flow<MockSettings> = safeDataFlow.map { prefs ->
        MockSettings(
            updateIntervalMs = prefs[Keys.UPDATE_INTERVAL_MS] ?: 500L,
            defaultAltitudeMeters = prefs[Keys.DEFAULT_ALTITUDE] ?: 35.0,
            defaultAccuracyMeters = prefs[Keys.DEFAULT_ACCURACY] ?: 1.0f,
            darkMapTiles = prefs[Keys.DARK_MAP_TILES] ?: false,
            crosshairLockEnabled = prefs[Keys.CROSSHAIR_LOCK] ?: false,
            showJoystick = prefs[Keys.SHOW_JOYSTICK] ?: false
        )
    }

    val lastTargetCoordinateFlow: Flow<GeoCoordinate?> = safeDataFlow.map { prefs ->
        val lat = prefs[Keys.LAST_TARGET_LAT]
        val lng = prefs[Keys.LAST_TARGET_LNG]
        if (lat != null && lng != null) {
            GeoCoordinate(
                latitude = lat,
                longitude = lng,
                altitudeMeters = prefs[Keys.DEFAULT_ALTITUDE] ?: 35.0,
                accuracyMeters = prefs[Keys.DEFAULT_ACCURACY] ?: 1.0f
            )
        } else {
            null
        }
    }

    suspend fun saveSettings(settings: MockSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UPDATE_INTERVAL_MS] = settings.updateIntervalMs
            prefs[Keys.DEFAULT_ALTITUDE] = settings.defaultAltitudeMeters
            prefs[Keys.DEFAULT_ACCURACY] = settings.defaultAccuracyMeters
            prefs[Keys.DARK_MAP_TILES] = settings.darkMapTiles
            prefs[Keys.CROSSHAIR_LOCK] = settings.crosshairLockEnabled
            prefs[Keys.SHOW_JOYSTICK] = settings.showJoystick
        }
    }

    suspend fun saveLastTargetCoordinate(latitude: Double, longitude: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_TARGET_LAT] = latitude
            prefs[Keys.LAST_TARGET_LNG] = longitude
        }
    }
}
