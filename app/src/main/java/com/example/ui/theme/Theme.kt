package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val KindredDarkColorScheme = darkColorScheme(
    primary = CoralRose,
    onPrimary = Color.White,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = CoralRoseLight,
    secondary = ChampagneGold,
    onSecondary = Color(0xFF281606),
    secondaryContainer = ChampagneContainerDark,
    onSecondaryContainer = ChampagneGoldLight,
    tertiary = EmeraldMint,
    onTertiary = Color(0xFF042220),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA6F2EA),
    background = PlumObsidian,
    onBackground = WarmIvoryText,
    surface = PlumSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = PlumSurfaceElevated,
    onSurfaceVariant = MutedRoseGray,
    surfaceContainerLow = PlumSurface,
    surfaceContainer = PlumSurfaceElevated,
    surfaceContainerHigh = PlumSurfaceHighest,
    outline = PlumOutline,
    error = ErrorCrimson,
    onError = Color.White
)

private val KindredLightColorScheme = lightColorScheme(
    primary = CoralRose,
    onPrimary = Color.White,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = CoralRoseDark,
    secondary = Color(0xFFB56324),
    onSecondary = Color.White,
    secondaryContainer = ChampagneContainerLight,
    onSecondaryContainer = Color(0xFF422106),
    tertiary = Color(0xFF1A8E83),
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF063833),
    background = WarmPorcelain,
    onBackground = EspressoInk,
    surface = WarmCardWhite,
    onSurface = EspressoInk,
    surfaceVariant = SoftBlushSurface,
    onSurfaceVariant = EspressoMuted,
    surfaceContainerLow = WarmPorcelain,
    surfaceContainer = SoftBlushSurface,
    surfaceContainerHigh = SoftBlushElevated,
    outline = BlushOutline,
    error = ErrorCrimson,
    onError = Color.White
)

val KindredShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun KindredTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KindredDarkColorScheme
        else -> KindredLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KindredTypography,
        shapes = KindredShapes,
        content = content
    )
}
