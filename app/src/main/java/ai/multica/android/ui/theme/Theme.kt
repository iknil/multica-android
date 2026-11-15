package ai.multica.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorDarkActionText = androidx.compose.ui.graphics.Color(0xFF062B66)

private val LightColors = lightColorScheme(
    primary = ActionLight,
    onPrimary = SurfaceLight,
    background = CanvasLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    secondary = SignalLight,
    surfaceVariant = StatusLayerLight,
    outlineVariant = DividerLight,
)

private val DarkColors = darkColorScheme(
    primary = ActionDark,
    onPrimary = ColorDarkActionText,
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    secondary = SignalDark,
    surfaceVariant = StatusLayerDark,
    outlineVariant = DividerDark,
)

@Composable
fun MulticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
