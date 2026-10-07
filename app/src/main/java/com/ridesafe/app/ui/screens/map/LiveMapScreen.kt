package com.ridesafe.app.ui.screens.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ridesafe.app.data.model.JoinRequest
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import com.google.android.gms.location.LocationServices
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.ui.theme.BikerAmber
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.StatusRed
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.LocationUtils
import androidx.compose.material.icons.automirrored.filled.AltRoute
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.ui.theme.StatusBlue
import com.ridesafe.app.ui.theme.StatusGreen
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/**
 * Creates a glowing amber circular badge for the current user ("You").
 * Matches the premium dark cockpit amber theme with concentric glow rings,
 * electric amber border, status emoji, "YOU" condensed label, and an amber pointer pin.
 */
private fun createCurrentUserMarkerBitmap(status: RiderStatus): Bitmap {
    val diameter = 90f
    val pointerHeight = 18f
    val totalHeight = diameter + pointerHeight
    val totalWidth = diameter

    val bitmap = Bitmap.createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = totalWidth / 2f
    val centerY = diameter / 2f

    // 1. Soft outer amber glow
    val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#26FFB300")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, 44f, outerGlowPaint)

    // 2. Middle amber glow ring
    val midGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#4DFFB300")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, 38f, midGlowPaint)

    // 3. Dark glassmorphic badge center
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#14171E")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, 32f, bgPaint)

    // 4. Solid electric amber circular border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFB300")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    canvas.drawCircle(centerX, centerY, 32f, strokePaint)

    // 5. Inverted pointer pin pointing to exact GPS coordinate
    val pointerPath = Path().apply {
        moveTo(centerX - 8f, centerY + 28f)
        lineTo(centerX + 8f, centerY + 28f)
        lineTo(centerX, totalHeight - 2f)
        close()
    }
    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFB300")
        style = Paint.Style.FILL
    }
    canvas.drawPath(pointerPath, pointerPaint)

    // 6. Draw Status emoji at center
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    val emojiBaseline = centerY - 4f - ((emojiPaint.descent() + emojiPaint.ascent()) / 2f)
    canvas.drawText(status.emoji, centerX, emojiBaseline, emojiPaint)

    // 7. Draw "YOU" condensed label
    val youTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        color = android.graphics.Color.parseColor("#FFB300")
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("YOU", centerX, centerY + 20f, youTextPaint)

    return bitmap
}

/**
 * Creates a custom map pin bitmap with the rider's name and status emoji.
 * Displays e.g. "🏍️ Rahul (You)" or "⛽ Sahil" inside a sleek rounded pill
 * with an inverted pointer triangle pointing to the GPS coordinate.
 */
private fun createRiderMarkerBitmap(name: String, status: RiderStatus, isCurrentUser: Boolean): Bitmap {
    if (isCurrentUser) {
        return createCurrentUserMarkerBitmap(status)
    }

    val displayName = name.trim().ifEmpty { "Rider" }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        textAlign = Paint.Align.LEFT
    }

    val textWidth = textPaint.measureText(displayName)
    val horizontalPadding = 26f
    val emojiWidth = 46f
    val spacing = 12f
    val contentWidth = emojiWidth + spacing + textWidth

    val pillWidth = (contentWidth + horizontalPadding * 2f).coerceAtLeast(130f)
    val pillHeight = 74f
    val pointerHeight = 22f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#171A21")
        style = Paint.Style.FILL
    }

    val strokeColor = if (isCurrentUser) {
        android.graphics.Color.parseColor("#FFC107")
    } else {
        status.color.toArgb()
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = if (isCurrentUser) 5f else 3.5f
    }

    val fillAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }

    // Draw rounded badge rectangle
    val rect = RectF(4f, 4f, pillWidth - 4f, pillHeight - 4f)
    canvas.drawRoundRect(rect, 36f, 36f, bgPaint)
    canvas.drawRoundRect(rect, 36f, 36f, strokePaint)

    // Draw pointer pin triangle at bottom center
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 14f, pillHeight - 4f)
        lineTo(centerX + 14f, pillHeight - 4f)
        lineTo(centerX, totalHeight - 2f)
        close()
    }
    canvas.drawPath(pointerPath, fillAccentPaint)

    // Draw status emoji
    val startX = (pillWidth - contentWidth) / 2f
    val emojiBaseline = pillHeight / 2f - ((emojiPaint.descent() + emojiPaint.ascent()) / 2f)
    canvas.drawText(status.emoji, startX, emojiBaseline, emojiPaint)

    // Draw name text
    val textStartX = startX + emojiWidth + spacing
    val textBaseline = pillHeight / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(displayName, textStartX, textBaseline, textPaint)

    return bitmap
}

