package com.tunnellight.stop_scroll.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalChartColors = staticCompositionLocalOf { LightChartColors }

private val StopScrollShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Dynamic colour is offered but off by default. The chart palette never follows it: those
 * eight hues were validated as a set against these specific surfaces, and letting a wallpaper
 * re-tint them would quietly break the colour-blind separation they were chosen for.
 */
@Composable
fun StopScrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalChartColors provides if (darkTheme) DarkChartColors else LightChartColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StopScrollTypography,
            shapes = StopScrollShapes,
            content = content,
        )
    }
}
