package com.ridesafe.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Semantic token holder ───────────────────────────────────────────────
@Immutable
data class PackSyncColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primaryButtonBg: Color,
    val primaryButtonText: Color,
    val logoFill: Color,
    val liveGreen: Color,
    val destructiveRed: Color
)

val LocalPackSyncColors = staticCompositionLocalOf {
    PackSyncColors(
        background = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceRaised = Color.Unspecified,
        border = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textTertiary = Color.Unspecified,
        primaryButtonBg = Color.Unspecified,
        primaryButtonText = Color.Unspecified,
        logoFill = Color.Unspecified,
        liveGreen = Color.Unspecified,
        destructiveRed = Color.Unspecified
    )
}

private val DarkPackSyncColors = PackSyncColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceRaised = DarkSurfaceRaised,
    border = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    primaryButtonBg = DarkPrimaryButtonBg,
    primaryButtonText = DarkPrimaryButtonText,
    logoFill = DarkLogoFill,
    liveGreen = DarkLiveGreen,
    destructiveRed = DarkDestructiveRed
)

private val LightPackSyncColors = PackSyncColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceRaised = LightSurfaceRaised,
    border = LightBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    primaryButtonBg = LightPrimaryButtonBg,
    primaryButtonText = LightPrimaryButtonText,
    logoFill = LightLogoFill,
    liveGreen = LightLiveGreen,
    destructiveRed = LightDestructiveRed
)

// M3 color schemes (used by Material components like AlertDialog)
private val DarkMaterialScheme = darkColorScheme(
    primary = DarkPrimaryButtonBg,
    onPrimary = DarkPrimaryButtonText,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DarkDestructiveRed
)

private val LightMaterialScheme = lightColorScheme(
    primary = LightPrimaryButtonBg,
    onPrimary = LightPrimaryButtonText,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LightDestructiveRed
)

// ── Public API ──────────────────────────────────────────────────────────

object PackSyncTheme {
    val colors: PackSyncColors
        @Composable get() = LocalPackSyncColors.current
}

@Composable
fun RideSafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val packSyncColors = if (darkTheme) DarkPackSyncColors else LightPackSyncColors
    val materialScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = packSyncColors.background.toArgb()
            window.navigationBarColor = packSyncColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPackSyncColors provides packSyncColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = PackSyncTypography,
            content = content
        )
    }
}