/**
 * Creates a distinctive custom pin for Start and Destination markers on the map,
 * with bright border accents (green for Start, coral red for Destination)
 * and label text displaying the location name.
 */
private fun createTripMarkerBitmap(name: String, isDestination: Boolean): Bitmap {
    val prefix = if (isDestination) "🏁" else "🚩"
    val label = if (isDestination) "DESTINATION" else "START"
    val placeName = name.trim().ifEmpty { if (isDestination) "Destination" else "Start" }
    val displayName = "$prefix $placeName"

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = if (isDestination) android.graphics.Color.parseColor("#FFC107") else android.graphics.Color.parseColor("#69F0AE")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(displayName)
    val labelWidth = labelPaint.measureText(label)
    val contentWidth = maxOf(textWidth, labelWidth)
    val horizontalPadding = 26f

    val pillWidth = (contentWidth + horizontalPadding * 2f).coerceAtLeast(140f)
    val pillHeight = 78f
    val pointerHeight = 20f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgColor = android.graphics.Color.parseColor("#171A21")
    val strokeColor = if (isDestination) {
        android.graphics.Color.parseColor("#FF5252") // Coral Red
    } else {
        android.graphics.Color.parseColor("#00E676") // Emerald Green
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val fillAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }

    // Draw pill badge
    val rect = RectF(4f, 4f, pillWidth - 4f, pillHeight - 4f)
    canvas.drawRoundRect(rect, 36f, 36f, bgPaint)
    canvas.drawRoundRect(rect, 36f, 36f, strokePaint)

    // Draw bottom pointer pin
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 12f, pillHeight - 4f)
        lineTo(centerX + 12f, pillHeight - 4f)
        lineTo(centerX, totalHeight - 2f)
        close()
    }
    canvas.drawPath(pointerPath, fillAccentPaint)

    // Draw small uppercase label
    val labelX = (pillWidth - labelWidth) / 2f
    canvas.drawText(label, labelX, 28f, labelPaint)

    // Draw display name
    val textX = (pillWidth - textWidth) / 2f
    val textY = 62f
    canvas.drawText(displayName, textX, textY, textPaint)

    return bitmap
}

/**
 * Zooms and pans the camera to tightly enclose all active riders AND the planned route points.
 */
private fun zoomToFitContent(
    mapView: MapView?,
    riders: List<RiderWithDistance>,
    routePoints: List<GeoPoint>,
    tripInfo: TripInfo?
) {
    val mv = mapView ?: return
    val additionalPoints = mutableListOf<GeoPoint>()
    riders.forEach { r ->
        if (r.rider.lat != 0.0 && r.rider.lng != 0.0) {
            additionalPoints.add(GeoPoint(r.rider.lat, r.rider.lng))
        }
    }
    if (tripInfo != null && tripInfo.isTripPlanned) {
        if (tripInfo.startLat != 0.0 && tripInfo.startLng != 0.0) {
            additionalPoints.add(GeoPoint(tripInfo.startLat, tripInfo.startLng))
        }
        if (tripInfo.destLat != 0.0 && tripInfo.destLng != 0.0) {
            additionalPoints.add(GeoPoint(tripInfo.destLat, tripInfo.destLng))
        }
    }

    val boundingBox = PolylineUtils.calculateRouteBoundingBox(routePoints, additionalPoints)
    if (boundingBox != null) {
        if (mv.width == 0 || mv.height == 0) {
            mv.post {
                try {
                    mv.zoomToBoundingBox(boundingBox, true, 130)
                } catch (e: Exception) {
                    mv.controller?.setCenter(boundingBox.centerWithDateLine)
                }
            }
        } else {
            try {
                mv.zoomToBoundingBox(boundingBox, true, 130)
            } catch (e: Exception) {
                mv.controller?.setCenter(boundingBox.centerWithDateLine)
            }
        }
    }
}


