package com.ridesafe.app.ui.screens.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.ui.theme.PackSyncTheme
import kotlinx.coroutines.launch

/**
 * Redesigned Update Ride Status Bottom Sheet.
 * Built for motorcycle glove ergonomics:
 * - Pinned bottom sheet reachable by thumb
 * - Full-width "Resume Riding" hero button at top
 * - 2-column grid of equal-height tiles for stop categories with min-height and content sizing
 * - Consistent 2.2px custom line icons
 * - Emergency SOS isolated at the bottom with a 2-second Press-and-Hold circular progress lockout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopStatusDialog(
    currentStatus: RiderStatus,
    onStatusSelected: (RiderStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PackSyncTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PackSyncTheme.colors.border)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title + Subtitle + 44dp Close Button (properly aligned, never clipped)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Update Ride Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = PackSyncTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Broadcast your status to the convoy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PackSyncTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PackSyncTheme.colors.surfaceRaised)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PackSyncTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1. Primary Full-Width Action: Resume Riding
            val isRidingActive = currentStatus == RiderStatus.RIDING
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isRidingActive) PackSyncTheme.colors.liveGreen.copy(alpha = 0.16f)
                        else PackSyncTheme.colors.surfaceRaised
                    )
                    .border(
                        width = if (isRidingActive) 2.dp else 1.dp,
                        color = if (isRidingActive) PackSyncTheme.colors.liveGreen else PackSyncTheme.colors.border,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onStatusSelected(RiderStatus.RIDING) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRidingActive) PackSyncTheme.colors.liveGreen
                            else PackSyncTheme.colors.liveGreen.copy(alpha = 0.18f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Riding",
                        tint = if (isRidingActive) Color.Black else PackSyncTheme.colors.liveGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resume Riding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRidingActive) PackSyncTheme.colors.liveGreen else PackSyncTheme.colors.textPrimary
                    )
                    Text(
                        text = "Moving normally with the pack",
                        style = MaterialTheme.typography.bodySmall,
                        color = PackSyncTheme.colors.textSecondary
                    )
                }

                if (isRidingActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PackSyncTheme.colors.liveGreen
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // Section Label
            Text(
                text = "OR REPORT A STOP REASON:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = PackSyncTheme.colors.textTertiary
            )

            // 2. 2-Column Stop Category Grid (Equal-height per row, min-height with content sizing)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1: Refueling & Tire Puncture
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StopOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalGasStation,
                        title = "Refueling",
                        subtitle = "Stopped at gas station",
                        status = RiderStatus.REFUELING,
                        isSelected = currentStatus == RiderStatus.REFUELING,
                        onClick = { onStatusSelected(RiderStatus.REFUELING) }
                    )
                    StopOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Build,
                        title = "Tire Puncture",
                        subtitle = "Flat tire or repair",
                        status = RiderStatus.PUNCTURE,
                        isSelected = currentStatus == RiderStatus.PUNCTURE,
                        onClick = { onStatusSelected(RiderStatus.PUNCTURE) }
                    )
                }

                // Row 2: Rest Stop & Other Stop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StopOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalCafe,
                        title = "Rest Stop",
                        subtitle = "Breather or coffee",
                        status = RiderStatus.REST,
                        isSelected = currentStatus == RiderStatus.REST,
                        onClick = { onStatusSelected(RiderStatus.REST) }
                    )
                    StopOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.WarningAmber,
                        title = "Other Stop",
                        subtitle = "Temporary pause",
                        status = RiderStatus.OTHER,
                        isSelected = currentStatus == RiderStatus.OTHER,
                        onClick = { onStatusSelected(RiderStatus.OTHER) }
                    )
                }
            }

            // 3. Isolated Emergency SOS Card with 2-Second Hold Lockout
            EmergencyHoldCard(
                isActive = currentStatus == RiderStatus.EMERGENCY,
                onTriggered = { onStatusSelected(RiderStatus.EMERGENCY) }
            )
        }
    }
}

@Composable
private fun StopOptionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: RiderStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tileBg = if (isSelected) status.color.copy(alpha = 0.16f) else PackSyncTheme.colors.surfaceRaised
    val tileBorder = if (isSelected) status.color else PackSyncTheme.colors.border

    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tileBg)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = tileBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) status.color else PackSyncTheme.colors.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.Black else PackSyncTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(status.color)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) status.color else PackSyncTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = PackSyncTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Emergency SOS Card with 2-second press-and-hold lockout to prevent accidental activation.
 */
@Composable
private fun EmergencyHoldCard(
    isActive: Boolean,
    onTriggered: () -> Unit
) {
    val redColor = PackSyncTheme.colors.destructiveRed
    val holdProgress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val cardBg = if (isActive || isHolding) redColor.copy(alpha = 0.22f) else redColor.copy(alpha = 0.10f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                width = if (isActive || isHolding) 2.5.dp else 1.5.dp,
                color = redColor,
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        var triggered = false
                        val job = coroutineScope.launch {
                            holdProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
                            )
                            if (holdProgress.value >= 1f) {
                                triggered = true
                                onTriggered()
                            }
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            job.cancel()
                            if (!triggered) {
                                coroutineScope.launch { holdProgress.snapTo(0f) }
                            }
                            isHolding = false
                        }
                    }
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left circular progress indicator around SOS shield
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { if (isActive) 1f else holdProgress.value },
                modifier = Modifier.size(52.dp),
                color = redColor,
                trackColor = redColor.copy(alpha = 0.25f),
                strokeWidth = 4.dp
            )
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Emergency SOS",
                tint = redColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isHolding) "HOLDING TO BROADCAST..." else "Emergency SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = redColor
            )
            Text(
                text = if (isHolding) {
                    val remaining = ((1f - holdProgress.value) * 2f).coerceAtLeast(0f)
                    String.format("%.1fs remaining • Release to cancel", remaining)
                } else if (isActive) {
                    "ACTIVE • Convoy broadcast in progress"
                } else {
                    "Hold 2s • Immediate help needed"
                },
                style = MaterialTheme.typography.bodySmall,
                color = PackSyncTheme.colors.textSecondary
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isActive) redColor else redColor.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(1.dp, redColor)
        ) {
            Text(
                text = if (isActive) "ACTIVE" else if (isHolding) "HOLD" else "HOLD 2s",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.White else redColor
            )
        }
    }
}
