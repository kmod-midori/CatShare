package moe.reimu.catshare.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// CatShare 主题 — Material 3 Expressive
// 支持：动态取色 (Android 12+) / 深色模式 / Edge-to-Edge
// ============================================================

// ── 浅色主题 ──
private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Orange99,
    primaryContainer = Orange90,
    onPrimaryContainer = Orange10,
    secondary = Amber40,
    onSecondary = Amber99,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    tertiary = Mint40,
    onTertiary = Mint99,
    tertiaryContainer = Mint90,
    onTertiaryContainer = Mint10,
    error = Red40,
    onError = Red90,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral30,
    surfaceTint = Orange40,
    outline = Neutral50,
    outlineVariant = Neutral80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = Orange80,
    scrim = Neutral10,
)

// ── 深色主题 ──
private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Orange20,
    primaryContainer = Orange30,
    onPrimaryContainer = Orange90,
    secondary = Amber80,
    onSecondary = Amber20,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = Mint80,
    onTertiary = Mint20,
    tertiaryContainer = Mint30,
    onTertiaryContainer = Mint90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80,
    surfaceTint = Orange80,
    outline = Neutral60,
    outlineVariant = Neutral30,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Orange40,
    scrim = Neutral10,
)

/**
 * CatShare 全局主题。
 *
 * - Android 12+ 默认启用动态取色
 * - 自动跟随系统深色模式
 * - 使用 M3 Expressive 风格的形状和排版
 * - 启用 Edge-to-Edge 全屏沉浸
 */
@Composable
fun CatShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Edge-to-Edge 全屏沉浸
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as androidx.activity.ComponentActivity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CatShareTypography,
        shapes = CatShareShapes,
        content = content
    )
}