/**
 * LiveMapScreen is the main in-ride dashboard.
 * Shows all riders on an OpenStreetMap (osmdroid) map in real time, current stop statuses,
 * and quick-access controls for group communication.
 *
 * The map uses osmdroid's MapView wrapped in Compose's AndroidView interop.
 * osmdroid loads free OpenStreetMap tiles — no API key or billing account needed.
 */
@Composable
fun LiveMapScreen(
    viewModel: MapViewModel,
    onLeaveRide: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Cache marker bitmaps by unique rider identity so we don't recreate them every recomposition
    val markerBitmapCache = remember { mutableMapOf<String, Bitmap>() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Hold a reference to the osmdroid MapView so we can control it from Compose callbacks
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredInitialLocation by remember { mutableStateOf(false) }
    var isRouteVisible by remember { mutableStateOf(true) }

    // Configure osmdroid ONCE before the MapView is created.
    // This sets the User-Agent (required by OpenStreetMap tile servers) and
    // tile cache paths (using app-internal storage to avoid needing WRITE_EXTERNAL_STORAGE).
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Store tiles in app-private directories — works on all Android versions
            // without needing WRITE_EXTERNAL_STORAGE permission
            osmdroidBasePath = context.getDir("osmdroid", Context.MODE_PRIVATE)
            osmdroidTileCache = context.getDir("osmdroid_tiles", Context.MODE_PRIVATE)
        }
    }

    // Center camera immediately on the device's real GPS position or route
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                android.util.Log.d("RideSafeDebug", "[MapRender] LiveMapScreen lastLocation: loc=$loc, hasCentered=$hasCenteredInitialLocation")
                if (loc != null && !hasCenteredInitialLocation) {
                    if (uiState.routePoints.isNotEmpty()) {
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
                        hasCenteredInitialLocation = true
                    } else {
                        mapView?.controller?.let { controller ->
                            controller.setZoom(15.5)
                            controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        }
                        hasCenteredInitialLocation = true
                    }
                    android.util.Log.d("RideSafeDebug", "[MapRender] Camera centered on real GPS position: ${loc.latitude}, ${loc.longitude}")
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[MapRender] SecurityException on lastLocation: ${e.message}", e)
        }
    }

    // Automatically zoom to fit route when route points load or map becomes ready
    LaunchedEffect(mapView, uiState.routePoints) {
        if (mapView != null && uiState.routePoints.isNotEmpty()) {
            zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            hasCenteredInitialLocation = true
        }
    }


    // Also update camera when current rider GPS coordinates arrive from Firebase or local sensor
    val currentRider = uiState.riders.find { it.isCurrentUser }
    LaunchedEffect(currentRider?.rider?.lat, currentRider?.rider?.lng) {
        val lat = currentRider?.rider?.lat ?: 0.0
        val lng = currentRider?.rider?.lng ?: 0.0
        android.util.Log.d("RideSafeDebug", "[MapRender] LaunchedEffect currentRider coords: lat=$lat, lng=$lng, hasCentered=$hasCenteredInitialLocation")
        if (!hasCenteredInitialLocation && lat != 0.0 && lng != 0.0) {
            if (uiState.routePoints.isNotEmpty()) {
                zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            } else {
                mapView?.controller?.let { controller ->
                    controller.setZoom(16.0)
                    controller.animateTo(GeoPoint(lat, lng), 16.0, 800L)
                }
            }
            hasCenteredInitialLocation = true
            android.util.Log.d("RideSafeDebug", "[MapRender] Camera animated to current rider position: ($lat, $lng)")
        }
    }

    // Clean up osmdroid MapView lifecycle when this composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BikerDarkBg)) {
        // Extract rider & trip data so Compose tracks them as dependencies for recomposition.
        val riders = uiState.riders
        val tripInfo = uiState.tripInfo
        val routePoints = uiState.routePoints

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    // Use standard OpenStreetMap tiles (Mapnik style)
                    setTileSource(TileSourceFactory.MAPNIK)
                    // Enable pinch-to-zoom and two-finger rotate
                    setMultiTouchControls(true)
                    // Disable the default +/- zoom buttons (we have our own controls)
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                    // Set initial zoom and center (will be overridden by GPS / route)
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(28.6139, 77.2090))

                    // Start the map's tile loading
                    onResume()

                    // Store reference for use in Compose callbacks
                    mapView = this
                }
            },
            update = { mv ->
                // Clear all existing overlays and re-add from current state.
                mv.overlays.clear()
                android.util.Log.d("RideSafeDebug", "[MapRender] Updating map overlays for ${riders.size} riders, routePoints=${routePoints.size}:")

                // 1. Draw planned route polyline if present and visible (drawn UNDER markers)
                if (routePoints.isNotEmpty() && isRouteVisible) {
                    val routePolyline = OsmPolyline(mv).apply {
                        setPoints(routePoints)
                        outlinePaint.apply {
                            color = android.graphics.Color.parseColor("#00B0FF") // Electric route blue
                            strokeWidth = 16f // 6-8dp width
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        title = if (tripInfo != null && tripInfo.isTripPlanned) {
                            "Route: ${tripInfo.formattedDistance} (${tripInfo.formattedDuration})"
                        } else {
                            "Planned Route"
                        }
                    }
                    mv.overlays.add(routePolyline)
                }

                // 2. Add Start point marker if trip planned and visible
                if (tripInfo != null && tripInfo.isTripPlanned && isRouteVisible && tripInfo.startLat != 0.0 && tripInfo.startLng != 0.0) {
                    val startPos = GeoPoint(tripInfo.startLat, tripInfo.startLng)
                    val startKey = "start_${tripInfo.startName}"
                    val startBitmap = markerBitmapCache.getOrPut(startKey) {
                        createTripMarkerBitmap(tripInfo.startName, isDestination = false)
                    }
                    val startMarker = OsmMarker(mv).apply {
                        position = startPos
                        title = "Start: ${tripInfo.startName}"
                        snippet = "Convoy Departure Point"
                        icon = BitmapDrawable(mv.context.resources, startBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { marker, _ ->
                            marker.showInfoWindow()
                            true
                        }
                    }
                    mv.overlays.add(startMarker)
                }

                // 3. Add Destination point marker if trip planned and visible
                if (tripInfo != null && tripInfo.isTripPlanned && isRouteVisible && tripInfo.destLat != 0.0 && tripInfo.destLng != 0.0) {
                    val destPos = GeoPoint(tripInfo.destLat, tripInfo.destLng)
                    val destKey = "dest_${tripInfo.destName}"
                    val destBitmap = markerBitmapCache.getOrPut(destKey) {
                        createTripMarkerBitmap(tripInfo.destName, isDestination = true)
                    }
                    val destMarker = OsmMarker(mv).apply {
                        position = destPos
                        title = "Destination: ${tripInfo.destName}"
                        snippet = if (tripInfo.formattedDistance.isNotEmpty()) {
                            "Total Distance: ${tripInfo.formattedDistance} (${tripInfo.formattedDuration})"
                        } else {
                            "Convoy Destination"
                        }
                        icon = BitmapDrawable(mv.context.resources, destBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { marker, _ ->
                            marker.showInfoWindow()
                            true
                        }
                    }
                    mv.overlays.add(destMarker)
                }

                // 4. Add Rider markers (on top)
                riders.forEach { riderItem ->
                    val rider = riderItem.rider
                    if (rider.lat != 0.0 && rider.lng != 0.0) {
                        val position = GeoPoint(rider.lat, rider.lng)
                        val status = rider.riderStatus

                        val titleText = if (riderItem.isCurrentUser) {
                            "${rider.name} (You) ${status.emoji}"
                        } else {
                            "${rider.name} ${status.emoji}"
                        }

                        val snippetText = if (riderItem.isCurrentUser) {
                            "Status: ${status.displayName}"
                        } else {
                            "Status: ${status.displayName} • ${riderItem.formattedDistance} • ${LocationUtils.formatTimeAgo(rider.lastUpdated)}"
                        }

                        val cacheKey = "${rider.id}_${rider.name}_${status.name}_${riderItem.isCurrentUser}"
                        val markerBitmap = markerBitmapCache.getOrPut(cacheKey) {
                            createRiderMarkerBitmap(rider.name, status, riderItem.isCurrentUser)
                        }

                        val marker = OsmMarker(mv).apply {
                            this.position = position
                            this.title = titleText
                            this.snippet = snippetText
                            this.icon = BitmapDrawable(mv.context.resources, markerBitmap)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { clickedMarker, _ ->
                                viewModel.selectRider(riderItem)
                                clickedMarker.showInfoWindow()
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }

                // Trigger a redraw so all new overlays appear immediately
                mv.invalidate()
            }
        )

        // 2. Top Header Panels: Ride Code + Horizontal Floating Trip Bar + Emergency Alert
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top-left floating pill (Ride Code) + Top-right circular badges (Rider count & Leave)
            TopRideBar(
                rideCode = uiState.rideCode,
                riderCount = uiState.riders.size,
                onCopyCode = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("PackSync Code", uiState.rideCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Ride code copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                onLeaveClick = {
                    viewModel.leaveRide(onLeaveComplete = onLeaveRide)
                }
            )

            // Horizontal floating bar: Route fork icon, destination name, distance · duration, and FIT ROUTE text link
            if (tripInfo != null && tripInfo.isTripPlanned) {
                TripOverviewBanner(
                    tripInfo = tripInfo,
                    onFitRouteClick = {
                        isRouteVisible = true
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, tripInfo)
                    }
                )
            }

            // Emergency Alert Banner: Displays when any other convoy rider sets status to EMERGENCY
            val emergencyRiders = uiState.riders.filter { !it.isCurrentUser && it.rider.riderStatus == RiderStatus.EMERGENCY }
            if (emergencyRiders.isNotEmpty()) {
                EmergencyAlertBanner(
                    emergencyRiders = emergencyRiders,
                    onLocateRider = { emergencyRider ->
                        val lat = emergencyRider.rider.lat
                        val lng = emergencyRider.rider.lng
                        if (lat != 0.0 && lng != 0.0) {
                            mapView?.controller?.animateTo(GeoPoint(lat, lng), 17.0, 1000L)
                        }
                    }
                )
            }
        }

        // 3. Expandable Rider Radar Panel: shows relative distance and ahead/behind status for all riders
        RiderRadarPanel(
            riders = uiState.riders,
            onRiderClick = { riderItem ->
                val lat = riderItem.rider.lat
                val lng = riderItem.rider.lng
                if (lat != 0.0 && lng != 0.0) {
                    mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
        )

        // 4. Bottom Status Bar: Status Pill on left + Three circular icon buttons on right
        BottomControlDock(
            myStatus = uiState.myStatus,
            isRouteVisible = isRouteVisible,
            hasPlannedRoute = tripInfo != null && tripInfo.isTripPlanned,
            onStatusClick = { viewModel.openStatusPicker() },
            onRiderListClick = { viewModel.openRiderList() },
            onToggleRouteClick = {
                if (tripInfo != null && tripInfo.isTripPlanned) {
                    isRouteVisible = !isRouteVisible
                    if (isRouteVisible) {
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, tripInfo)
                        Toast.makeText(context, "Route visible", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Route hidden", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No route planned for this convoy", Toast.LENGTH_SHORT).show()
                }
            },
            onRecenterClick = {
                val lat = currentRider?.rider?.lat ?: 0.0
                val lng = currentRider?.rider?.lng ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
                } else {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                mapView?.controller?.animateTo(
                                    GeoPoint(loc.latitude, loc.longitude), 16.0, 1000L
                                )
                            } else {
                                Toast.makeText(context, "Acquiring GPS location...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "Location permission needed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )

        // 5. Stop Status Picker Modal
        if (uiState.isStatusPickerOpen) {
            StopStatusDialog(
                currentStatus = uiState.myStatus,
                onStatusSelected = { newStatus ->
                    viewModel.setRiderStatus(newStatus)
                },
                onDismiss = { viewModel.closeStatusPicker() }
            )
        }

        // 6. Rider List Bottom Sheet
        if (uiState.isRiderListOpen) {
            RiderListBottomSheet(
                riders = uiState.riders,
                onRiderClick = { riderItem ->
                    viewModel.closeRiderList()
                    val lat = riderItem.rider.lat
                    val lng = riderItem.rider.lng
                    if (lat != 0.0 && lng != 0.0) {
                        mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
                    }
                },
                onDismiss = { viewModel.closeRiderList() }
            )
        }

        // 7. Join Request Approval Pop-up Dialog (Fix 2)
        uiState.activeJoinRequest?.let { req ->
            JoinRequestApprovalDialog(
                request = req,
                timeRemainingSeconds = uiState.joinRequestTimeRemaining,
                rideCode = uiState.rideCode,
                onApprove = { viewModel.approveJoinRequest(req) },
                onDecline = { viewModel.declineJoinRequest(req) },
                onCancel = { viewModel.dismissJoinRequest() }
            )
        }
    }
}

/**
 * In-app pop-up dialog presented to the leader/riders for 2 minutes when someone requests to join (Fix 2).
 */
@Composable
fun JoinRequestApprovalDialog(
    request: JoinRequest,
    timeRemainingSeconds: Int,
    rideCode: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    val minutes = timeRemainingSeconds / 60
    val seconds = timeRemainingSeconds % 60
    val timeFormatted = String.format("%d:%02d", minutes, seconds)
    val progress = (timeRemainingSeconds / 120f).coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(20.dp),
        containerColor = BikerSurfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BikerAmber.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BikerAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Join Request",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BikerCardBg,
                    border = BorderStroke(1.dp, BikerBorder)
                ) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = BikerAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${request.riderName} wants to join your convoy ($rideCode).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BikerAmber,
                    trackColor = BikerBorder
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Request will auto-expire in $timeFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel / Dismiss button (Fix 2: "there should be cancel button too")
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    border = BorderStroke(1.dp, BikerBorder)
                ) {
                    Text("Dismiss", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                // Decline button
                Button(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed.copy(alpha = 0.2f),
                        contentColor = StatusRed
                    ),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f))
                ) {
                    Text("Decline", fontWeight = FontWeight.Bold, maxLines = 1)
                }

                // Approve button
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Approve", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    )
}

/**
 * Top floating header bar:
 * - Top-left: Glassmorphic floating pill with "RIDE CODE" label, large bold amber code in condensed typography, and copy icon
 * - Top-right: Circular rider-count badge (person icon + number) and circular exit/leave button with red-tinted outline
 */
@Composable
private fun TopRideBar(
    rideCode: String,
    riderCount: Int,
    onCopyCode: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val condensedFont = remember {
        FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Top-left floating pill: "RIDE CODE" label above large bold amber code with copy icon
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = BikerAmber.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xEE14171E))
                .border(1.dp, BikerAmber.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .clickable(onClick = onCopyCode)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RIDE CODE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = rideCode.ifEmpty { "CREW47" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = condensedFont,
                    color = BikerAmber,
                    letterSpacing = 2.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy ride code",
                tint = BikerAmber.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 2. Top-right: circular rider-count badge + circular exit/leave button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Circular rider-count badge (person icon + number)
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .defaultMinSize(minWidth = 44.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = BikerAmber.copy(alpha = 0.2f),
                        ambientColor = Color.Black
                    )
                    .clip(CircleShape)
                    .background(Color(0xEE14171E))
                    .border(1.dp, BikerAmber.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Riders",
                        tint = BikerAmber,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$riderCount",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }

            // Circular exit/leave icon button with a red-tinted outline
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = StatusRed.copy(alpha = 0.3f),
                        ambientColor = Color.Black
                    )
                    .clip(CircleShape)
                    .background(Color(0xEE14171E))
                    .border(1.5.dp, StatusRed.copy(alpha = 0.65f), CircleShape)
                    .clickable(onClick = onLeaveClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Leave Ride",
                    tint = StatusRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Emergency Alert Banner showing which rider needs immediate help and their distance,
 * styled in dark glassmorphism with vivid red pulse and quick locate action.
 */
@Composable
private fun EmergencyAlertBanner(
    emergencyRiders: List<RiderWithDistance>,
    onLocateRider: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryEmergency = emergencyRiders.firstOrNull() ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = StatusRed.copy(alpha = 0.35f),
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEE14171E))
            .background(StatusRed.copy(alpha = 0.16f))
            .border(1.5.dp, StatusRed, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StatusRed.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚨", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "EMERGENCY: ${primaryEmergency.rider.name.ifEmpty { "Rider" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = StatusRed
                    )
                    Text(
                        text = "Needs help! ${primaryEmergency.formattedDistance} away",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Button(
                onClick = { onLocateRider(primaryEmergency) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "LOCATE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Horizontal floating bar:
 * - Route-fork icon
 * - Destination name (truncated with ellipsis if long)
 * - "distance · duration" in smaller text
 * - "FIT ROUTE" text link in amber on the right
 */
@Composable
private fun TripOverviewBanner(
    tripInfo: TripInfo?,
    onFitRouteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destinationName = if (tripInfo != null && tripInfo.destName.isNotBlank()) {
        tripInfo.destName
    } else {
        "Convoy Destination"
    }

    val distanceDurationText = if (tripInfo != null && tripInfo.formattedDistance.isNotBlank()) {
        if (tripInfo.formattedDuration.isNotBlank()) {
            "${tripInfo.formattedDistance} · ${tripInfo.formattedDuration}"
        } else {
            tripInfo.formattedDistance
        }
    } else {
        "Route Navigation"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = BikerAmber.copy(alpha = 0.2f),
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEE14171E))
            .border(1.dp, BikerAmber.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left & Middle: Route-fork icon + Destination name + Distance · Duration
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route fork icon badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(StatusBlue.copy(alpha = 0.15f))
                        .border(1.dp, StatusBlue.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = "Route Fork",
                        tint = StatusBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = destinationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = distanceDurationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: "FIT ROUTE" text link in amber
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFitRouteClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "FIT ROUTE",
                    color = BikerAmber,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * Expandable "RIDER RADAR" panel:
 * - Header row with pulse/radar icon, "RIDER RADAR" label, and rider-count pill, with chevron to expand/collapse
 * - When empty: shows muted text "Waiting for other riders to join..."
 * - When active: lists riders with distance, speed, and ahead/behind indicators
 */
@Composable
fun RiderRadarPanel(
    riders: List<RiderWithDistance>,
    onRiderClick: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val fellowRiders = riders.filter { !it.isCurrentUser }
    var isExpanded by remember { mutableStateOf(true) }

    // Pulsing animation for the radar icon to feel alive and responsive
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = BikerAmber.copy(alpha = 0.2f),
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xEE14171E))
            .border(1.dp, BikerAmber.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar with Expand / Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulse / Radar icon
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(BikerAmber.copy(alpha = 0.2f * pulseAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = BikerAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RIDER RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Rider-count pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(BikerAmber.copy(alpha = 0.15f))
                            .border(1.dp, BikerAmber.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${fellowRiders.size} ${if (fellowRiders.size == 1) "RIDER" else "RIDERS"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BikerAmber,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Chevron to expand/collapse
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (fellowRiders.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Waiting for other riders to join...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 160.dp)
                        ) {
                            fellowRiders.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BikerSurfaceElevated.copy(alpha = 0.7f))
                                        .border(1.dp, BikerBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .clickable { onRiderClick(item) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Direction Badge / Arrow
                                    when (item.isAhead) {
                                        true -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1B5E20)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Ahead",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        false -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE65100)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Behind",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        null -> {
                                            Text(
                                                text = item.rider.riderStatus.emoji,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.relativePositionText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        if (item.rider.speed > 0f) {
                                            Text(
                                                text = "Speed: ${LocationUtils.formatSpeed(item.rider.speed)} • ${item.rider.riderStatus.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = item.rider.riderStatus.color,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.rider.riderStatus.emoji,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible alias for RiderRadarPanel
 */
@Composable
fun RiderProximityBox(
    riders: List<RiderWithDistance>,
    onRiderClick: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    RiderRadarPanel(riders = riders, onRiderClick = onRiderClick, modifier = modifier)
}

/**
 * Bottom status bar:
 * - "MY STATUS" pill on the left showing current status (e.g. green-outlined "Riding")
 * - Three circular icon buttons on the right: rider list, route toggle, recenter-on-me
 */
@Composable
private fun BottomControlDock(
    myStatus: RiderStatus,
    isRouteVisible: Boolean,
    hasPlannedRoute: Boolean,
    onStatusClick: () -> Unit,
    onRiderListClick: () -> Unit,
    onToggleRouteClick: () -> Unit,
    onRecenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "MY STATUS" pill on the left (e.g. green-outlined "Riding")
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(percent = 50),
                    spotColor = myStatus.color.copy(alpha = 0.35f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xEE14171E))
                .border(
                    width = 1.5.dp,
                    color = myStatus.color.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(percent = 50)
                )
                .clickable(onClick = onStatusClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(myStatus.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = myStatus.emoji, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "MY STATUS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = myStatus.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = myStatus.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Three circular icon buttons on the right:
        // 1. Rider list button
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = BikerAmber.copy(alpha = 0.2f),
                    ambientColor = Color.Black
                )
                .clip(CircleShape)
                .background(Color(0xEE14171E))
                .border(1.dp, BikerAmber.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onRiderListClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Riders List",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Route toggle button
        val isRouteActive = hasPlannedRoute && isRouteVisible
        val routeBorderColor = if (isRouteActive) StatusBlue.copy(alpha = 0.85f) else BikerBorder
        val routeIconColor = if (isRouteActive) StatusBlue else TextMuted
        val routeBgTint = if (isRouteActive) StatusBlue.copy(alpha = 0.14f) else Color.Transparent

        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = if (isRouteActive) StatusBlue.copy(alpha = 0.3f) else Color.Transparent,
                    ambientColor = Color.Black
                )
                .clip(CircleShape)
                .background(Color(0xEE14171E))
                .background(routeBgTint)
                .border(1.dp, routeBorderColor, CircleShape)
                .clickable(onClick = onToggleRouteClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                contentDescription = "Toggle Route",
                tint = routeIconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        // 3. Recenter-on-me button
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = BikerAmber.copy(alpha = 0.35f),
                    ambientColor = Color.Black
                )
                .clip(CircleShape)
                .background(Color(0xEE14171E))
                .background(BikerAmber.copy(alpha = 0.08f))
                .border(1.5.dp, BikerAmber.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onRecenterClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter on Me",
                tint = BikerAmber,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun TopRideBarPreview() {
    RideSafeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TopRideBar(
                rideCode = "CREW47",
                riderCount = 5,
                onCopyCode = {},
                onLeaveClick = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun BottomControlDockPreview() {
    RideSafeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            BottomControlDock(
                myStatus = RiderStatus.RIDING,
                isRouteVisible = true,
                hasPlannedRoute = true,
                onStatusClick = {},
                onRiderListClick = {},
                onToggleRouteClick = {},
                onRecenterClick = {}
            )
        }
    }
}
