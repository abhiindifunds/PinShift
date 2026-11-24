package com.example

import android.app.Application
import com.example.data.local.PinShiftDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.NominatimApi
import com.example.data.repository.LocationRepository
import org.osmdroid.config.Configuration
import java.io.File

class PinShiftApplication : Application() {

    lateinit var locationRepository: LocationRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize osmdroid configuration cleanly and store tiles inside app cacheDir
        val osmBase = File(cacheDir, "osmdroid").apply { mkdirs() }
        val osmTiles = File(osmBase, "tiles").apply { mkdirs() }

        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        osmConfig.userAgentValue = "$packageName/1.0"
        osmConfig.osmdroidBasePath = osmBase
        osmConfig.osmdroidTileCache = osmTiles
        osmConfig.isMapViewHardwareAccelerated = true
        osmConfig.cacheMapTileCount = 16
        osmConfig.tileDownloadThreads = 4
        osmConfig.tileFileSystemThreads = 4

        val database = PinShiftDatabase.getInstance(this)
        val dataStore = SettingsDataStore(this)
        val nominatimApi = NominatimApi.create("PinShift-Android/1.0 ($packageName)")

        locationRepository = LocationRepository(
            context = this,
            savedLocationDao = database.savedLocationDao(),
            settingsDataStore = dataStore,
            nominatimApi = nominatimApi
        )
    }
}
