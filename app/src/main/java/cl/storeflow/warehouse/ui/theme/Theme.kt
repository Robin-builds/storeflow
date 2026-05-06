package cl.storeflow.warehouse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = Verde700,
    onPrimary           = Color.White,
    primaryContainer    = Verde50,
    onPrimaryContainer  = Verde900,

    secondary              = Verde600,
    onSecondary            = Color.White,
    secondaryContainer     = Verde200,
    onSecondaryContainer   = Verde800,

    background       = Slate50,
    onBackground     = Slate900,
    surface          = Color.White,
    onSurface        = Slate900,
    surfaceVariant   = Slate100,
    onSurfaceVariant = Slate700,

    outline        = Slate300,
    outlineVariant = Slate100,

    error            = Rojo600,
    onError          = Color.White,
    errorContainer   = Rojo50,
    onErrorContainer = Rojo700,
)

@Composable
fun StoreFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = StoreFlowTypography,
        shapes      = StoreFlowShapes,
        content     = content
    )
}
