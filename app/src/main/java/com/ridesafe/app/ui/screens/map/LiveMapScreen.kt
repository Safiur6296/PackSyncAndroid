package com.ridesafe.app.ui.screens.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.PackSyncTheme
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.LocationUtils
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/**
 * Creates a high-contrast directional puck for the current user ("YOU").
 * Designed for immediate glanceability through helmet visors:
 * - High-contrast concentric outer halo with live status color
 * - Solid high-contrast core
 * - Forward-pointing navigation heading chevron
 * - Live semantic status pip
 * - Glanceable "YOU" label
 */
private fun createCurrentUserMarkerBitmap(status: RiderStatus): Bitmap {
    val diameter = 94f
    val pointerHeight = 18f
    val totalHeight = diameter + pointerHeight
    val totalWidth = diameter

    val bitmap = Bitmap.createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = totalWidth / 2f
    val centerY = diameter / 2f

    // 1. Soft live-status outer pulse ring
    val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3330D158")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, 46f, outerGlowPaint)

    // 2. High-contrast solid dark puck center
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, 34f, bgPaint)

    // 3. Crisp outer border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FAFAFA")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(centerX, centerY, 34f, strokePaint)

    // 4. Directional heading chevron (pointing upward in puck)
    val chevronPath = Path().apply {
        moveTo(centerX, centerY - 18f)
        lineTo(centerX + 12f, centerY + 10f)
        lineTo(centerX, centerY + 4f)
        lineTo(centerX - 12f, centerY + 10f)
        close()
    }
    val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    canvas.drawPath(chevronPath, chevronPaint)

    // 5. Semantic status pip (Live green or status color)
    val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (status) {
            RiderStatus.RIDING -> android.graphics.Color.parseColor("#30D158")
            RiderStatus.EMERGENCY -> android.graphics.Color.parseColor("#FF453A")
            else -> android.graphics.Color.parseColor("#FFB300")
        }
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX + 24f, centerY - 24f, 8f, pipPaint)

    val pipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(centerX + 24f, centerY - 24f, 8f, pipBorderPaint)

    return bitmap
}

/**
 * Creates a map pin bitmap for fellow riders with their name and status.
 */
private fun createRiderMarkerBitmap(name: String, status: RiderStatus, isCurrentUser: Boolean): Bitmap {
    if (isCurrentUser) {
        return createCurrentUserMarkerBitmap(status)
    }

    val displayName = name.trim().ifEmpty { "Rider" }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(displayName)
    val horizontalPadding = 20f
    val pillWidth = (textWidth + horizontalPadding * 2f).coerceAtLeast(110f)
    val pillHeight = 56f
    val pointerHeight = 14f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val accentColor = when (status) {
        RiderStatus.RIDING -> android.graphics.Color.parseColor("#30D158")
        RiderStatus.EMERGENCY -> android.graphics.Color.parseColor("#FF453A")
        else -> android.graphics.Color.parseColor("#FFB300")
    }

    // Pill background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1C1C1E")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val rect = RectF(2f, 2f, pillWidth - 2f, pillHeight - 2f)
    canvas.drawRoundRect(rect, 28f, 28f, bgPaint)
    canvas.drawRoundRect(rect, 28f, 28f, strokePaint)

    // Inverted pointer
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 10f, pillHeight - 2f)
        lineTo(centerX + 10f, pillHeight - 2f)
        lineTo(centerX, totalHeight - 1f)
        close()
    }
    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pointerPath, pointerPaint)

    // Name text
    val textX = (pillWidth - textWidth) / 2f
    val textBaseline = pillHeight / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(displayName, textX, textBaseline, textPaint)

    return bitmap
}

/**
 * Creates custom waypoint pins for Start and Destination points.
 * - Start point: Restrained, compact 44f waypoint circle with center dot (NO giant billboard card overlapping "YOU").
 * - Destination: High-contrast checkered flag pin with destination name.
 */
