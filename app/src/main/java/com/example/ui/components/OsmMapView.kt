package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.GeoCoordinate
import com.example.ui.MapCameraCommand
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalCoral
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private class OsmMapHolder {
    var mapView: MapView? = null
    var targetMarker: Marker? = null
    var realMarker: Marker? = null
    var routeLine: Polyline? = null
    var isProgrammaticCameraMove: Boolean = false
    var lastHandledCameraCommandId: Long? = null
    var lastHandledZoomCommandId: Long? = null
    var lastDarkMapTiles: Boolean? = null
    var lastTargetPin: GeoCoordinate? = null
    var lastRealLocation: GeoCoordinate? = null
    var lastIsMockActive: Boolean? = null
    var lastCrosshairLock: Boolean? = null
}

@Composable
fun OsmMapView(
    targetPin: GeoCoordinate,
    realLocation: GeoCoordinate?,
    isMockActive: Boolean,
    darkMapTiles: Boolean,
    crosshairLockEnabled: Boolean,
    cameraCommand: MapCameraCommand?,
    zoomStepCommand: Pair<Long, Double>?,
    onMapTapped: (Double, Double) -> Unit,
    onCrosshairCenterChanged: (Double, Double) -> Unit,
    onCameraCommandConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val currentOnMapTapped by rememberUpdatedState(onMapTapped)
    val currentOnCrosshairCenterChanged by rememberUpdatedState(onCrosshairCenterChanged)
    val currentOnCameraCommandConsumed by rememberUpdatedState(onCameraCommandConsumed)
    val currentCrosshairLock by rememberUpdatedState(crosshairLockEnabled)

    val holder = remember { OsmMapHolder() }

    val pinBActiveDrawable = remember(context) {
        createPinDrawable(
            context = context,
            fillColor = ElectricCyan.toArgb(),
            haloColor = ElectricCyan.copy(alpha = 0.28f).toArgb(),
            isActive = true
        )
    }
    val pinBIdleDrawable = remember(context) {
        createPinDrawable(
            context = context,
            fillColor = SignalCoral.toArgb(),
            haloColor = SignalCoral.copy(alpha = 0.24f).toArgb(),
            isActive = false
        )
    }
    val pinADrawable = remember(context) {
        createOriginDotDrawable(context = context)
    }

    val darkTileFilter = remember { createDarkCartographyColorFilter() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.mapView?.onDetach()
            holder.mapView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 20.5
                isTilesScaledToDpi = true

                controller.setZoom(15.5)
                controller.setCenter(GeoPoint(targetPin.latitude, targetPin.longitude))

                val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                            currentOnMapTapped(p.latitude, p.longitude)
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                            currentOnMapTapped(p.latitude, p.longitude)
                        }
                        return true
                    }
                })
                overlays.add(eventsOverlay)

                val line = Polyline(this).apply {
                    outlinePaint.color = ElectricCyan.copy(alpha = 0.7f).toArgb()
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.isAntiAlias = true
                }
                overlays.add(line)
                holder.routeLine = line

                val originMarker = Marker(this).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = pinADrawable
                    title = "Real Location A"
                }
                overlays.add(originMarker)
                holder.realMarker = originMarker

                val destMarker = Marker(this).apply {
                    setAnchor(Marker.ANCHOR_CENTER, 46f / 52f)
                    icon = if (isMockActive) pinBActiveDrawable else pinBIdleDrawable
                    title = "Mock Target Pin B"
                    position = GeoPoint(targetPin.latitude, targetPin.longitude)
                }
                overlays.add(destMarker)
                holder.targetMarker = destMarker

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        if (currentCrosshairLock && !holder.isProgrammaticCameraMove) {
                            val center = mapCenter
                            currentOnCrosshairCenterChanged(center.latitude, center.longitude)
                        }
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        if (currentCrosshairLock && !holder.isProgrammaticCameraMove) {
                            val center = mapCenter
                            currentOnCrosshairCenterChanged(center.latitude, center.longitude)
                        }
                        return false
                    }
                })

                holder.mapView = this
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onResume()
                }
            }
        },
        update = { mapView ->
            var needsInvalidate = false

            if (holder.lastDarkMapTiles != darkMapTiles) {
                holder.lastDarkMapTiles = darkMapTiles
                mapView.overlayManager.tilesOverlay.apply {
                    setColorFilter(if (darkMapTiles) darkTileFilter else null)
                    setLoadingBackgroundColor(
                        if (darkMapTiles) android.graphics.Color.rgb(11, 18, 28)
                        else android.graphics.Color.rgb(226, 232, 240)
                    )
                    setLoadingLineColor(
                        if (darkMapTiles) android.graphics.Color.rgb(30, 41, 59)
                        else android.graphics.Color.rgb(203, 213, 225)
                    )
                }
                needsInvalidate = true
            }

            if (holder.lastTargetPin != targetPin ||
                holder.lastIsMockActive != isMockActive ||
                holder.lastCrosshairLock != crosshairLockEnabled
            ) {
                holder.targetMarker?.apply {
                    position = GeoPoint(targetPin.latitude, targetPin.longitude)
                    icon = if (isMockActive) pinBActiveDrawable else pinBIdleDrawable
                    isEnabled = !crosshairLockEnabled
                }
                holder.lastIsMockActive = isMockActive
                holder.lastCrosshairLock = crosshairLockEnabled
                needsInvalidate = true
            }

            if (holder.lastRealLocation != realLocation || holder.lastTargetPin != targetPin) {
                if (realLocation != null) {
                    holder.realMarker?.apply {
                        position = GeoPoint(realLocation.latitude, realLocation.longitude)
                        isEnabled = true
                    }
                    holder.routeLine?.apply {
                        setPoints(
                            listOf(
                                GeoPoint(realLocation.latitude, realLocation.longitude),
                                GeoPoint(targetPin.latitude, targetPin.longitude)
                            )
                        )
                        isEnabled = true
                    }
                } else {
                    holder.realMarker?.isEnabled = false
                    holder.routeLine?.isEnabled = false
                }
                holder.lastRealLocation = realLocation
                needsInvalidate = true
            }

            holder.lastTargetPin = targetPin

            if (cameraCommand != null && holder.lastHandledCameraCommandId != cameraCommand.id) {
                holder.lastHandledCameraCommandId = cameraCommand.id
                val target = GeoPoint(cameraCommand.latitude, cameraCommand.longitude)
                holder.isProgrammaticCameraMove = true
                mapView.post {
                    if (cameraCommand.zoom != null) {
                        if (mapView.isLayoutOccurred) {
                            mapView.controller.animateTo(target, cameraCommand.zoom, 350L)
                        } else {
                            mapView.controller.setZoom(cameraCommand.zoom)
                            mapView.controller.setCenter(target)
                        }
                    } else {
                        if (mapView.isLayoutOccurred) {
                            mapView.controller.animateTo(target)
                        } else {
                            mapView.controller.setCenter(target)
                        }
                    }
                    currentOnCameraCommandConsumed()
                    scope.launch {
                        delay(420L)
                        holder.isProgrammaticCameraMove = false
                    }
                }
            }

            if (zoomStepCommand != null && holder.lastHandledZoomCommandId != zoomStepCommand.first) {
                holder.lastHandledZoomCommandId = zoomStepCommand.first
                val nextZoom = (mapView.zoomLevelDouble + zoomStepCommand.second).coerceIn(3.0, 20.5)
                mapView.controller.setZoom(nextZoom)
                if (currentCrosshairLock) {
                    val center = mapView.mapCenter
                    currentOnCrosshairCenterChanged(center.latitude, center.longitude)
                }
            }

            if (needsInvalidate) {
                mapView.invalidate()
            }
        }
    )
}

