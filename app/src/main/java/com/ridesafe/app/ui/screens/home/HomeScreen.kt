package com.ridesafe.app.ui.screens.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridesafe.app.BuildConfig
import com.ridesafe.app.data.model.LocalRideSession
import com.ridesafe.app.data.model.LocalRideSessionUi
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.ui.theme.PackSyncTheme
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.PermissionHelper

// ── Constants ───────────────────────────────────────────────────────────
private val ScreenHPadding = 20.dp
private val SectionGap = 28.dp
private val GroupGap = 12.dp
private val ButtonRadius = 12.dp
private val SurfaceRadius = 16.dp
private val CodeCellRadius = 10.dp
private val PrimaryButtonHeight = 56.dp
private val CompactButtonHeight = 48.dp
private val MinTapTarget = 48.dp

/**
 * HomeScreen handles rider onboarding: entering a rider name, creating a new ride,
 * entering a ride code to join an existing group, and viewing recent ride sessions.
 */
@Composable
fun HomeScreen(
    onRequestPermissions: () -> Unit,
    onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(PermissionHelper.hasRequiredRidePermissions(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions = PermissionHelper.hasRequiredRidePermissions(context)
                viewModel.refreshSessions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        hasPermissions = hasPermissions,
        onRequestPermissions = onRequestPermissions,
        onCheckForUpdates = onCheckForUpdates,
        onRiderNameChange = viewModel::onRiderNameChange,
        onJoinCodeChange = viewModel::onJoinCodeChange,
        onPlanRoute = {
            if (!hasPermissions) {
                onRequestPermissions()
                return@HomeScreenContent
            }
            if (uiState.riderName.trim().isBlank()) {
                Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                return@HomeScreenContent
            }
            viewModel.openTripPlanner()
        },
        onQuickStartRide = {
            if (!hasPermissions) {
                onRequestPermissions()
                return@HomeScreenContent
            }
            viewModel.createRide(context, onRideJoined)
        },
        onJoinRide = {
            if (!hasPermissions) {
                onRequestPermissions()
                return@HomeScreenContent
            }
            viewModel.joinRide(context, onRideJoined)
        },
        onRejoinRide = { session ->
            if (!hasPermissions) {
                onRequestPermissions()
                return@HomeScreenContent
            }
            viewModel.rejoinRide(context, session, onRideJoined)
        },
        onDeleteSession = viewModel::deleteSession,
        onRefreshSessions = viewModel::refreshSessions,
        onCloseTripPlanner = viewModel::closeTripPlanner,
        onStartQueryChange = viewModel::onStartQueryChange,
        onDestQueryChange = viewModel::onDestQueryChange,
        onSelectStartPlace = viewModel::selectStartPlace,
        onSelectDestPlace = viewModel::selectDestPlace,
        onUseCurrentLocationForStart = viewModel::detectAndSetCurrentLocationAsStart,
        onClearStartPlace = viewModel::clearStartPlace,
        onClearDestPlace = viewModel::clearDestPlace,
        onRetryRouteCalculation = viewModel::calculateRoute,
        onCreateRideWithRoute = {
            viewModel.createRideWithPlannedTrip(context, onRideJoined)
        }
    )
}

/**
 * Stateless content composable for HomeScreen, separating UI from ViewModel.
 */
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onRiderNameChange: (String) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onPlanRoute: () -> Unit = {},
    onQuickStartRide: () -> Unit = {},
    onJoinRide: () -> Unit = {},
    onRejoinRide: (LocalRideSession) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onRefreshSessions: () -> Unit = {},
    onCloseTripPlanner: () -> Unit = {},
    onStartQueryChange: (String) -> Unit = {},
    onDestQueryChange: (String) -> Unit = {},
    onSelectStartPlace: (PlaceSuggestion) -> Unit = {},
    onSelectDestPlace: (PlaceSuggestion) -> Unit = {},
    onUseCurrentLocationForStart: () -> Unit = {},
    onClearStartPlace: () -> Unit = {},
    onClearDestPlace: () -> Unit = {},
    onRetryRouteCalculation: () -> Unit = {},
    onCreateRideWithRoute: () -> Unit = {}
) {
    val colors = PackSyncTheme.colors
    val focusManager = LocalFocusManager.current
    var sessionToDelete by remember { mutableStateOf<LocalRideSessionUi?>(null) }

    // Delete confirmation dialog
    if (sessionToDelete != null) {
        val session = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Remove ride?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${session.rideCode}\" from your local history? Active riders in the group won't be affected.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(session.rideCode)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.destructiveRed
                    ),
                    shape = RoundedCornerShape(ButtonRadius)
                ) {
                    Text("Remove", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(SurfaceRadius)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHPadding)
        ) {
            Spacer(modifier = Modifier.height(56.dp)) // safe area

            // ── 1. Header: logo mark + wordmark ─────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackSyncLogoMark(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "PackSync",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(SectionGap))

            // ── 2. Greeting / callsign ──────────────────────────────────────
            CallsignGreeting(
                riderName = uiState.riderName,
                onRiderNameChange = onRiderNameChange
            )

            Spacer(modifier = Modifier.height(SectionGap))

            // ── Permission banner ───────────────────────────────────────────
            AnimatedVisibility(
                visible = !hasPermissions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SurfaceRadius))
                            .background(colors.surfaceRaised)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Location permission needed for live tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg
                            ),
                            shape = RoundedCornerShape(ButtonRadius),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Grant",
                                color = colors.primaryButtonText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(SectionGap))
                }
            }

            // ── 3. Start a ride ─────────────────────────────────────────────
            Text(
                text = "Start a ride",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(GroupGap))

            // Primary CTA: Plan route & create ride
            PrimaryButton(
                text = "Plan route & create ride",
                onClick = {
                    focusManager.clearFocus()
                    onPlanRoute()
                },
                isLoading = uiState.isCreatingRide,
                enabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
            )

            Spacer(modifier = Modifier.height(GroupGap))

            // Secondary CTA: Quick start without a route
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onQuickStartRide()
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "Quick start without a route",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Share a 6-letter code with your pack for live GPS tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )

            Spacer(modifier = Modifier.height(SectionGap))

            // ── 4. Join a convoy ────────────────────────────────────────────
            Text(
                text = "Join a convoy",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(GroupGap))

            SegmentedCodeInput(
                code = uiState.joinCode,
                onCodeChange = onJoinCodeChange
            )

            Spacer(modifier = Modifier.height(GroupGap))

            // Join button — active only when 6 chars entered
            val joinEnabled = uiState.joinCode.length >= 6 &&
                    !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
            JoinButton(
                onClick = {
                    focusManager.clearFocus()
                    onJoinRide()
                },
                enabled = joinEnabled,
                isLoading = uiState.isJoiningRide
            )

            // Error message
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(GroupGap))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = colors.destructiveRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(SectionGap))

            // ── 5. Recent convoys ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recent convoys",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                    if (uiState.sessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.sessions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshSessions,
                    modifier = Modifier.size(MinTapTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoadingSessions) {
                // Skeleton loading rows
                repeat(2) {
                    SkeletonSessionRow()
                    if (it < 1) {
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            } else if (uiState.sessions.isEmpty()) {
                // Empty state with dot motif
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DotMotif(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No rides yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textTertiary
                    )
                }
            } else {
                uiState.sessions.forEachIndexed { index, sessionUi ->
                    SessionRow(
                        sessionUi = sessionUi,
                        isRejoining = uiState.rejoiningCode == sessionUi.rideCode,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = onRequestPermissions,
                        onRejoin = {
                            if (!hasPermissions) onRequestPermissions()
                            else onRejoinRide(sessionUi.session)
                        },
                        onDelete = { sessionToDelete = sessionUi }
                    )
                    if (index < uiState.sessions.lastIndex) {
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── 6. Footer ───────────────────────────────────────────────────
            Text(
                text = "v${BuildConfig.VERSION_NAME} · Check for updates",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCheckForUpdates() }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp)) // bottom safe area
        }

        // Full-page Plan Trip Modal
        AnimatedVisibility(
            visible = uiState.isTripPlannerOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            TripPlannerModal(
                startQuery = uiState.startLocationQuery,
                destQuery = uiState.destLocationQuery,
                selectedStartPlace = uiState.selectedStartPlace,
                selectedDestPlace = uiState.selectedDestPlace,
                startSuggestions = uiState.startSuggestions,
                destSuggestions = uiState.destSuggestions,
                isLoadingStartSuggestions = uiState.isLoadingStartSuggestions,
                isLoadingDestSuggestions = uiState.isLoadingDestSuggestions,
                isDetectingStartLocation = uiState.isDetectingStartLocation,
                isCalculatingRoute = uiState.isCalculatingRoute,
                calculatedRoute = uiState.calculatedRoute,
                routeError = uiState.routeError,
                isCreatingRide = uiState.isCreatingRide,
                onStartQueryChange = onStartQueryChange,
                onDestQueryChange = onDestQueryChange,
                onSelectStartPlace = onSelectStartPlace,
                onSelectDestPlace = onSelectDestPlace,
                onUseCurrentLocationForStart = onUseCurrentLocationForStart,
                onClearStartPlace = onClearStartPlace,
                onClearDestPlace = onClearDestPlace,
                onRetryRouteCalculation = onRetryRouteCalculation,
                onCreateRideWithRoute = onCreateRideWithRoute,
                onSkipAndCreateRide = onQuickStartRide,
                onDismiss = onCloseTripPlanner
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Components
// ═══════════════════════════════════════════════════════════════════════

/**
 * Geometric logo mark — the dot-arrow motif from the PackSync brand.
 * Drawn with Canvas for zero-dependency, scalable rendering.
 */
@Composable
private fun PackSyncLogoMark(modifier: Modifier = Modifier) {
    val color = PackSyncTheme.colors.textPrimary
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dotR = w * 0.065f

        // 3x3 triangular dot cluster (bottom-left)
        val dots = listOf(
            // row 0 (bottom) — 3 dots
            0f to 2f, 1f to 2f, 2f to 2f,
            // row 1 (middle) — 2 dots
            0f to 1f, 1f to 1f,
            // row 2 (top) — 1 dot
            0f to 0f
        )
        val gridStep = w * 0.18f
        val offsetX = w * 0.08f
        val offsetY = h * 0.25f
        dots.forEach { (col, row) ->
            drawCircle(
                color = color,
                radius = dotR,
                center = androidx.compose.ui.geometry.Offset(
                    offsetX + col * gridStep + gridStep / 2,
                    offsetY + row * gridStep + gridStep / 2
                )
            )
        }

        // Arrow — pointing top-right
        val arrowPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.88f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.50f)
            moveTo(w * 0.88f, h * 0.22f)
            lineTo(w * 0.60f, h * 0.22f)
        }
        drawPath(
            path = arrowPath,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.09f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

/**
 * Small 3-dot triangular motif for the empty state.
 */
@Composable
private fun DotMotif(modifier: Modifier = Modifier) {
    val color = PackSyncTheme.colors.textTertiary
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val dotR = w * 0.08f
        val cx = w / 2f
        // Triangle: 1 dot top, 2 dots bottom
        drawCircle(color = color, radius = dotR, center = androidx.compose.ui.geometry.Offset(cx, w * 0.25f))
        drawCircle(color = color, radius = dotR, center = androidx.compose.ui.geometry.Offset(cx - w * 0.15f, w * 0.65f))
        drawCircle(color = color, radius = dotR, center = androidx.compose.ui.geometry.Offset(cx + w * 0.15f, w * 0.65f))
    }
}

/**
 * Greeting title that doubles as inline callsign editor.
 * Tap the name to enter edit mode (bottom sheet not needed for this simple case).
 */
@Composable
private fun CallsignGreeting(
    riderName: String,
    onRiderNameChange: (String) -> Unit
) {
    val colors = PackSyncTheme.colors
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (isEditing || riderName.isBlank()) {
        // Inline edit mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Hi, ",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
            BasicTextField(
                value = riderName,
                onValueChange = onRiderNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                ),
                cursorBrush = SolidColor(colors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (riderName.isNotBlank()) isEditing = false
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (!it.isFocused && riderName.isNotBlank()) isEditing = false },
                decorationBox = { inner ->
                    Box {
                        if (riderName.isEmpty()) {
                            Text(
                                text = "your name",
                                style = TextStyle(
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textTertiary
                                )
                            )
                        }
                        inner()
                    }
                    // Underline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                            .height(2.dp)
                            .background(colors.border)
                    )
                }
            )
        }
        // Auto-focus when entering edit mode
        if (isEditing) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    } else {
        // Display mode — tap to edit
        Text(
            text = "Hi, $riderName",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { isEditing = true }
        )
    }
}

