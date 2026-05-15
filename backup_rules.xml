package it.officina.riparazioni.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Stati colorati
val ColorAttesa      = Color(0xFFEF9F27)
val ColorLavorazione = Color(0xFFD85A30)
val ColorPronto      = Color(0xFF639922)
val ColorConsegnato  = Color(0xFFB4B2A9)
val ColorDanger      = Color(0xFFA32D2D)
val ColorDangerBg    = Color(0xFFFCEBEB)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F2937),
    onPrimary = Color.White,
    secondary = Color(0xFF4B5563),
    background = Color(0xFFFAFAF7),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = Color(0xFF4B5563),
    error = ColorDanger
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1F2937),
    secondary = Color(0xFFD1D5DB),
    background = Color(0xFF111827),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB),
    error = Color(0xFFF87171)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun RiparazioniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
