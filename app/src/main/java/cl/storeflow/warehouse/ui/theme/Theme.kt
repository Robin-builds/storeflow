package cl.storeflow.warehouse.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary             = Verde700,
    onPrimary           = Color.White,
    primaryContainer    = Verde50,
    onPrimaryContainer  = Verde900,

    secondary             = Verde600,
    onSecondary           = Color.White,
    secondaryContainer    = Verde200,
    onSecondaryContainer  = Verde800,

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

private val OscuroColorScheme = darkColorScheme(
    primary             = OscuroPrimary,
    onPrimary           = Color(0xFF002919),
    primaryContainer    = OscuroPrimaryC,
    onPrimaryContainer  = Verde200,

    secondary             = OscuroPrimary,
    onSecondary           = Color(0xFF002919),
    secondaryContainer    = Color(0xFF1A3A30),
    onSecondaryContainer  = Verde200,

    background       = OscuroBg,
    onBackground     = OscuroOnBg,
    surface          = OscuroSurface,
    onSurface        = OscuroOnBg,
    surfaceVariant   = OscuroVariant,
    onSurfaceVariant = OscuroOnVariant,

    outline        = OscuroOutline,
    outlineVariant = Color(0xFF383A52),

    error            = OscuroError,
    onError          = Color(0xFF2D0009),
    errorContainer   = OscuroErrorC,
    onErrorContainer = OscuroOnErrorC,
)

private val OscuroPlusColorScheme = darkColorScheme(
    primary             = OscuroPlusPrimary,
    onPrimary           = Color(0xFF002919),
    primaryContainer    = OscuroPlusPrimaryC,
    onPrimaryContainer  = Verde200,

    secondary             = OscuroPlusPrimary,
    onSecondary           = Color(0xFF002919),
    secondaryContainer    = Color(0xFF0F2820),
    onSecondaryContainer  = Verde200,

    background       = OscuroPlusBg,
    onBackground     = OscuroPlusOnBg,
    surface          = OscuroPlusSurface,
    onSurface        = OscuroPlusOnBg,
    surfaceVariant   = OscuroPlusVariant,
    onSurfaceVariant = OscuroPlusOnVariant,

    outline        = OscuroPlusOutline,
    outlineVariant = Color(0xFF2A2E40),

    error            = OscuroPlusError,
    onError          = Color(0xFF2D0009),
    errorContainer   = OscuroPlusErrorC,
    onErrorContainer = OscuroPlusOnErrorC,
)

@Composable
fun StoreFlowTheme(
    tema: TemaApp = TemaApp.CLARO,
    content: @Composable () -> Unit
) {
    val colorScheme = when (tema) {
        TemaApp.CLARO       -> LightColorScheme
        TemaApp.OSCURO      -> OscuroColorScheme
        TemaApp.OSCURO_PLUS -> OscuroPlusColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                tema == TemaApp.CLARO
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = StoreFlowTypography,
        shapes      = StoreFlowShapes,
        content     = content
    )
}
