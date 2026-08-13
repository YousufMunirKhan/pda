package com.example.swtichandsavepda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = SuccessBg,
    onSecondaryContainer = BrandAccentDark,
    tertiary = Slate500,
    onTertiary = Color.White,
    tertiaryContainer = Slate100,
    onTertiaryContainer = Slate900,
    error = StatusDanger,
    onError = Color.White,
    errorContainer = DangerBg,
    onErrorContainer = DangerText,
    background = CanvasLight,
    onBackground = Slate900,
    surface = SurfaceLight,
    onSurface = Slate900,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    outline = StrokeControl,
    outlineVariant = StrokeCard,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA7FF),
    onPrimary = Color(0xFF04244F),
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimaryLight,
    secondary = Color(0xFF4ADE9B),
    onSecondary = Color(0xFF00301C),
    secondaryContainer = BrandAccentDark,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = DarkTextMuted,
    onTertiary = DarkBg,
    tertiaryContainer = DarkSurfaceElevated,
    onTertiaryContainer = DarkTextPrimary,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    scrim = Color.Black,
)

/**
 * Brand tokens that have no Material 3 slot — gradients and the muted text ramp
 * the POS app uses for hero panels and secondary copy.
 */
@Immutable
data class BrandColors(
    val heroGradient: Brush,
    val panelGradient: Brush,
    val textHeading: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardStroke: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

private val LightBrandColors = BrandColors(
    // login_screen_gradient_bg: 135°, #2563EB → #10B981
    heroGradient = Brush.linearGradient(listOf(BrandPrimary, BrandAccent)),
    // login_brand_panel_bg: vertical #EAF7FF → #EEF8FF → #F7FCFF
    panelGradient = Brush.verticalGradient(
        listOf(BrandPanelTop, BrandPanelMid, BrandPanelBottom)
    ),
    textHeading = Slate900,
    textSecondary = Slate500,
    textTertiary = Slate400,
    cardStroke = StrokeCard,
    success = StatusSuccess,
    warning = StatusWarning,
    danger = StatusDanger,
)

private val DarkBrandColors = LightBrandColors.copy(
    panelGradient = Brush.verticalGradient(listOf(DarkSurfaceElevated, DarkSurface)),
    textHeading = DarkTextPrimary,
    textSecondary = DarkTextMuted,
    textTertiary = Slate500,
    cardStroke = DarkDivider,
)

private val LocalBrandColors = staticCompositionLocalOf { LightBrandColors }

@Composable
fun StockFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val brandColors = if (darkTheme) DarkBrandColors else LightBrandColors

    CompositionLocalProvider(LocalBrandColors provides brandColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** Brand tokens for the current theme. Use alongside [MaterialTheme.colorScheme]. */
val MaterialTheme.brandColors: BrandColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandColors.current
