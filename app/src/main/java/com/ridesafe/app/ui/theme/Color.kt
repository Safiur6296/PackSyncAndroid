package com.ridesafe.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── PackSync semantic color tokens ──────────────────────────────────────
// Monochrome palette derived from the new geometric dot-arrow logo.
// Functional color (green, red) used ONLY for live status and destructive actions.

// Dark theme
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val DarkSurfaceRaised = Color(0xFF1C1C1C)
val DarkBorder = Color(0xFF262626)
val DarkTextPrimary = Color(0xFFFAFAFA)
val DarkTextSecondary = Color(0xFFA1A1A1)
val DarkTextTertiary = Color(0xFF6B6B6B)
val DarkPrimaryButtonBg = Color(0xFFFAFAFA)
val DarkPrimaryButtonText = Color(0xFF0A0A0A)
val DarkLogoFill = Color(0xFFFAFAFA)

// Light theme
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFF2F2F2)
val LightBorder = Color(0xFFE5E5E5)
val LightTextPrimary = Color(0xFF0A0A0A)
val LightTextSecondary = Color(0xFF5C5C5C)
val LightTextTertiary = Color(0xFF8A8A8A)
val LightPrimaryButtonBg = Color(0xFF0A0A0A)
val LightPrimaryButtonText = Color(0xFFFAFAFA)
val LightLogoFill = Color(0xFF0A0A0A)

// Functional — only for live-status dot and destructive actions
val DarkLiveGreen = Color(0xFF30D158)
val LightLiveGreen = Color(0xFF1F9D4D)
val DarkDestructiveRed = Color(0xFFFF453A)
val LightDestructiveRed = Color(0xFFD92D20)

// ── Backward-compat aliases for map screens (not yet migrated) ──────
// ponytail: remove these once LiveMapScreen / RiderListBottomSheet / StopStatusDialog
// are migrated to PackSyncTheme.colors.
val BikerAmber = Color(0xFFFAFAFA) // was orange, mapped to primary for now
val BikerDarkBg = DarkBackground
val BikerCardBg = DarkSurface
val BikerSurfaceElevated = DarkSurfaceRaised
val BikerBorder = DarkBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextMuted = DarkTextTertiary
val StatusGreen = DarkLiveGreen
val StatusRed = DarkDestructiveRed
val StatusAmber = Color(0xFFFFB300) // kept for permission banner in map
val StatusOrange = Color(0xFFFF7043)
val StatusBlue = Color(0xFF40C4FF)