private fun createTripMarkerBitmap(name: String, isDestination: Boolean): Bitmap {
    if (!isDestination) {
        // Restrained start waypoint node: clean circle ring that never obscures the rider puck
        val size = 48f
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#A1A1AA")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#141416")
            style = Paint.Style.FILL
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FAFAFA")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 4f, bgPaint)
        canvas.drawCircle(center, center, center - 4f, ringPaint)
        canvas.drawCircle(center, center, 6f, dotPaint)
        return bitmap
    }

    // Destination Waypoint Pin
    val placeName = name.trim().ifEmpty { "Destination" }
    val displayName = "🏁 $placeName"

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(displayName)
    val horizontalPadding = 24f
    val pillWidth = (textWidth + horizontalPadding * 2f).coerceAtLeast(130f)
    val pillHeight = 64f
    val pointerHeight = 16f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val strokeColor = android.graphics.Color.parseColor("#00B0FF")
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    val rect = RectF(3f, 3f, pillWidth - 3f, pillHeight - 3f)
    canvas.drawRoundRect(rect, 32f, 32f, bgPaint)
    canvas.drawRoundRect(rect, 32f, 32f, strokePaint)

    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 10f, pillHeight - 3f)
        lineTo(centerX + 10f, pillHeight - 3f)
        lineTo(centerX, totalHeight - 1f)
        close()
    }
    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pointerPath, pointerPaint)

    val textX = (pillWidth - textWidth) / 2f
    val textY = pillHeight / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(displayName, textX, textY, textPaint)

    return bitmap
}

/**
 * Zooms and pans camera to fit all active convoy riders and the planned route.
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
 * LiveMapScreen — Redesigned HMI Live Ride Map for PackSync
 */
