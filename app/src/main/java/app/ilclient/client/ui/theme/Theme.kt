package app.ilclient.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    AMETHYST_NIGHT,
    MIDNIGHT_BLUE,
    EMERALD_DARK,
    CRIMSON_DARK,
    LIGHT_MINIMAL,
    SYSTEM_DEFAULT
}

data class ILClientExtendedColors(
    val elevatedSurface: Color,
    val secondarySurface: Color,
    val electricMagenta: Color,
    val accentOrange: Color,
    val connectedGreen: Color,
    val errorRed: Color
)

val LocalILClientColors = staticCompositionLocalOf {
    ILClientExtendedColors(
        elevatedSurface = Color(0xFF121026),
        secondarySurface = Color(0xFF1B1930),
        electricMagenta = Color(0xFFF044A6),
        accentOrange = Color(0xFFFF8A3D),
        connectedGreen = Color(0xFF22C55E),
        errorRed = Color(0xFFEF4444)
    )
}

private val AmethystNightScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFF6F3FF),
    secondary = Color(0xFFF044A6),
    tertiary = Color(0xFFFF8A3D),
    background = Color(0xFF0A0915),
    surface = Color(0xFF121026),
    surfaceVariant = Color(0xFF1B1930),
    onBackground = Color(0xFFF6F3FF),
    onSurface = Color(0xFFF6F3FF),
    onSurfaceVariant = Color(0xFFAAA5BD),
    error = Color(0xFFEF4444)
)

private val MidnightBlueScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFFFF8A3D),
    background = Color(0xFF070B19),
    surface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFF1E293B),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val EmeraldDarkScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFFF8A3D),
    background = Color(0xFF06120E),
    surface = Color(0xFF0B211A),
    surfaceVariant = Color(0xFF133329)
)

private val CrimsonDarkScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    secondary = Color(0xFFFB7185),
    tertiary = Color(0xFFFF8A3D),
    background = Color(0xFF14070B),
    surface = Color(0xFF240D14),
    surfaceVariant = Color(0xFF36141F)
)

private val LightMinimalScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    secondary = Color(0xFFDB2777),
    tertiary = Color(0xFFEA580C),
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1ECF9)
)

@Composable
fun ILClientTheme(
    themeMode: AppThemeMode = AppThemeMode.AMETHYST_NIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val scheme = when {
        themeMode == AppThemeMode.SYSTEM_DEFAULT && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == AppThemeMode.SYSTEM_DEFAULT -> if (systemDark) AmethystNightScheme else LightMinimalScheme
        themeMode == AppThemeMode.MIDNIGHT_BLUE -> MidnightBlueScheme
        themeMode == AppThemeMode.EMERALD_DARK -> EmeraldDarkScheme
        themeMode == AppThemeMode.CRIMSON_DARK -> CrimsonDarkScheme
        themeMode == AppThemeMode.LIGHT_MINIMAL -> LightMinimalScheme
        else -> AmethystNightScheme
    }
    val extended = ILClientExtendedColors(
        elevatedSurface = scheme.surface,
        secondarySurface = scheme.surfaceVariant,
        electricMagenta = Color(0xFFF044A6),
        accentOrange = Color(0xFFFF8A3D),
        connectedGreen = Color(0xFF22C55E),
        errorRed = scheme.error
    )
    CompositionLocalProvider(LocalILClientColors provides extended) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}