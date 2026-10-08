package com.ridesafe.app.ui.screens.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.ridesafe.app.R
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.PackSyncTheme
import com.ridesafe.app.util.LocationUtils
import com.ridesafe.app.util.PolylineUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/**
 * Maps RiderStatus to its exact hexadecimal color integer.
 */
private fun getStatusColorInt(status: RiderStatus): Int {
    return when (status) {
        RiderStatus.RIDING -> android.graphics.Color.parseColor("#00E676")     // Green
        RiderStatus.REFUELING -> android.graphics.Color.parseColor("#FFB300")  // Amber
        RiderStatus.PUNCTURE -> android.graphics.Color.parseColor("#FF7043")   // Orange
        RiderStatus.REST -> android.graphics.Color.parseColor("#40C4FF")       // Blue
        RiderStatus.OTHER -> android.graphics.Color.parseColor("#B0BEC5")      // Grey
        RiderStatus.EMERGENCY -> android.graphics.Color.parseColor("#FF1744")  // Red
    }
}

/**
 * Draws the vector status icon inside a circular badge on Android Canvas.
 */
private fun drawStatusIconOnCanvas(canvas: Canvas, cx: Float, cy: Float, status: RiderStatus, colorInt: Int) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }

    when (status) {
        RiderStatus.RIDING -> {
            // Motorcycle: 2 wheels + frame + handlebar
            canvas.drawCircle(cx - 4.2f, cy + 2.5f, 2.2f, strokePaint)
            canvas.drawCircle(cx + 4.2f, cy + 2.5f, 2.2f, strokePaint)
            canvas.drawLine(cx - 4.2f, cy + 2.5f, cx - 0.5f, cy - 1.5f, strokePaint)
            canvas.drawLine(cx - 0.5f, cy - 1.5f, cx + 4.2f, cy + 2.5f, strokePaint)
            canvas.drawLine(cx + 1.2f, cy - 3.8f, cx + 4f, cy - 3.8f, strokePaint)
        }
        RiderStatus.REFUELING -> {
            // Gas pump: body rect + hose
            canvas.drawRoundRect(RectF(cx - 4f, cy - 4.5f, cx + 1.5f, cy + 4.5f), 1.5f, 1.5f, strokePaint)
            val hose = Path().apply {
                moveTo(cx + 1.5f, cy - 2.5f)
                lineTo(cx + 4.2f, cy - 2.5f)
                lineTo(cx + 4.2f, cy + 2.5f)
            }
            canvas.drawPath(hose, strokePaint)
        }
        RiderStatus.PUNCTURE -> {
            // Wrench: diagonal shaft + jaw
            canvas.drawLine(cx - 3.5f, cy + 3.5f, cx + 1.8f, cy - 1.8f, strokePaint.apply { strokeWidth = 2.4f })
            val jaw = Path().apply {
                moveTo(cx + 0.8f, cy - 3.8f)
                lineTo(cx + 3.8f, cy - 0.8f)
                lineTo(cx + 4.5f, cy - 2.8f)
                lineTo(cx + 2.8f, cy - 4.5f)
                close()
            }
            canvas.drawPath(jaw, fillPaint)
        }
        RiderStatus.REST -> {
            // Coffee cup: body + handle + steam
            canvas.drawRoundRect(RectF(cx - 3.5f, cy - 1.8f, cx + 2.2f, cy + 4.2f), 1.5f, 1.5f, strokePaint)
            val handle = Path().apply {
                moveTo(cx + 2.2f, cy - 0.5f)
                quadTo(cx + 4.5f, cy + 1f, cx + 2.2f, cy + 2.8f)
            }
            canvas.drawPath(handle, strokePaint)
            canvas.drawLine(cx - 1.8f, cy - 4.2f, cx - 1.8f, cy - 2.8f, strokePaint)
            canvas.drawLine(cx + 0.5f, cy - 4.2f, cx + 0.5f, cy - 2.8f, strokePaint)
        }
        RiderStatus.EMERGENCY -> {
            // Exclamation shield
            canvas.drawLine(cx, cy - 4.5f, cx, cy + 0.5f, strokePaint.apply { strokeWidth = 2.2f })
            canvas.drawCircle(cx, cy + 3.2f, 1.3f, fillPaint)
        }
        RiderStatus.OTHER -> {
            // Warning triangle
            val triangle = Path().apply {
                moveTo(cx, cy - 4.5f)
                lineTo(cx + 4.5f, cy + 3.8f)
                lineTo(cx - 4.5f, cy + 3.8f)
                close()
            }
            canvas.drawPath(triangle, strokePaint)
            canvas.drawCircle(cx, cy + 1.5f, 1.1f, fillPaint)
        }
    }
}