@Composable
fun LiveMapScreen(
    viewModel: MapViewModel,
    onLeaveRide: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val markerBitmapCache = remember { mutableMapOf<String, Bitmap>() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredInitialLocation by remember { mutableStateOf(false) }
    var isRouteVisible by remember { mutableStateOf(true) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.getDir("osmdroid", Context.MODE_PRIVATE)
            osmdroidTileCache = context.getDir("osmdroid_tiles", Context.MODE_PRIVATE)
        }
    }

    // Center camera initially on device GPS or route
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !hasCenteredInitialLocation) {
                    if (uiState.routePoints.isNotEmpty()) {
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
                    } else {
                        mapView?.controller?.let { controller ->
                            controller.setZoom(15.5)
                            controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        }
                    }
                    hasCenteredInitialLocation = true
                }
            }
        } catch (_: SecurityException) {}
    }

    // Auto-fit route when route points load
    LaunchedEffect(mapView, uiState.routePoints) {
        if (mapView != null && uiState.routePoints.isNotEmpty()) {
            zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            hasCenteredInitialLocation = true
        }
    }

    val currentRider = uiState.riders.find { it.isCurrentUser }
    LaunchedEffect(currentRider?.rider?.lat, currentRider?.rider?.lng) {
        val lat = currentRider?.rider?.lat ?: 0.0
        val lng = currentRider?.rider?.lng ?: 0.0
        if (!hasCenteredInitialLocation && lat != 0.0 && lng != 0.0) {
            if (uiState.routePoints.isNotEmpty()) {
                zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            } else {
                mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 800L)
            }
            hasCenteredInitialLocation = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BikerDarkBg)) {
        val riders = uiState.riders
        val tripInfo = uiState.tripInfo
        val routePoints = uiState.routePoints

        // 1. Full-Screen Interactive OpenStreetMap Canvas
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(28.6139, 77.2090))
                    onResume()
                    mapView = this
                }
            },
            update = { mv ->
                mv.overlays.clear()

                // Draw planned route polyline with protective casing
                if (routePoints.isNotEmpty() && isRouteVisible) {
                    val routePolyline = OsmPolyline(mv).apply {
                        setPoints(routePoints)
                        outlinePaint.apply {
                            color = android.graphics.Color.parseColor("#00B0FF")
                            strokeWidth = 16f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                    }
                    mv.overlays.add(routePolyline)
                }

                // Add Start point marker (restrained, no overlap with rider)
                if (tripInfo != null && tripInfo.isTripPlanned && isRouteVisible && tripInfo.startLat != 0.0 && tripInfo.startLng != 0.0) {
                    val startPos = GeoPoint(tripInfo.startLat, tripInfo.startLng)
                    val startKey = "start_${tripInfo.startName}"
                    val startBitmap = markerBitmapCache.getOrPut(startKey) {
                        createTripMarkerBitmap(tripInfo.startName, isDestination = false)
                    }
                    val startMarker = OsmMarker(mv).apply {
                        position = startPos
                        title = "Start: ${tripInfo.startName}"
                        icon = BitmapDrawable(mv.context.resources, startBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                    }
                    mv.overlays.add(startMarker)
                }

                // Add Destination marker
                if (tripInfo != null && tripInfo.isTripPlanned && isRouteVisible && tripInfo.destLat != 0.0 && tripInfo.destLng != 0.0) {
                    val destPos = GeoPoint(tripInfo.destLat, tripInfo.destLng)
                    val destKey = "dest_${tripInfo.destName}"
                    val destBitmap = markerBitmapCache.getOrPut(destKey) {
                        createTripMarkerBitmap(tripInfo.destName, isDestination = true)
                    }
                    val destMarker = OsmMarker(mv).apply {
                        position = destPos
                        title = "Destination: ${tripInfo.destName}"
                        icon = BitmapDrawable(mv.context.resources, destBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                    }
                    mv.overlays.add(destMarker)
                }

                // Add Rider Markers
                riders.forEach { riderItem ->
                    val rider = riderItem.rider
                    if (rider.lat != 0.0 && rider.lng != 0.0) {
                        val position = GeoPoint(rider.lat, rider.lng)
                        val status = rider.riderStatus
                        val cacheKey = "${rider.id}_${rider.name}_${status.name}_${riderItem.isCurrentUser}"
                        val markerBitmap = markerBitmapCache.getOrPut(cacheKey) {
                            createRiderMarkerBitmap(rider.name, status, riderItem.isCurrentUser)
                        }

                        val marker = OsmMarker(mv).apply {
                            this.position = position
                            this.title = if (riderItem.isCurrentUser) "${rider.name} (You)" else rider.name
                            this.snippet = "Status: ${status.displayName} • ${riderItem.formattedDistance}"
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

                mv.invalidate()
            }
        )

        // 2. Consolidated Top HUD Bar (Replaces the 3 stacked cards with 1 slim area)
        ConsolidatedTopHud(
            rideCode = uiState.rideCode,
            riderCount = uiState.riders.size,
            tripInfo = uiState.tripInfo,
            emergencyRiders = uiState.riders.filter { !it.isCurrentUser && it.rider.riderStatus == RiderStatus.EMERGENCY },
            onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PackSync Code", uiState.rideCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Ride code copied: ${uiState.rideCode}", Toast.LENGTH_SHORT).show()
            },
            onFitRoute = {
                isRouteVisible = true
                zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            },
            onLeaveClick = { showLeaveConfirmDialog = true },
            onLocateEmergencyRider = { emRider ->
                val lat = emRider.rider.lat
                val lng = emRider.rider.lng
                if (lat != 0.0 && lng != 0.0) {
                    mapView?.controller?.animateTo(GeoPoint(lat, lng), 17.0, 1000L)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // 3. Compact Collapsed / Expandable Rider Radar (44dp pill by default)
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
                .padding(start = 16.dp, end = 16.dp, bottom = 86.dp)
        )

        // 4. Ergonomic Bottom Thumb Dock (MY STATUS Hero + 3 Circular Action Buttons)
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
                            }
                        }
                    } catch (_: SecurityException) {}
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )

        // 5. Redesigned Update Ride Status Bottom Sheet
        if (uiState.isStatusPickerOpen) {
            StopStatusDialog(
                currentStatus = uiState.myStatus,
                onStatusSelected = { newStatus ->
                    viewModel.setRiderStatus(newStatus)
                },
                onDismiss = { viewModel.closeStatusPicker() }
            )
        }

        // 6. Rider List Roster Sheet
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

        // 7. Accidental-Tap Prevention: Leave Convoy Confirmation Dialog
        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = PackSyncTheme.colors.surface,
                title = {
                    Text(
                        text = "Leave Convoy?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PackSyncTheme.colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "You will disconnect from live navigation and group convoy telemetry (${uiState.rideCode}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PackSyncTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLeaveConfirmDialog = false
                            viewModel.leaveRide(onLeaveComplete = onLeaveRide)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PackSyncTheme.colors.destructiveRed
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Leave Convoy", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showLeaveConfirmDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PackSyncTheme.colors.border)
                    ) {
                        Text("Stay in Convoy", color = PackSyncTheme.colors.textSecondary)
                    }
                }
            )
        }
    }
}

