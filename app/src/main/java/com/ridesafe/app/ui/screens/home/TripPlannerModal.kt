package com.ridesafe.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.data.model.RouteResult
import com.ridesafe.app.ui.theme.PackSyncTheme
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.PolylineUtils

private val ScreenHPadding = 20.dp
private val InputRadius = 12.dp
private val InputHeight = 52.dp
private val PrimaryButtonHeight = 56.dp
private val BackButtonSize = 44.dp

/**
 * Redesigned Plan Convoy Route screen:
 * - Strictly monochrome system: black, white, and neutral greys only.
 * - Simple vertical timeline: filled dot for start, hollow ring for destination, hairline connector.
 * - Flat 12px surface fields with no heavy card frames.
 * - Clean "Where to?" empty state without decorative icons or orange accents.
 * - Clean "18 km · 32 min" headline result and minimal monochrome map preview.
 * - Inverted primary button and centered quiet text button.
 */
@Composable
fun TripPlannerModal(
    startQuery: String,
    destQuery: String,
    selectedStartPlace: PlaceSuggestion?,
    selectedDestPlace: PlaceSuggestion?,
    startSuggestions: List<PlaceSuggestion>,
    destSuggestions: List<PlaceSuggestion>,
    isLoadingStartSuggestions: Boolean,
    isLoadingDestSuggestions: Boolean,
    isDetectingStartLocation: Boolean,
    isCalculatingRoute: Boolean,
    calculatedRoute: RouteResult?,
    routeError: String?,
    isCreatingRide: Boolean,
    onStartQueryChange: (String) -> Unit,
    onDestQueryChange: (String) -> Unit,
    onSelectStartPlace: (PlaceSuggestion) -> Unit,
    onSelectDestPlace: (PlaceSuggestion) -> Unit,
    onUseCurrentLocationForStart: () -> Unit,
    onClearStartPlace: () -> Unit,
    onClearDestPlace: () -> Unit,
    onRetryRouteCalculation: () -> Unit,
    onCreateRideWithRoute: () -> Unit,
    onSkipAndCreateRide: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PackSyncTheme.colors
    val focusManager = LocalFocusManager.current
    var isEditingStart by remember { mutableStateOf(false) }
    var isEditingDest by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Scrollable / Content area ───────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ScreenHPadding)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Header: 44px back button + "Plan route" title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.size(BackButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Plan route",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Set a start and destination",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Route Inputs with Vertical Timeline
                RouteTimelineInputs(
                    startQuery = startQuery,
                    destQuery = destQuery,
                    selectedStartPlace = selectedStartPlace,
                    isDetectingStartLocation = isDetectingStartLocation,
                    onStartQueryChange = {
                        isEditingStart = true
                        isEditingDest = false
                        onStartQueryChange(it)
                    },
                    onDestQueryChange = {
                        isEditingDest = true
                        isEditingStart = false
                        onDestQueryChange(it)
                    },
                    onUseCurrentLocationForStart = {
                        focusManager.clearFocus()
                        onUseCurrentLocationForStart()
                    },
                    onClearStartPlace = onClearStartPlace,
                    onClearDestPlace = onClearDestPlace
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Middle Area: Search Results or Route Status / Empty Guidance
                Box(modifier = Modifier.weight(1f)) {
                    val activeSuggestions = if (isEditingStart) startSuggestions else destSuggestions
                    val isLoadingActive = if (isEditingStart) isLoadingStartSuggestions else isLoadingDestSuggestions

                    if (activeSuggestions.isNotEmpty() || isLoadingActive) {
                        // Suggestions list with hairline dividers
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEditingStart) "Starting points" else "Destinations",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textPrimary
                                )
                                if (isLoadingActive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colors.textPrimary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.border, thickness = 1.dp)

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(activeSuggestions) { suggestion ->
                                    SuggestionListItem(
                                        suggestion = suggestion,
                                        onClick = {
                                            focusManager.clearFocus()
                                            if (isEditingStart) {
                                                isEditingStart = false
                                                onSelectStartPlace(suggestion)
                                            } else {
                                                isEditingDest = false
                                                onSelectDestPlace(suggestion)
                                            }
                                        }
                                    )
                                    HorizontalDivider(color = colors.border, thickness = 1.dp)
                                }
                            }
                        }
                    } else {
                        // Overview: Empty state, Calculating, Error, or Route Found
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isCalculatingRoute) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surface)
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = colors.textPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Calculating route…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textSecondary
                                    )
                                }
                            } else if (calculatedRoute != null) {
                                RouteResultCard(
                                    route = calculatedRoute,
                                    startName = selectedStartPlace?.name ?: "Start",
                                    destName = selectedDestPlace?.name ?: "Destination"
                                )
                            } else if (routeError != null) {
                                RouteErrorCard(
                                    error = routeError,
                                    onRetry = onRetryRouteCalculation
                                )
                            } else {
                                // Minimal 2-line empty state
                                EmptyRoutePrompt()
                            }
                        }
                    }
                }
            }

            // ── Fixed Bottom Actions (surface background) ───────────────────
            HorizontalDivider(color = colors.border, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = ScreenHPadding, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isRouteReady = calculatedRoute != null && selectedStartPlace != null && selectedDestPlace != null
                val isButtonEnabled = isRouteReady && !isCreatingRide && !isCalculatingRoute

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1f,
                    animationSpec = tween(150),
                    label = "btn_scale"
                )

                // Primary CTA: Create convoy with route
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onCreateRideWithRoute()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PrimaryButtonHeight)
                        .scale(scale),
                    shape = RoundedCornerShape(InputRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonText,
                        disabledContainerColor = colors.surfaceRaised,
                        disabledContentColor = colors.textTertiary
                    ),
                    enabled = isButtonEnabled,
                    interactionSource = interactionSource,
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    if (isCreatingRide) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.primaryButtonText,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Create convoy with route",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isButtonEnabled) colors.primaryButtonText else colors.textTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary quiet text button: Skip route & start directly
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        onSkipAndCreateRide()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreatingRide,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = "Skip route & start directly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Route inputs with an architectural vertical timeline:
 * - Filled dot for start
 * - 1px hairline border connector
 * - Hollow ring for destination
 */