/**
 * Full-width primary button with pressed-state scale animation.
 */
@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val colors = PackSyncTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "btn_scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(PrimaryButtonHeight)
            .scale(scale),
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryButtonBg,
            disabledContainerColor = colors.primaryButtonBg.copy(alpha = 0.38f)
        ),
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.primaryButtonText,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryButtonText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.primaryButtonText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Segmented 6-box convoy code input with paste button.
 */
@Composable
private fun SegmentedCodeInput(
    code: String,
    onCodeChange: (String) -> Unit
) {
    val colors = PackSyncTheme.colors
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Invisible text field that captures input
        Box(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (i in 0 until 6) {
                    val char = code.getOrNull(i)?.toString() ?: ""
                    val isCurrent = i == code.length && isFocused

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PrimaryButtonHeight)
                            .clip(RoundedCornerShape(CodeCellRadius))
                            .background(colors.surfaceRaised)
                            .border(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) colors.textPrimary else colors.border,
                                shape = RoundedCornerShape(CodeCellRadius)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }

            // Invisible input overlay
            BasicTextField(
                value = code,
                onValueChange = { value ->
                    val filtered = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
                    onCodeChange(filtered)
                    if (filtered.length == 6) focusManager.clearFocus()
                },
                singleLine = true,
                textStyle = TextStyle(color = colors.background), // invisible
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = SolidColor(colors.background), // invisible cursor
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }

        // Paste button
        IconButton(
            onClick = {
                val clipText = clipboardManager.getText()?.text ?: ""
                val cleaned = clipText.trim().uppercase().filter { it.isLetterOrDigit() }.take(6)
                if (cleaned.isNotEmpty()) {
                    onCodeChange(cleaned)
                    focusManager.clearFocus()
                    Toast.makeText(context, "Pasted \"$cleaned\"", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .size(PrimaryButtonHeight)
                .clip(RoundedCornerShape(CodeCellRadius))
                .background(colors.surfaceRaised)
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Paste code",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Compact join button for the code entry section.
 */
@Composable
private fun JoinButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean = false
) {
    val colors = PackSyncTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "join_scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactButtonHeight)
            .scale(scale),
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryButtonBg,
            disabledContainerColor = colors.primaryButtonBg.copy(alpha = 0.38f)
        ),
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.primaryButtonText,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Join Convoy",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryButtonText
            )
        }
    }
}

