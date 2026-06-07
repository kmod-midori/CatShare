package moe.reimu.catshare.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = OrangeVariant40,
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = Amber40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = AmberLight40,
    onSecondaryContainer = Color(0xFF3A2500),

    tertiary = Mint40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = MintLight40,
    onTertiaryContainer = Color(0xFF0B2E1B),

    error = Error40,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Cream40,
    onBackground = TextOnSurface40,

    surface = Cream40,
    onSurface = TextOnSurface40,

    surfaceVariant = WarmGray40,
    onSurfaceVariant = TextOnVariant40,

    surfaceTint = Orange40,

    outline = Outline40,
    outlineVariant = Color(0xFFD7C8B5),

    scrim = Color(0x99000000),

    inverseSurface = Color(0xFF3A2E1F),
    inverseOnSurface = Color(0xFFF5EDE2),
    inversePrimary = OrangeLight40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Color(0xFF3A1B00),
    primaryContainer = OrangeVariant80,
    onPrimaryContainer = Color(0xFFFFDBC7),

    secondary = Amber80,
    onSecondary = Color(0xFF3A2500),
    secondaryContainer = AmberVariant80,
    onSecondaryContainer = Color(0xFFFFDDB3),

    tertiary = Mint80,
    onTertiary = Color(0xFF0B2E1B),
    tertiaryContainer = MintVariant80,
    onTertiaryContainer = Color(0xFFC7EFD6),

    error = Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Cream80,
    onBackground = TextOnSurface80,

    surface = Cream80,
    onSurface = TextOnSurface80,

    surfaceVariant = WarmGray80,
    onSurfaceVariant = TextOnVariant80,

    surfaceTint = Orange80,

    outline = Outline80,
    outlineVariant = Color(0xFF544B40),

    scrim = Color(0xCC000000),

    inverseSurface = Color(0xFFF5EDE2),
    inverseOnSurface = Color(0xFF3A2E1F),
    inversePrimary = Orange40,
)

data class CatShareExtraColors(
    val heroGradientStart: androidx.compose.ui.graphics.Color,
    val heroGradientEnd: androidx.compose.ui.graphics.Color,
    val heroTextPrimary: androidx.compose.ui.graphics.Color,
    val heroTextSecondary: androidx.compose.ui.graphics.Color,
)

private val LightExtraColors = CatShareExtraColors(
    heroGradientStart = OrangeLight40,
    heroGradientEnd = AmberLight40,
    heroTextPrimary = Color(0xFFFFFFFF),
    heroTextSecondary = Color(0xFFFFFFFF),
)

private val DarkExtraColors = CatShareExtraColors(
    heroGradientStart = OrangeVariant80,
    heroGradientEnd = MintVariant80,
    heroTextPrimary = Color(0xFFFFFFFF),
    heroTextSecondary = Color(0xFFE0D5C3),
)

val LocalCatShareExtraColors = staticCompositionLocalOf { LightExtraColors }

@Composable
fun CatShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    // 适配沉浸式状态栏
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalCatShareExtraColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CatShareTypography,
            shapes = CatShareShapes,
            content = content,
        )
    }
}