@Composable
private fun RouteTimelineInputs(
    startQuery: String,
    destQuery: String,
    selectedStartPlace: PlaceSuggestion?,
    isDetectingStartLocation: Boolean,
    onStartQueryChange: (String) -> Unit,
    onDestQueryChange: (String) -> Unit,
    onUseCurrentLocationForStart: () -> Unit,
    onClearStartPlace: () -> Unit,
    onClearDestPlace: () -> Unit
) {
    val colors = PackSyncTheme.colors
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator column
        Column(
            modifier = Modifier
                .width(20.dp)
                .padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Start dot: filled dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary)
            )

            // Timeline line: 1px border-colored line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(68.dp)
                    .background(colors.border)
            )

            // Destination ring: hollow ring
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(1.5.dp, colors.textPrimary, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Inputs column
        Column(modifier = Modifier.weight(1f)) {
            // Starting point label
            Text(
                text = "Starting point",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Start text field (no border, surface background, 12px radius)
            OutlinedTextField(
                value = startQuery,
                onValueChange = onStartQueryChange,
                placeholder = {
                    Text(
                        text = if (isDetectingStartLocation) "Detecting location…" else "Search starting point",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = colors.textPrimary
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDetectingStartLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.textSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            IconButton(
                                onClick = onUseCurrentLocationForStart,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Current location",
                                    tint = if (selectedStartPlace?.name == "Current Location") colors.textPrimary else colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (startQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearStartPlace,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    cursorColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(InputRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(InputHeight)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Destination label
            Text(
                text = "Destination",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Destination text field (no border, surface background, 12px radius)
            OutlinedTextField(
                value = destQuery,
                onValueChange = onDestQueryChange,
                placeholder = {
                    Text(
                        text = "Search destination",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = colors.textPrimary
                ),
                trailingIcon = {
                    if (destQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearDestPlace,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    cursorColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(InputRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(InputHeight)
            )
        }
    }
}

/**
 * Clean empty state under two lines:
 * Headline "Where to?" and one body line. No decorative icons.
 */
@Composable
private fun EmptyRoutePrompt() {
    val colors = PackSyncTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Where to?",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Search a destination to see the route and ride time.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary
        )
    }
}

/**
 * Route result: Headline style "18 km · 32 min" on one row,
 * with a clean monochrome map polyline preview.
 */
@Composable
private fun RouteResultCard(
    route: RouteResult,
    startName: String,
    destName: String
) {
    val colors = PackSyncTheme.colors
    val dist = PolylineUtils.formatDistance(route.distanceMeters)
    val dur = PolylineUtils.formatDuration(route.durationSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(18.dp)
    ) {
        // Distance and ride time on one row, Headline style
        Text(
            text = "$dist · $dur",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )

        if (route.summary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "via ${route.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Architectural monochrome route preview
        MonochromeRouteCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceRaised)
        )
    }
}

/**
 * Minimalist monochrome canvas route preview.
 * Grey road paths, route in textPrimary.
 */
@Composable
private fun MonochromeRouteCanvas(modifier: Modifier = Modifier) {
    val colors = PackSyncTheme.colors
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle background grid roads
        val gridColor = colors.border
        drawLine(gridColor, Offset(0f, h * 0.35f), Offset(w, h * 0.35f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 0.7f), Offset(w, h * 0.7f), strokeWidth = 1f)
        drawLine(gridColor, Offset(w * 0.3f, 0f), Offset(w * 0.3f, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(w * 0.7f, 0f), Offset(w * 0.7f, h), strokeWidth = 1f)

        // Route polyline
        val routePath = Path().apply {
            moveTo(w * 0.15f, h * 0.75f)
            cubicTo(w * 0.35f, h * 0.72f, w * 0.45f, h * 0.45f, w * 0.55f, h * 0.48f)
            cubicTo(w * 0.65f, h * 0.52f, w * 0.75f, h * 0.28f, w * 0.85f, h * 0.25f)
        }
        drawPath(
            path = routePath,
            color = colors.textPrimary,
            style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Start point: filled dot
        drawCircle(
            color = colors.textPrimary,
            radius = 4.dp.toPx(),
            center = Offset(w * 0.15f, h * 0.75f)
        )

        // Destination point: hollow ring
        drawCircle(
            color = colors.textPrimary,
            radius = 4.dp.toPx(),
            center = Offset(w * 0.85f, h * 0.25f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * Search suggestion item with location icon, name, and locality.
 */
@Composable
private fun SuggestionListItem(
    suggestion: PlaceSuggestion,
    onClick: () -> Unit
) {
    val colors = PackSyncTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.locality.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = suggestion.locality,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Route calculation error row.
 */
@Composable
private fun RouteErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    val colors = PackSyncTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Route calculation failed",
                style = MaterialTheme.typography.titleLarge,
                color = colors.destructiveRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        TextButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Retry",
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, name = "Dark Route Planner")
@Composable
private fun TripPlannerModalDarkPreview() {
    RideSafeTheme(darkTheme = true) {
        TripPlannerModal(
            startQuery = "Connaught Place",
            destQuery = "India Gate",
            selectedStartPlace = PlaceSuggestion(
                name = "Connaught Place",
                locality = "New Delhi, Delhi",
                lat = 28.6304,
                lng = 77.2177
            ),
            selectedDestPlace = PlaceSuggestion(
                name = "India Gate",
                locality = "Rajpath, New Delhi",
                lat = 28.6129,
                lng = 77.2295
            ),
            startSuggestions = emptyList(),
            destSuggestions = emptyList(),
            isLoadingStartSuggestions = false,
            isLoadingDestSuggestions = false,
            isDetectingStartLocation = false,
            isCalculatingRoute = false,
            calculatedRoute = RouteResult(
                routePoints = emptyList(),
                encodedPolyline = "",
                distanceMeters = 18000.0,
                durationSeconds = 1920.0,
                summary = "Kasturba Gandhi Marg"
            ),
            routeError = null,
            isCreatingRide = false,
            onStartQueryChange = {},
            onDestQueryChange = {},
            onSelectStartPlace = {},
            onSelectDestPlace = {},
            onUseCurrentLocationForStart = {},
            onClearStartPlace = {},
            onClearDestPlace = {},
            onRetryRouteCalculation = {},
            onCreateRideWithRoute = {},
            onSkipAndCreateRide = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA, name = "Light Route Planner")
@Composable
private fun TripPlannerModalLightPreview() {
    RideSafeTheme(darkTheme = false) {
        TripPlannerModal(
            startQuery = "",
            destQuery = "",
            selectedStartPlace = null,
            selectedDestPlace = null,
            startSuggestions = emptyList(),
            destSuggestions = emptyList(),
            isLoadingStartSuggestions = false,
            isLoadingDestSuggestions = false,
            isDetectingStartLocation = false,
            isCalculatingRoute = false,
            calculatedRoute = null,
            routeError = null,
            isCreatingRide = false,
            onStartQueryChange = {},
            onDestQueryChange = {},
            onSelectStartPlace = {},
            onSelectDestPlace = {},
            onUseCurrentLocationForStart = {},
            onClearStartPlace = {},
            onClearDestPlace = {},
            onRetryRouteCalculation = {},
            onCreateRideWithRoute = {},
            onSkipAndCreateRide = {},
            onDismiss = {}
        )
    }
}
