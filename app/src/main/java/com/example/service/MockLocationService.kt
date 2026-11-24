package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.GeoCoordinate
import com.example.data.repository.LocationRepository
import com.example.data.repository.MockLocationEngineState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class MockLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mockLoopJob: Job? = null

    private lateinit var locationManager: LocationManager
    private var fusedClient: FusedLocationProviderClient? = null

    @Volatile
    private var currentCoordinate = GeoCoordinate(latitude = 37.7955, longitude = -122.3937)

    @Volatile
    private var updateIntervalMs = 500L

    @Volatile
    private var gpsProviderRegistered = false

    @Volatile
    private var networkProviderRegistered = false

    @Volatile
    private var fusedSystemProviderRegistered = false

    @Volatile
    private var fusedMockModeEnabled = false

    @Volatile
    private var fusedUpdatesRegistered = false

    @Volatile
    private var lastFusedError: String? = null

    @Volatile
    private var lastVerifiedLocation: Location? = null

    @Volatile
    private var pendingFlushOnJump = true

    private val fusedVerificationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                lastVerifiedLocation = loc
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedClient = try {
            LocationServices.getFusedLocationProviderClient(this)
        } catch (_: Exception) {
            null
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMockingAndSelf()
                return START_NOT_STICKY
            }

            ACTION_START_OR_UPDATE, null -> {
                if (intent != null) {
                    val prev = currentCoordinate
                    val lat = intent.getDoubleExtra(EXTRA_LATITUDE, prev.latitude)
                    val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, prev.longitude)
                    val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, prev.altitudeMeters)
                    val acc = intent.getFloatExtra(EXTRA_ACCURACY, prev.accuracyMeters)
                    updateIntervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 500L).coerceIn(250L, 10000L)
                    val next = GeoCoordinate(
                        latitude = lat.coerceIn(-90.0, 90.0),
                        longitude = lng.coerceIn(-180.0, 180.0),
                        altitudeMeters = alt,
                        accuracyMeters = acc.coerceAtLeast(1.0f)
                    )
                    if (LocationRepository.distanceMeters(
                            prev.latitude,
                            prev.longitude,
                            next.latitude,
                            next.longitude
                        ) > 5.0
                    ) {
                        pendingFlushOnJump = true
                        lastVerifiedLocation = null
                    }
                    currentCoordinate = next
                }

                if (!startForegroundWithNotification(currentCoordinate)) {
                    MockLocationEngineState.setError(
                        message = "Grant Location permission before starting Mock Location.",
                        requiresDevOptions = false
                    )
                    stopSelfCleanup()
                    return START_NOT_STICKY
                }
                ensureProvidersAndStartLoop()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(coord: GeoCoordinate): Boolean {
        val notification = buildNotification(coord)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private var lastProviderFailure: String? = null
    private var lastFailureWasSecurity: Boolean = false

    private fun ensureProvidersAndStartLoop() {
        if (!setupSystemTestProviders()) {
            val isAuthorized = MockLocationEngineState.isMockLocationAllowed(this)
            val message = when {
                !isAuthorized || lastFailureWasSecurity ->
                    "Inside Developer Options, tap \"Select mock location app\" → PinShift."
                !lastProviderFailure.isNullOrBlank() ->
                    "Mock provider error: $lastProviderFailure"
                else ->
                    "Unable to initialize mock location provider."
            }
            MockLocationEngineState.setError(
                message = message,
                requiresDevOptions = !isAuthorized || lastFailureWasSecurity
            )
            stopSelfCleanup()
            return
        }

        // Update notification text to reflect latest Pin B
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(currentCoordinate))

        if (mockLoopJob?.isActive == true) {
            // Immediately push the new coordinate once so the jump is instantaneous
            serviceScope.launch { pushCoordinateTick() }
            return
        }

        mockLoopJob = serviceScope.launch {
            while (isActive) {
                pushCoordinateTick()
                delay(updateIntervalMs)
            }
        }
    }

    private fun setupSystemTestProviders(): Boolean {
        lastProviderFailure = null
        lastFailureWasSecurity = false

        if (!gpsProviderRegistered) {
            gpsProviderRegistered = registerTestProvider(LocationManager.GPS_PROVIDER)
        }
        if (!networkProviderRegistered) {
            networkProviderRegistered = registerTestProvider(LocationManager.NETWORK_PROVIDER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !fusedSystemProviderRegistered) {
            fusedSystemProviderRegistered = registerTestProvider(LocationManager.FUSED_PROVIDER)
        }

        return gpsProviderRegistered || networkProviderRegistered || fusedSystemProviderRegistered
    }

    private fun registerTestProvider(providerName: String): Boolean {
        return try {
            try {
                locationManager.removeTestProvider(providerName)
            } catch (_: Exception) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val props = ProviderProperties.Builder()
                    .setHasNetworkRequirement(false)
                    .setHasSatelliteRequirement(false)
                    .setHasCellRequirement(false)
                    .setHasMonetaryCost(false)
                    .setHasAltitudeSupport(true)
                    .setHasBearingSupport(true)
                    .setHasSpeedSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
                locationManager.addTestProvider(providerName, props)
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    providerName,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
            }
            locationManager.setTestProviderEnabled(providerName, true)
            true
        } catch (se: SecurityException) {
            lastFailureWasSecurity = true
            lastProviderFailure = se.localizedMessage
            false
        } catch (e: Exception) {
            lastProviderFailure = e.localizedMessage ?: e.javaClass.simpleName
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureFusedMockModeSync(): Boolean {
        val client = fusedClient ?: return false
        if (fusedMockModeEnabled) return true
        return try {
            Tasks.await(client.setMockMode(true), 1500L, TimeUnit.MILLISECONDS)
            fusedMockModeEnabled = true
            lastFusedError = null
            ensureFusedVerificationUpdates(client)
            true
        } catch (e: Exception) {
            fusedMockModeEnabled = false
            lastFusedError = e.cause?.localizedMessage ?: e.localizedMessage ?: "Play Services mock mode unavailable"
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureFusedVerificationUpdates(client: FusedLocationProviderClient) {
        if (fusedUpdatesRegistered || !fusedMockModeEnabled) return
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateIntervalMs)
                .setMinUpdateIntervalMillis(250L)
                .build()
            client.requestLocationUpdates(req, fusedVerificationCallback, Looper.getMainLooper())
            fusedUpdatesRegistered = true
        } catch (_: Exception) {
            fusedUpdatesRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun pushToFusedProviderSync(coord: GeoCoordinate): Boolean {
        val client = fusedClient ?: return false
        if (!ensureFusedMockModeSync()) return false

        if (pendingFlushOnJump) {
            pendingFlushOnJump = false
            try {
                Tasks.await(client.flushLocations(), 800L, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
            }
        }

        val fusedLoc = buildMockLocation(LocationManager.GPS_PROVIDER, coord)
        return try {
            Tasks.await(client.setMockLocation(fusedLoc), 1500L, TimeUnit.MILLISECONDS)
            lastFusedError = null
            true
        } catch (_: Exception) {
            // Play Services occasionally drops mock mode when switching apps; re-enable and retry immediately
            fusedMockModeEnabled = false
            if (ensureFusedMockModeSync()) {
                try {
                    val retryLoc = buildMockLocation(LocationManager.GPS_PROVIDER, coord)
                    Tasks.await(client.setMockLocation(retryLoc), 1500L, TimeUnit.MILLISECONDS)
                    lastFusedError = null
                    true
                } catch (retryEx: Exception) {
                    lastFusedError = retryEx.cause?.localizedMessage ?: retryEx.localizedMessage
                    false
                }
            } else {
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readVerifiedDriftMeters(coord: GeoCoordinate): Double? {
        val client = fusedClient
        val reported = lastVerifiedLocation ?: try {
            if (client != null) {
                Tasks.await(client.lastLocation, 500L, TimeUnit.MILLISECONDS)
            } else null
        } catch (_: Exception) {
            null
        } ?: try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        } ?: return null

        return LocationRepository.distanceMeters(
            coord.latitude,
            coord.longitude,
            reported.latitude,
            reported.longitude
        )
    }

    @SuppressLint("MissingPermission")
    private fun pushCoordinateTick() {
        val coord = currentCoordinate
        var anyGps = false
        var anyNetwork = false
        var anySystemFused = false

        if (gpsProviderRegistered) {
            anyGps = pushToLocationManager(LocationManager.GPS_PROVIDER, coord)
        }
        if (networkProviderRegistered) {
            anyNetwork = pushToLocationManager(LocationManager.NETWORK_PROVIDER, coord)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fusedSystemProviderRegistered) {
            anySystemFused = pushToLocationManager(LocationManager.FUSED_PROVIDER, coord)
        }

        val playFusedOk = pushToFusedProviderSync(coord)
        val fusedOk = playFusedOk || anySystemFused
        val drift = readVerifiedDriftMeters(coord)

        if (anyGps || anyNetwork || fusedOk) {
            MockLocationEngineState.setRunning(
                coordinate = coord,
                gpsActive = anyGps,
                networkActive = anyNetwork,
                fusedActive = fusedOk,
                verifiedDriftMeters = drift,
                lastFusedError = lastFusedError
            )
        } else {
            MockLocationEngineState.setError(
                message = "Mock location permission was revoked in Developer Options.",
                requiresDevOptions = true
            )
            stopSelfCleanup()
        }
    }

    private fun pushToLocationManager(providerName: String, coord: GeoCoordinate): Boolean {
        return try {
            val location = buildMockLocation(providerName, coord)
            locationManager.setTestProviderLocation(providerName, location)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            // Re-register if provider was removed externally
            if (registerTestProvider(providerName)) {
                try {
                    locationManager.setTestProviderLocation(
                        providerName,
                        buildMockLocation(providerName, coord)
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildMockLocation(providerName: String, coord: GeoCoordinate): Location {
        return Location(providerName).apply {
            latitude = coord.latitude
            longitude = coord.longitude
            altitude = coord.altitudeMeters
            accuracy = coord.accuracyMeters.coerceAtLeast(1.0f)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            bearing = 0f
            speed = 0f
            extras = Bundle().apply {
                putInt("satellites", 12)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.1f
                verticalAccuracyMeters = 1.0f
                speedAccuracyMetersPerSecond = 0.01f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                elapsedRealtimeUncertaintyNanos = 0.0
            }
        }
    }

    private fun stopMockingAndSelf() {
        MockLocationEngineState.setStopped()
        stopSelfCleanup()
    }

    @SuppressLint("MissingPermission")
    private fun stopSelfCleanup() {
        mockLoopJob?.cancel()
        mockLoopJob = null
        lastVerifiedLocation = null

        if (gpsProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {
            }
            gpsProviderRegistered = false
        }

        if (networkProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
            }
            networkProviderRegistered = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fusedSystemProviderRegistered) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.FUSED_PROVIDER)
            } catch (_: Exception) {
            }
            fusedSystemProviderRegistered = false
        }

        if (fusedUpdatesRegistered) {
            try {
                fusedClient?.removeLocationUpdates(fusedVerificationCallback)
            } catch (_: Exception) {
            }
            fusedUpdatesRegistered = false
        }

        if (fusedMockModeEnabled) {
            try {
                fusedClient?.setMockMode(false)
            } catch (_: Exception) {
            }
            fusedMockModeEnabled = false
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mockLoopJob?.cancel()
        if (fusedUpdatesRegistered) {
            try {
                fusedClient?.removeLocationUpdates(fusedVerificationCallback)
            } catch (_: Exception) {
            }
            fusedUpdatesRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(coord: GeoCoordinate): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val coordText = String.format(Locale.US, "%.6f, %.6f", coord.latitude, coord.longitude)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PinShift Active • Mocking GPS")
            .setContentText("Pin B: $coordText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop_action),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 4042

        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_MOCK"
        const val ACTION_STOP = "com.example.action.STOP_MOCK"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ALTITUDE = "extra_altitude"
        const val EXTRA_ACCURACY = "extra_accuracy"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"

        fun startOrUpdate(
            context: Context,
            coordinate: GeoCoordinate,
            intervalMs: Long
        ) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_LATITUDE, coordinate.latitude)
                putExtra(EXTRA_LONGITUDE, coordinate.longitude)
                putExtra(EXTRA_ALTITUDE, coordinate.altitudeMeters)
                putExtra(EXTRA_ACCURACY, coordinate.accuracyMeters)
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