/**
 * Creates the directional rotating puck for the current user:
 * - Soft translucent heading cone pointing forward (angle 0° / UP)
 * - Soft live pulse glow
 * - Dark puck core with high-contrast border
 * - Navigation chevron pointing UP (rotates smoothly to phone direction)
 */
private fun createCurrentUserArrowBitmap(): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // 1. Soft translucent heading cone pointing forward (centered around 270° / UP)
    val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3300E676")
        style = Paint.Style.FILL
    }
    val conePath = Path().apply {
        moveTo(cx, cy)
        arcTo(RectF(cx - 52f, cy - 52f, cx + 52f, cy + 52f), 242f, 56f, false)
        close()
    }
    canvas.drawPath(conePath, conePaint)

    // 2. Soft live pulse glow
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#2600E676")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 32f, glowPaint)

    // 3. High-contrast solid dark puck center
    val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 22f, puckPaint)

    // 4. Crisp outer border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FAFAFA")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(cx, cy, 22f, strokePaint)

    // 5. Directional heading chevron (pointing upward in puck)
    val chevronPath = Path().apply {
        moveTo(cx, cy - 14f)
        lineTo(cx + 9f, cy + 10f)
        lineTo(cx, cy + 4f)
        lineTo(cx - 9f, cy + 10f)
        close()
    }
    val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    canvas.drawPath(chevronPath, chevronPaint)

    return bitmap
}

/**
 * Creates the UNROTATED status badge for the current user's marker.
 * Sits at the top-right corner of the puck, stays readable at any zoom level,
 * and does NOT rotate with the heading arrow.
 */
private fun createCurrentUserBadgeBitmap(status: RiderStatus): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Top-right corner of the 22f puck
    val bx = cx + 18f
    val by = cy - 18f
    val badgeRadius = 12f
    val statusColorInt = getStatusColorInt(status)

    // Solid dark circular backing for high contrast
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(bx, by, badgeRadius, bgPaint)

    // Status tinted border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = statusColorInt
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(bx, by, badgeRadius, borderPaint)

    // Status icon drawn inside, tinted with status color
    drawStatusIconOnCanvas(canvas, bx, by, status, statusColorInt)

    return bitmap
}

/**
 * Creates a map pin bitmap for fellow riders with their name and top-right status badge.
 */