/**
 * Single session row in recent convoys list.
 */
@Composable
private fun SessionRow(
    sessionUi: LocalRideSessionUi,
    isRejoining: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRejoin: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = PackSyncTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sessionUi.rideCode,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                )
                if (sessionUi.isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LiveIndicator()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(formatSessionTime(sessionUi.timestamp, sessionUi.isHost))
                    if (sessionUi.riderName.isNotBlank()) {
                        append(" · as ${sessionUi.riderName}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Join button (compact)
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = tween(150),
            label = "rejoin_scale"
        )
        Button(
            onClick = onRejoin,
            modifier = Modifier
                .height(36.dp)
                .scale(scale),
            shape = RoundedCornerShape(ButtonRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (sessionUi.isActive) colors.primaryButtonBg else colors.surfaceRaised
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            enabled = !isRejoining,
            interactionSource = interactionSource
        ) {
            if (isRejoining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = if (sessionUi.isActive) colors.primaryButtonText else colors.textSecondary,
                    strokeWidth = 1.5.dp
                )
            } else {
                Text(
                    text = "Join",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sessionUi.isActive) colors.primaryButtonText else colors.textPrimary
                )
            }
        }

        // Overflow menu for delete
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = colors.surface
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Remove",
                            color = colors.destructiveRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = colors.destructiveRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Live status indicator — green dot + "Live" text.
 * Dot motif used with meaning: this convoy is active.
 */
@Composable
private fun LiveIndicator() {
    val colors = PackSyncTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.liveGreen)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Live",
            style = MaterialTheme.typography.bodySmall,
            color = colors.liveGreen,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Skeleton row for loading state — surfaceRaised pulsing bars.
 */
@Composable
private fun SkeletonSessionRow() {
    val colors = PackSyncTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceRaised)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceRaised)
            )
        }
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(ButtonRadius))
                .background(colors.surfaceRaised)
        )
    }
}