/**
 * Consolidated Top HUD:
 * - Upper auxiliary strip: Ride code (with tap-to-copy), live rider count, overflow menu (hold-to-leave)
 * - Single Primary Navigation Bar: Destination name, remaining distance & duration, and Fit Route button
 * - Integrated emergency alert bar when companion rider needs assistance
 */
@Composable
private fun ConsolidatedTopHud(
    rideCode: String,
    riderCount: Int,
    tripInfo: TripInfo?,
    emergencyRiders: List<RiderWithDistance>,
    onCopyCode: () -> Unit,
    onFitRoute: () -> Unit,
    onLeaveClick: () -> Unit,
    onLocateEmergencyRider: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upper Auxiliary Meta Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Title / Lead Arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PackSync",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = PackSyncTheme.colors.textSecondary
                )
            }

            // Right-aligned capsules: Ride Code + Rider Count + Overflow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Monospaced Ride Code Capsule
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PackSyncTheme.colors.surface,
                    border = BorderStroke(1.5.dp, PackSyncTheme.colors.border),
                    modifier = Modifier.clickable(onClick = onCopyCode)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CODE:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PackSyncTheme.colors.textTertiary
                        )
                        Text(
                            text = rideCode.ifEmpty { "BIKE44" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = PackSyncTheme.colors.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = PackSyncTheme.colors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Rider Count Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PackSyncTheme.colors.surface,
                    border = BorderStroke(1.5.dp, PackSyncTheme.colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Convoy",
                            tint = PackSyncTheme.colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$riderCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PackSyncTheme.colors.textPrimary
                        )
                    }
                }

                // Overflow Menu Button (Holding Leave Option)
                Box {
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PackSyncTheme.colors.surface)
                            .border(1.5.dp, PackSyncTheme.colors.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = PackSyncTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leave Convoy", color = PackSyncTheme.colors.destructiveRed, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    tint = PackSyncTheme.colors.destructiveRed
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onLeaveClick()
                            }
                        )
                    }
                }
            }
        }

        // Primary Navigation Card (Destination + Metrics + Fit Route Action)
        val destinationName = tripInfo?.destName?.trim()?.ifEmpty { "Malda Convoy Destination" } ?: "Malda Convoy Destination"
        val distanceDurationText = if (tripInfo != null && tripInfo.formattedDistance.isNotBlank()) {
            if (tripInfo.formattedDuration.isNotBlank()) {
                "${tripInfo.formattedDistance} • ${tripInfo.formattedDuration}"
            } else {
                tripInfo.formattedDistance
            }
        } else {
            "321 km • 4 hr 15 min"
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(1.5.dp, PackSyncTheme.colors.border),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destinationName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PackSyncTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = distanceDurationText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = PackSyncTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tactile Fit Route Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PackSyncTheme.colors.surfaceRaised,
                    border = BorderStroke(1.dp, PackSyncTheme.colors.border),
                    modifier = Modifier.clickable(onClick = onFitRoute)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit Route",
                            tint = PackSyncTheme.colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "FIT ROUTE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = PackSyncTheme.colors.textPrimary
                        )
                    }
                }
            }
        }

        // Inline Emergency Alert Pill if any companion rider needs help
        val primaryEmergency = emergencyRiders.firstOrNull()
        if (primaryEmergency != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PackSyncTheme.colors.destructiveRed.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, PackSyncTheme.colors.destructiveRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🚨", fontSize = 16.sp)
                        Column {
                            Text(
                                text = "EMERGENCY: ${primaryEmergency.rider.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = PackSyncTheme.colors.destructiveRed
                            )
                            Text(
                                text = "${primaryEmergency.formattedDistance} away",
                                fontSize = 10.sp,
                                color = PackSyncTheme.colors.textPrimary
                            )
                        }
                    }

                    Button(
                        onClick = { onLocateEmergencyRider(primaryEmergency) },
                        colors = ButtonDefaults.buttonColors(containerColor = PackSyncTheme.colors.destructiveRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("LOCATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * RiderRadarPanel:
 * - Collapsed by default into a compact 44dp pill
 * - Expands on tap or swipe up to display telemetry for all convoy riders
 */
@Composable
fun RiderRadarPanel(
    riders: List<RiderWithDistance>,
    onRiderClick: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val fellowRiders = riders.filter { !it.isCurrentUser }
    var isExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = PackSyncTheme.colors.surface,
        border = BorderStroke(1.5.dp, PackSyncTheme.colors.border),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header bar (Pill mode when collapsed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PackSyncTheme.colors.liveGreen.copy(alpha = 0.2f * pulseAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = PackSyncTheme.colors.liveGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "CONVOY RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = PackSyncTheme.colors.textPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PackSyncTheme.colors.surfaceRaised,
                        border = BorderStroke(1.dp, PackSyncTheme.colors.border)
                    ) {
                        Text(
                            text = if (fellowRiders.isEmpty()) "Solo" else "${fellowRiders.size} in Pack",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PackSyncTheme.colors.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isExpanded) {
                        Text(
                            text = if (fellowRiders.isEmpty()) "0 Nearby" else "View",
                            fontSize = 11.sp,
                            color = PackSyncTheme.colors.textTertiary
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = PackSyncTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Telemetry Roster
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (fellowRiders.isEmpty()) {
                        Text(
                            text = "Waiting for other riders to join convoy...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PackSyncTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 180.dp)
                        ) {
                            fellowRiders.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PackSyncTheme.colors.surfaceRaised)
                                        .border(1.dp, PackSyncTheme.colors.border, RoundedCornerShape(12.dp))
                                        .clickable { onRiderClick(item) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Ahead / Behind arrow badge
                                    when (item.isAhead) {
                                        true -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(PackSyncTheme.colors.liveGreen.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Ahead",
                                                    tint = PackSyncTheme.colors.liveGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        false -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Behind",
                                                    tint = Color(0xFFFFB300),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        null -> {
                                            Text(text = item.rider.riderStatus.emoji, fontSize = 14.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.relativePositionText,
                                            fontWeight = FontWeight.Bold,
                                            color = PackSyncTheme.colors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                        if (item.rider.speed > 0f) {
                                            Text(
                                                text = "${LocationUtils.formatSpeed(item.rider.speed)} • ${item.rider.riderStatus.displayName}",
                                                color = PackSyncTheme.colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.rider.riderStatus.emoji,
                                        fontSize = 16.sp
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
 * BottomControlDock:
 * - "MY STATUS" Hero button on the left (largest control, 58dp height, heavy green border)
 * - Three 56dp glove-friendly circular action buttons: Riders List, Route Toggle, Recenter on Me
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
        // "MY STATUS" Hero Pill Button
        val isRiding = myStatus == RiderStatus.RIDING
        val statusAccent = if (isRiding) PackSyncTheme.colors.liveGreen else myStatus.color

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(2.dp, statusAccent),
            shadowElevation = 8.dp,
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clickable(onClick = onStatusClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Status",
                        tint = statusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "MY STATUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = PackSyncTheme.colors.textTertiary
                    )
                    Text(
                        text = myStatus.displayName.uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = statusAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Riders List Button
        IconButton(
            onClick = onRiderListClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(PackSyncTheme.colors.surface)
                .border(1.5.dp, PackSyncTheme.colors.border, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Riders List",
                tint = PackSyncTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Route Directions Toggle Button
        val isRouteActive = hasPlannedRoute && isRouteVisible
        IconButton(
            onClick = onToggleRouteClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isRouteActive) PackSyncTheme.colors.surfaceRaised else PackSyncTheme.colors.surface)
                .border(
                    width = 1.5.dp,
                    color = if (isRouteActive) Color(0xFF00B0FF) else PackSyncTheme.colors.border,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                contentDescription = "Route",
                tint = if (isRouteActive) Color(0xFF00B0FF) else PackSyncTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // 3. Recenter on Me Button
        IconButton(
            onClick = onRecenterClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(PackSyncTheme.colors.surface)
                .border(1.5.dp, PackSyncTheme.colors.liveGreen.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter",
                tint = PackSyncTheme.colors.liveGreen,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