private fun createRiderMarkerBitmap(name: String, status: RiderStatus): Bitmap {
    val displayName = name.trim().ifEmpty { "Rider" }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(displayName)
    val horizontalPadding = 24f
    val pillWidth = (textWidth + horizontalPadding * 2f).coerceAtLeast(120f)
    val pillHeight = 56f
    val pointerHeight = 14f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt() + 16, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val accentColor = getStatusColorInt(status)

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
    val rect = RectF(2f, 10f, pillWidth - 2f, pillHeight)
    canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
    canvas.drawRoundRect(rect, 24f, 24f, strokePaint)

    // Inverted pointer
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 10f, pillHeight - 1f)
        lineTo(centerX + 10f, pillHeight - 1f)
        lineTo(centerX, totalHeight - 1f)
        close()
    }
    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pointerPath, pointerPaint)

    // Name text
    val textX = (pillWidth - textWidth) / 2f - 4f
    val textBaseline = 10f + (pillHeight - 10f) / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(displayName, textX, textBaseline, textPaint)

    // Top-right circular status badge
    val bx = pillWidth - 6f
    val by = 12f
    val badgeRadius = 12f

    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#141416")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(bx, by, badgeRadius, badgeBgPaint)

    val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(bx, by, badgeRadius, badgeBorderPaint)

    drawStatusIconOnCanvas(canvas, bx, by, status, accentColor)

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
    val isDarkTheme = isSystemInDarkTheme()

    val markerBitmapCache = remember { mutableMapOf<String, Bitmap>() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var currentUserArrowMarker by remember { mutableStateOf<OsmMarker?>(null) }
    var hasCenteredInitialLocation by remember { mutableStateOf(false) }
    var isRouteVisible by remember { mutableStateOf(true) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    // Compass and Heading State
    var compassHeading by remember { mutableFloatStateOf(0f) }
    var currentSmoothedHeading by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.getDir("osmdroid", Context.MODE_PRIVATE)
            osmdroidTileCache = context.getDir("osmdroid_tiles", Context.MODE_PRIVATE)
        }
    }

    // Adapt map basemap to system dark / light mode
    LaunchedEffect(mapView, isDarkTheme) {
        val mv = mapView ?: return@LaunchedEffect
        if (isDarkTheme) {
            val darkMatrix = ColorMatrix(floatArrayOf(
                -0.85f, 0f, 0f, 0f, 235f,
                0f, -0.85f, 0f, 0f, 235f,
                0f, 0f, -0.85f, 0f, 235f,
                0f, 0f, 0f, 1f, 0f
            ))
            mv.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))
        } else {
            mv.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mv.invalidate()
    }

    // Register rotation vector / compass sensor listener
    DisposableEffect(sensorManager) {
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        val listener = object : SensorEventListener {
            private var lastTimestamp = 0L
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val now = SystemClock.uptimeMillis()
                // Throttle sensor intake to ~20 fps (50ms)
                if (now - lastTimestamp < 50L) return
                lastTimestamp = now

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val degrees = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                    compassHeading = degrees
                } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                    compassHeading = (event.values[0] + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Determine target heading: GPS bearing if speed > ~2 m/s, else device compass
    val targetHeading = if (uiState.userSpeed > 2.0f && uiState.userBearing != null && uiState.userBearing!! >= 0f) {
        uiState.userBearing!!
    } else {
        compassHeading
    }

    // Heading animation loop: 10-15 fps maximum (~66ms frame time), low-pass filter along shortest angular path
    LaunchedEffect(Unit) {
        while (isActive) {
            var diff = (targetHeading - currentSmoothedHeading) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f

            if (kotlin.math.abs(diff) > 0.25f) {
                currentSmoothedHeading = (currentSmoothedHeading + diff * 0.25f + 360f) % 360f
                currentUserArrowMarker?.rotation = currentSmoothedHeading
                mapView?.invalidate()
            }
            delay(66L) // ~15 fps limit to prevent battery drain and jitter
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
                    if (isDarkTheme) {
                        val darkMatrix = ColorMatrix(floatArrayOf(
                            -0.85f, 0f, 0f, 0f, 235f,
                            0f, -0.85f, 0f, 0f, 235f,
                            0f, 0f, -0.85f, 0f, 235f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))
                    }
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

                // Add Start point marker
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

                // Add Rider Markers with status badges and heading rotation
                riders.forEach { riderItem ->
                    val rider = riderItem.rider
                    if (rider.lat != 0.0 && rider.lng != 0.0) {
                        val position = GeoPoint(rider.lat, rider.lng)
                        val status = if (riderItem.isCurrentUser) uiState.myStatus else rider.riderStatus

                        if (riderItem.isCurrentUser) {
                            // Current user: Directional rotating arrow puck + UNROTATED top-right status badge
                            val arrowBitmap = markerBitmapCache.getOrPut("user_arrow") {
                                createCurrentUserArrowBitmap()
                            }
                            val badgeKey = "user_badge_${status.name}"
                            val badgeBitmap = markerBitmapCache.getOrPut(badgeKey) {
                                createCurrentUserBadgeBitmap(status)
                            }

                            val arrowMarker = OsmMarker(mv).apply {
                                this.position = position
                                this.title = "${rider.name} (You)"
                                this.snippet = "Status: ${status.displayName}"
                                this.icon = BitmapDrawable(mv.context.resources, arrowBitmap)
                                this.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                this.rotation = currentSmoothedHeading
                                setOnMarkerClickListener { _, _ ->
                                    viewModel.selectRider(riderItem)
                                    false
                                }
                            }
                            currentUserArrowMarker = arrowMarker
                            mv.overlays.add(arrowMarker)

                            val badgeMarker = OsmMarker(mv).apply {
                                this.position = position
                                this.title = "Status: ${status.displayName}"
                                this.icon = BitmapDrawable(mv.context.resources, badgeBitmap)
                                this.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                this.rotation = 0f
                                setOnMarkerClickListener { _, _ ->
                                    viewModel.selectRider(riderItem)
                                    false
                                }
                            }
                            mv.overlays.add(badgeMarker)
                        } else {
                            // Fellow rider: Pin with status badge on top-right corner
                            val cacheKey = "rider_${rider.id}_${rider.name}_${status.name}"
                            val markerBitmap = markerBitmapCache.getOrPut(cacheKey) {
                                createRiderMarkerBitmap(rider.name, status)
                            }

                            val marker = OsmMarker(mv).apply {
                                this.position = position
                                this.title = rider.name
                                this.snippet = "Status: ${status.displayName} • ${riderItem.formattedDistance}"
                                this.icon = BitmapDrawable(mv.context.resources, markerBitmap)
                                this.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { clickedMarker, _ ->
                                    viewModel.selectRider(riderItem)
                                    clickedMarker.showInfoWindow()
                                    true
                                }
                            }
                            mv.overlays.add(marker)
                        }
                    }
                }

                mv.invalidate()
            }
        )

        // 2. Redesigned Clean Header Row (Logo + Code + Riders + Menu)
        PackSyncHeader(
            rideCode = uiState.rideCode,
            riderCount = uiState.riders.size,
            onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PackSync Code", uiState.rideCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Ride code copied: ${uiState.rideCode}", Toast.LENGTH_SHORT).show()
            },
            onLeaveClick = { showLeaveConfirmDialog = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Companion Emergency Alert Banner if any fellow rider triggers SOS
        val emergencyRider = uiState.riders.firstOrNull { !it.isCurrentUser && it.rider.riderStatus == RiderStatus.EMERGENCY }
        if (emergencyRider != null) {
            EmergencyAlertBanner(
                emergencyRider = emergencyRider,
                onLocate = {
                    val lat = emergencyRider.rider.lat
                    val lng = emergencyRider.rider.lng
                    if (lat != 0.0 && lng != 0.0) {
                        mapView?.controller?.animateTo(GeoPoint(lat, lng), 17.0, 1000L)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        // 3. Ergonomic Bottom Thumb Area (Trip Container + Bottom Control Dock)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Repositioned Trip Container (directly above bottom bar)
            TripContainerCard(
                tripInfo = tripInfo,
                onFitRoute = {
                    isRouteVisible = true
                    zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
                }
            )

            // Bottom Control Dock (My Status + 3 Circular Action Buttons)
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
                }
            )
        }

        // 4. Update Ride Status Bottom Sheet
        if (uiState.isStatusPickerOpen) {
            StopStatusDialog(
                currentStatus = uiState.myStatus,
                onStatusSelected = { newStatus ->
                    viewModel.setRiderStatus(newStatus)
                },
                onDismiss = { viewModel.closeStatusPicker() }
            )
        }

        // 5. Rider List Roster Sheet
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

        // 6. Leave Convoy Confirmation Dialog
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
 * Clean Single Aligned Header Row:
 * [small PackSync logo mark] [Code pill: small "CODE" label + monospaced code + copy icon] [Riders pill: icon + count] [Menu button]
 * - All elements: 48dp height, 14dp corner radius, same surface fill and border, 12dp gaps, 16dp margins.
 * - Code pill takes flexible space and never wraps.
 */
@Composable
private fun PackSyncHeader(
    rideCode: String,
    riderCount: Int,
    onCopyCode: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Small PackSync logo mark (48dp x 48dp)
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(1.dp, PackSyncTheme.colors.border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_packsync_mark),
                    contentDescription = "PackSync",
                    tint = PackSyncTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Code pill: flexible space, never wraps, small "CODE" label + monospaced code + copy icon
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(onClick = onCopyCode),
            shape = RoundedCornerShape(14.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(1.dp, PackSyncTheme.colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "CODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PackSyncTheme.colors.textTertiary,
                        maxLines = 1
                    )
                    Text(
                        text = rideCode.ifEmpty { "BIKE44" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = PackSyncTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = PackSyncTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 3. Riders pill: icon + count (48dp height, minWidth 48dp)
        Surface(
            modifier = Modifier
                .height(48.dp)
                .defaultMinSize(minWidth = 48.dp),
            shape = RoundedCornerShape(14.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(1.dp, PackSyncTheme.colors.border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Riders",
                    tint = PackSyncTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "$riderCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PackSyncTheme.colors.textPrimary
                )
            }
        }

        // 4. Menu button (48dp x 48dp)
        Box {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { isMenuOpen = true },
                shape = RoundedCornerShape(14.dp),
                color = PackSyncTheme.colors.surface,
                border = BorderStroke(1.dp, PackSyncTheme.colors.border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = PackSyncTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Leave Convoy",
                            color = PackSyncTheme.colors.destructiveRed,
                            fontWeight = FontWeight.Bold
                        )
                    },
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

/**
 * Emergency Alert Banner shown directly under header when a companion rider triggers SOS.
 */
@Composable
private fun EmergencyAlertBanner(
    emergencyRider: com.ridesafe.app.ui.screens.map.RiderWithDistance,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PackSyncTheme.colors.destructiveRed.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, PackSyncTheme.colors.destructiveRed),
        modifier = modifier.fillMaxWidth()
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
                        text = "EMERGENCY: ${emergencyRider.rider.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = PackSyncTheme.colors.destructiveRed
                    )
                    Text(
                        text = "${emergencyRider.formattedDistance} away",
                        fontSize = 10.sp,
                        color = PackSyncTheme.colors.textPrimary
                    )
                }
            }

            Button(
                onClick = onLocate,
                colors = ButtonDefaults.buttonColors(containerColor = PackSyncTheme.colors.destructiveRed),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("LOCATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/**
 * Redesigned Trip Container placed directly above the bottom control dock:
 * - Top line: small "DESTINATION" label, destination name on one line with ellipsis
 * - Below: two equal stat columns ("DISTANCE 321 km", "TIME 4 h 15 min") with thin vertical divider
 * - Right: "Fit Route" button (48dp height, icon + label)
 */
@Composable
private fun TripContainerCard(
    tripInfo: TripInfo?,
    onFitRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tripInfo == null || (!tripInfo.isTripPlanned && tripInfo.destName.isBlank())) return

    val destinationName = tripInfo.destName.trim().ifEmpty { "Malda" }
    val distanceText = if (tripInfo.formattedDistance.isNotBlank()) tripInfo.formattedDistance else "321 km"

    // Format duration into standard "4 h 15 min" notation
    val rawDuration = if (tripInfo.formattedDuration.isNotBlank()) tripInfo.formattedDuration else "4 h 15 min"
    val durationText = rawDuration
        .replace("hr", "h")
        .replace("hrs", "h")
        .replace("hours", "h")
        .replace("hour", "h")
        .replace("mins", "min")
        .replace("minutes", "min")
        .replace("minute", "min")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PackSyncTheme.colors.surface,
        border = BorderStroke(1.dp, PackSyncTheme.colors.border),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top line: small "DESTINATION" label, then destination name on one line with ellipsis
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DESTINATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = PackSyncTheme.colors.textTertiary,
                    maxLines = 1
                )
                Text(
                    text = destinationName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PackSyncTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Below: two equal stat columns + right side "Fit Route" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1: DISTANCE
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DISTANCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = PackSyncTheme.colors.textTertiary,
                        maxLines = 1
                    )
                    Text(
                        text = distanceText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Default,
                        color = PackSyncTheme.colors.textPrimary,
                        maxLines = 1
                    )
                }

                // Thin vertical divider between stat columns
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(PackSyncTheme.colors.border)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Stat 2: TIME (formatted as "4 h 15 min")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TIME",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = PackSyncTheme.colors.textTertiary,
                        maxLines = 1
                    )
                    Text(
                        text = durationText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Default,
                        color = PackSyncTheme.colors.textPrimary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Right side: "Fit Route" button (48dp height)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PackSyncTheme.colors.surfaceRaised,
                    border = BorderStroke(1.dp, PackSyncTheme.colors.border),
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(onClick = onFitRoute)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit Route",
                            tint = PackSyncTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Fit Route",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PackSyncTheme.colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ergonomic Bottom Control Dock:
 * - "MY STATUS" Hero button on the left (54dp height, dynamic icon, status color, text truncation)
 * - Three 54dp glove-friendly circular action buttons: Riders List, Route Toggle, Recenter on Me
 * - All controls have equal 54dp height and are baseline/center-aligned.
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
    val buttonHeight = 54.dp
    val statusAccent = myStatus.color

    val statusIcon = when (myStatus) {
        RiderStatus.RIDING -> Icons.Default.TwoWheeler
        RiderStatus.REFUELING -> Icons.Default.LocalGasStation
        RiderStatus.PUNCTURE -> Icons.Default.Build
        RiderStatus.REST -> Icons.Default.LocalCafe
        RiderStatus.EMERGENCY -> Icons.Default.Security
        RiderStatus.OTHER -> Icons.Default.WarningAmber
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "MY STATUS" Hero Pill Button (54dp height)
        Surface(
            shape = RoundedCornerShape(27.dp),
            color = PackSyncTheme.colors.surface,
            border = BorderStroke(2.dp, statusAccent),
            shadowElevation = 4.dp,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(statusAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = myStatus.displayName,
                        tint = statusAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MY STATUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = PackSyncTheme.colors.textTertiary,
                        maxLines = 1
                    )
                    Text(
                        text = myStatus.displayName.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = statusAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Riders List Button (54dp)
        IconButton(
            onClick = onRiderListClick,
            modifier = Modifier
                .size(buttonHeight)
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

        // 2. Route Directions Toggle Button (54dp)
        val isRouteActive = hasPlannedRoute && isRouteVisible
        IconButton(
            onClick = onToggleRouteClick,
            modifier = Modifier
                .size(buttonHeight)
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

        // 3. Recenter on Me Button (54dp)
        IconButton(
            onClick = onRecenterClick,
            modifier = Modifier
                .size(buttonHeight)
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
