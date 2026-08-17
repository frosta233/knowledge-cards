package com.example.knowledgecards.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.example.knowledgecards.domain.AccentColor
import com.example.knowledgecards.domain.ThemeMode

/** Core colors of one accent preset in one theme mode. */
private data class CoreColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * One accent preset: light + dark core colors. Every other color role of
 * the scheme (surfaces, containers, outlines, …) is derived from the
 * matching core, so the whole chrome — navigation bar, top app bars,
 * flashcard page — follows the accent chosen in 设置 → 主题 instead of the
 * stock purple-tinted palette.
 */
private data class AccentPalette(
    val light: CoreColors,
    val dark: CoreColors
)

/** Accent presets used when dynamic color is off (or not supported). */
private val AccentPalettes = mapOf(
    AccentColor.GREEN to AccentPalette(
        light = CoreColors(
            primary = Color(0xFF2E7D32),
            onPrimary = Color.White,
            container = Color(0xFFB7F0B9),
            onContainer = Color(0xFF002105)
        ),
        dark = CoreColors(
            primary = Color(0xFF81C784),
            onPrimary = Color(0xFF00390A),
            container = Color(0xFF1B5E20),
            onContainer = Color(0xFFC8E6C9)
        )
    ),
    AccentColor.BLUE to AccentPalette(
        light = CoreColors(
            primary = Color(0xFF1565C0),
            onPrimary = Color.White,
            container = Color(0xFFD6E3FF),
            onContainer = Color(0xFF001B3F)
        ),
        dark = CoreColors(
            primary = Color(0xFFAAC7FF),
            onPrimary = Color(0xFF002F66),
            container = Color(0xFF004494),
            onContainer = Color(0xFFD6E3FF)
        )
    ),
    AccentColor.ORANGE to AccentPalette(
        light = CoreColors(
            primary = Color(0xFFE65100),
            onPrimary = Color.White,
            container = Color(0xFFFFDBC9),
            onContainer = Color(0xFF331000)
        ),
        dark = CoreColors(
            primary = Color(0xFFFFB68C),
            onPrimary = Color(0xFF541E00),
            container = Color(0xFF7A3400),
            onContainer = Color(0xFFFFDBC9)
        )
    ),
    AccentColor.PURPLE to AccentPalette(
        light = CoreColors(
            primary = Color(0xFF6A1B9A),
            onPrimary = Color.White,
            container = Color(0xFFEDDBFF),
            onContainer = Color(0xFF24004A)
        ),
        dark = CoreColors(
            primary = Color(0xFFD7B8FF),
            onPrimary = Color(0xFF3D006A),
            container = Color(0xFF53008E),
            onContainer = Color(0xFFEDDBFF)
        )
    ),
    // Morandi palette (low saturation, dusty tones)
    AccentColor.SAGE to AccentPalette(
        light = CoreColors(
            primary = Color(0xFF6B7F6E),
            onPrimary = Color.White,
            container = Color(0xFFDDEADF),
            onContainer = Color(0xFF1B3320)
        ),
        dark = CoreColors(
            primary = Color(0xFFA9BFAE),
            onPrimary = Color(0xFF1B3320),
            container = Color(0xFF3E5142),
            onContainer = Color(0xFFDDEADF)
        )
    ),
    AccentColor.DUSTY_BLUE to AccentPalette(
        light = CoreColors(
            primary = Color(0xFF6E8A99),
            onPrimary = Color.White,
            container = Color(0xFFD9E9F2),
            onContainer = Color(0xFF162A35)
        ),
        dark = CoreColors(
            primary = Color(0xFFA5C2D2),
            onPrimary = Color(0xFF162A35),
            container = Color(0xFF3A5462),
            onContainer = Color(0xFFD9E9F2)
        )
    ),
    AccentColor.TERRACOTTA to AccentPalette(
        light = CoreColors(
            primary = Color(0xFFB07B68),
            onPrimary = Color.White,
            container = Color(0xFFF5E0D8),
            onContainer = Color(0xFF3C1F14)
        ),
        dark = CoreColors(
            primary = Color(0xFFE0B49F),
            onPrimary = Color(0xFF3C1F14),
            container = Color(0xFF6E4331),
            onContainer = Color(0xFFF5E0D8)
        )
    )
)

/**
 * Builds a full scheme from the accent: every surface role is blended from
 * the accent container, so backgrounds visibly follow the chosen color.
 */
private fun CoreColors.toColorScheme(dark: Boolean): ColorScheme {
    // Neutral base the accent is blended into (near-black in dark mode).
    val base = if (dark) Color(0xFF121418) else Color.White
    // Gray roles derived from the on-container text color, tinted by the accent.
    val onSurfaceVariant = if (dark) {
        lerp(onContainer, Color.Black, 0.25f)
    } else {
        lerp(onContainer, Color.White, 0.30f)
    }
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            surface = lerp(base, container, 0.10f),
            onSurface = onContainer,
            surfaceVariant = lerp(base, container, 0.16f),
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = lerp(base, container, 0.14f),
            surfaceContainerHigh = lerp(base, container, 0.18f),
            surfaceContainerHighest = lerp(base, container, 0.22f),
            surfaceDim = lerp(base, container, 0.08f),
            surfaceBright = lerp(base, container, 0.14f),
            background = lerp(base, container, 0.10f),
            onBackground = onContainer,
            outline = lerp(onSurfaceVariant, Color.Black, 0.35f),
            outlineVariant = lerp(onSurfaceVariant, Color.Black, 0.55f),
            inverseSurface = lerp(Color.White, container, 0.12f),
            inverseOnSurface = Color(0xFF1C1D1F),
            inversePrimary = lerp(primary, Color.White, 0.35f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            surface = lerp(base, container, 0.30f),
            onSurface = onContainer,
            surfaceVariant = lerp(base, container, 0.42f),
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = lerp(base, container, 0.40f),
            surfaceContainerHigh = lerp(base, container, 0.50f),
            surfaceContainerHighest = lerp(base, container, 0.62f),
            surfaceDim = lerp(base, container, 0.22f),
            surfaceBright = lerp(base, container, 0.38f),
            background = lerp(base, container, 0.30f),
            onBackground = onContainer,
            outline = lerp(onSurfaceVariant, Color.White, 0.35f),
            outlineVariant = lerp(onSurfaceVariant, Color.White, 0.72f),
            inverseSurface = Color(0xFF2C2E30),
            inverseOnSurface = Color(0xFFF2F2F2),
            inversePrimary = lerp(primary, Color.White, 0.25f)
        )
    }
}

/**
 * App bar colors for all screens: the bar takes the accent container color
 * (primaryContainer) so the title bar follows the theme chosen in settings.
 */
@Composable
fun appTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
fun KnowledgeCardsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        accentColor == AccentColor.SYSTEM && darkTheme -> dynamicDarkColorScheme(context)
        accentColor == AccentColor.SYSTEM -> dynamicLightColorScheme(context)
        else -> AccentPalettes.getValue(accentColor).let { palette ->
            (if (darkTheme) palette.dark else palette.light).toColorScheme(darkTheme)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
