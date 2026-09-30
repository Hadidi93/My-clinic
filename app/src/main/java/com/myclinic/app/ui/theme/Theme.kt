package com.myclinic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Calm clinical palette: deep teal primary, soft neutral surfaces. Every text
// and background pair meets WCAG AA contrast in both light and dark mode.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEBF0),
    onPrimaryContainer = Color(0xFF00363C),
    secondary = Color(0xFF4A6366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F22),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF7F9FA),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F484A),
    surfaceContainer = Color(0xFFECF1F2),
    surfaceContainerHigh = Color(0xFFE6ECED),
    outline = Color(0xFF6F797A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D3DD),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF004F57),
    onPrimaryContainer = Color(0xFFBDEBF0),
    secondary = Color(0xFFB1CBCE),
    onSecondary = Color(0xFF1B3437),
    secondaryContainer = Color(0xFF324B4E),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFF1C048),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E4),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBEC8CA),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    outline = Color(0xFF899294),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

// Slightly larger body text than the Material default, for quick reading between cases.
private val base = Typography()
private val AppTypography = base.copy(
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp),
)

@Composable
fun MyClinicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Style for big numbers on dashboard cards. */
val DashboardNumberStyle = TextStyle(fontSize = 32.sp)