private fun createDarkCartographyColorFilter(): ColorMatrixColorFilter {
    val invertAndHue = ColorMatrix(
        floatArrayOf(
            -0.38f, -0.72f, 0.12f, 0f, 232f,
            -0.21f, -0.55f, -0.22f, 0f, 236f,
            0.14f, -0.58f, -0.45f, 0f, 245f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(invertAndHue)
}

private fun createPinDrawable(
    context: Context,
    fillColor: Int,
    haloColor: Int,
    isActive: Boolean
): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (44 * density).toInt()
    val height = (52 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val headCy = 18 * density
    val radius = 12 * density

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = haloColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, height - 8 * density, if (isActive) 13 * density else 9 * density, haloPaint)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        addCircle(cx, headCy, radius, Path.Direction.CW)
        moveTo(cx - radius * 0.85f, headCy + radius * 0.45f)
        lineTo(cx, height - 6 * density)
        lineTo(cx + radius * 0.85f, headCy + radius * 0.45f)
        close()
    }
    canvas.drawPath(path, pinPaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, headCy, 5f * density, innerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createOriginDotDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (26 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f

    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(65, 59, 130, 246)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, c, outer)

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 7f * density, ring)

    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(59, 130, 246)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, 4.5f * density, core)

    return BitmapDrawable(context.resources, bitmap)
}