/**
 * Formats relative time (e.g., "Created 2h ago", "Joined yesterday").
 */
private fun formatSessionTime(timestamp: Long, isHost: Boolean): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val prefix = if (isHost) "Created" else "Joined"
    return when {
        diff < 60_000L -> "$prefix just now"
        diff < 3600_000L -> "$prefix ${diff / 60_000L}m ago"
        diff < 86400_000L -> "$prefix ${diff / 3600_000L}h ago"
        diff < 172800_000L -> "$prefix yesterday"
        else -> "$prefix ${DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.DAY_IN_MILLIS)}"
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, name = "Dark")
@Composable
private fun HomeScreenDarkPreview() {
    RideSafeTheme(darkTheme = true) {
        HomeScreenContent(
            uiState = HomeUiState(
                riderName = "Safiur",
                joinCode = "MOT",
                sessions = listOf(
                    LocalRideSessionUi(
                        session = LocalRideSession(
                            rideCode = "MOTO16",
                            riderId = "id1",
                            riderName = "Safiur",
                            timestamp = System.currentTimeMillis() - 15 * 60 * 1000L,
                            isHost = true
                        ),
                        isActive = true
                    ),
                    LocalRideSessionUi(
                        session = LocalRideSession(
                            rideCode = "ROAD21",
                            riderId = "id2",
                            riderName = "Safiur",
                            timestamp = System.currentTimeMillis() - 3 * 86400 * 1000L,
                            isHost = false
                        ),
                        isActive = false
                    )
                ),
                isLoadingSessions = false
            ),
            hasPermissions = true,
            onRequestPermissions = {},
            onRiderNameChange = {},
            onJoinCodeChange = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA, name = "Light")
@Composable
private fun HomeScreenLightPreview() {
    RideSafeTheme(darkTheme = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                riderName = "Safiur",
                joinCode = "MOT",
                sessions = listOf(
                    LocalRideSessionUi(
                        session = LocalRideSession(
                            rideCode = "MOTO16",
                            riderId = "id1",
                            riderName = "Safiur",
                            timestamp = System.currentTimeMillis() - 15 * 60 * 1000L,
                            isHost = true
                        ),
                        isActive = true
                    )
                ),
                isLoadingSessions = false
            ),
            hasPermissions = true,
            onRequestPermissions = {},
            onRiderNameChange = {},
            onJoinCodeChange = {}
        )
    }
}
