package `in`.codelif.jportal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import `in`.codelif.jportal.data.Palette
import `in`.codelif.jportal.data.ThemeMode

/** colors outside the material roles: jportal's sgpa green / cgpa blue and attendance verdicts */
@Immutable
data class ExtraColors(
    val sgpa: Color,
    val cgpa: Color,
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val bad: Color,
    val warn: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFF2E7D32), Color.White, Color(0xFFC8E6C9), Color(0xFFC62828), Color(0xFFF9A825))
}

private fun extras(scheme: ColorScheme, dark: Boolean): ExtraColors {
    // keep jportal's chart hues but pull them toward the scheme so wallpaper themes don't clash
    val sgpaBase = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val cgpaBase = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val good = if (dark) Color(0xFF7BD88F) else Color(0xFF1E7D3A)
    return ExtraColors(
        sgpa = lerp(sgpaBase, scheme.primary, 0.12f),
        cgpa = lerp(cgpaBase, scheme.primary, 0.12f),
        good = good,
        onGood = if (dark) Color(0xFF00391A) else Color.White,
        goodContainer = if (dark) Color(0xFF16432A) else Color(0xFFCDEFD6),
        bad = scheme.error,
        warn = if (dark) Color(0xFFFFC857) else Color(0xFF9A6700),
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.7f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.8f),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun JPortalTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: Palette = Palette.Wallpaper,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode)
    val context = LocalContext.current
    val dynamic = palette == Palette.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> jportalDark
        else -> jportalLight
    }
    if (dark && amoled) scheme = scheme.amoled()
    CompositionLocalProvider(LocalExtraColors provides extras(scheme, dark)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JPortalTypography,
            shapes = JPortalShapes,
            content = content,
        )
    }
}
