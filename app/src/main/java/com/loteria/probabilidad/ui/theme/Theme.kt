package com.loteria.probabilidad.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colores personalizados - Paleta dorada/premium para lotería
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFFFE082)
private val GoldDark = Color(0xFFB8860B)

private val DeepPurple = Color(0xFF4A148C)
private val DeepPurpleLight = Color(0xFF7B1FA2)

private val Success = Color(0xFF2E7D32)
private val Error = Color(0xFFC62828)

// Tema oscuro
private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.Black,
    primaryContainer = GoldDark,
    onPrimaryContainer = GoldLight,
    
    secondary = DeepPurpleLight,
    onSecondary = Color.White,
    secondaryContainer = DeepPurple,
    onSecondaryContainer = Color(0xFFE1BEE7),
    
    tertiary = Color(0xFF26A69A),
    onTertiary = Color.White,
    
    background = Color(0xFF121212),
    onBackground = Color(0xFFE1E1E1),
    
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE1E1E1),
    
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCACACA),
    
    error = Error,
    onError = Color.White
)

// Tema claro
private val LightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = GoldLight,
    onPrimaryContainer = Color(0xFF3E2723),
    
    secondary = DeepPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1BEE7),
    onSecondaryContainer = DeepPurple,
    
    tertiary = Color(0xFF00796B),
    onTertiary = Color.White,
    
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1C1B1F),
    
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    
    error = Error,
    onError = Color.White
)

@Composable
fun LoteriaProbabilidadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Deshabilitado para mantener el tema dorado
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
