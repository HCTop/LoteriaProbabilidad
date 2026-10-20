package com.loteria.probabilidad.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * Tema Material 3 "noche y oro":
 *  - primary   = índigo profundo (acciones, chips seleccionados, títulos de sección)
 *  - secondary = oro (las bolas y los detalles "de lotería")
 *  - tertiary  = verde azulado (aciertos, estados positivos)
 *  - Superficies por niveles (surfaceContainerLowest … Highest): las tarjetas se separan del
 *    fondo por tono, no por sombras fuertes, como recomienda Material 3.
 * Todos los colores de la app deberían salir de aquí (MaterialTheme.colorScheme.*),
 * así el modo oscuro funciona solo.
 */

// Tema claro
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),

    secondary = Color(0xFF8A6A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF2B2000),

    tertiary = Color(0xFF00696B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF1F2),
    onTertiaryContainer = Color(0xFF002020),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF8F8FC),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFECECF2),
    surfaceContainerHigh = Color(0xFFE6E6EC),
    surfaceContainerHighest = Color(0xFFE1E1E7),
    surfaceBright = Color(0xFFF8F8FC),
    surfaceDim = Color(0xFFD9D9DF)
)

// Tema oscuro
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF2A3A9A),
    onPrimaryContainer = Color(0xFFDEE0FF),

    secondary = Color(0xFFF0C24B),
    onSecondary = Color(0xFF483700),
    secondaryContainer = Color(0xFF685000),
    onSecondaryContainer = Color(0xFFFFE08A),

    tertiary = Color(0xFF80D4D6),
    onTertiary = Color(0xFF003738),
    tertiaryContainer = Color(0xFF004F51),
    onTertiaryContainer = Color(0xFF9CF1F2),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),

    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318)
)

/** Esquinas: tarjetas grandes y suaves, chips y botones redondeados. */
val FormasApp = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LoteriaProbabilidadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Desactivado: la app tiene su propia identidad de color
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Barra de estado del mismo color que el fondo (más limpio que una franja de color)
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FormasApp,
        content = content
    )
}
