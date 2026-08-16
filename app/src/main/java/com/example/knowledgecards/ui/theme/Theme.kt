package com.example.knowledgecards.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.knowledgecards.domain.AccentColor
import com.example.knowledgecards.domain.ThemeMode

/** Accent presets used when dynamic color is off (or not supported). */
private val AccentLightColors = mapOf(
    AccentColor.GREEN to lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F0B9),
        onPrimaryContainer = Color(0xFF002105),
        secondaryContainer = Color(0xFFB7F0B9),
        onSecondaryContainer = Color(0xFF002105)
    ),
    AccentColor.BLUE to lightColorScheme(
        primary = Color(0xFF1565C0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3F),
        secondaryContainer = Color(0xFFD6E3FF),
        onSecondaryContainer = Color(0xFF001B3F)
    ),
    AccentColor.ORANGE to lightColorScheme(
        primary = Color(0xFFE65100),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBC9),
        onPrimaryContainer = Color(0xFF331000),
        secondaryContainer = Color(0xFFFFDBC9),
        onSecondaryContainer = Color(0xFF331000)
    ),
    AccentColor.PURPLE to lightColorScheme(
        primary = Color(0xFF6A1B9A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDDBFF),
        onPrimaryContainer = Color(0xFF24004A),
        secondaryContainer = Color(0xFFEDDBFF),
        onSecondaryContainer = Color(0xFF24004A)
    ),
    // Morandi palette (low saturation, dusty tones)
    AccentColor.SAGE to lightColorScheme(
        primary = Color(0xFF6B7F6E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDEADF),
        onPrimaryContainer = Color(0xFF1B3320),
        secondaryContainer = Color(0xFFDDEADF),
        onSecondaryContainer = Color(0xFF1B3320)
    ),
    AccentColor.DUSTY_BLUE to lightColorScheme(
        primary = Color(0xFF6E8A99),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E9F2),
        onPrimaryContainer = Color(0xFF162A35),
        secondaryContainer = Color(0xFFD9E9F2),
        onSecondaryContainer = Color(0xFF162A35)
    ),
    AccentColor.TERRACOTTA to lightColorScheme(
        primary = Color(0xFFB07B68),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF5E0D8),
        onPrimaryContainer = Color(0xFF3C1F14),
        secondaryContainer = Color(0xFFF5E0D8),
        onSecondaryContainer = Color(0xFF3C1F14)
    )
)

private val AccentDarkColors = mapOf(
    AccentColor.GREEN to darkColorScheme(
        primary = Color(0xFF81C784),
        onPrimary = Color(0xFF00390A),
        primaryContainer = Color(0xFF1B5E20),
        onPrimaryContainer = Color(0xFFC8E6C9),
        secondaryContainer = Color(0xFF1B5E20),
        onSecondaryContainer = Color(0xFFC8E6C9)
    ),
    AccentColor.BLUE to darkColorScheme(
        primary = Color(0xFFAAC7FF),
        onPrimary = Color(0xFF002F66),
        primaryContainer = Color(0xFF004494),
        onPrimaryContainer = Color(0xFFD6E3FF),
        secondaryContainer = Color(0xFF004494),
        onSecondaryContainer = Color(0xFFD6E3FF)
    ),
    AccentColor.ORANGE to darkColorScheme(
        primary = Color(0xFFFFB68C),
        onPrimary = Color(0xFF541E00),
        primaryContainer = Color(0xFF7A3400),
        onPrimaryContainer = Color(0xFFFFDBC9),
        secondaryContainer = Color(0xFF7A3400),
        onSecondaryContainer = Color(0xFFFFDBC9)
    ),
    AccentColor.PURPLE to darkColorScheme(
        primary = Color(0xFFD7B8FF),
        onPrimary = Color(0xFF3D006A),
        primaryContainer = Color(0xFF53008E),
        onPrimaryContainer = Color(0xFFEDDBFF),
        secondaryContainer = Color(0xFF53008E),
        onSecondaryContainer = Color(0xFFEDDBFF)
    ),
    // Morandi palette (low saturation, dusty tones)
    AccentColor.SAGE to darkColorScheme(
        primary = Color(0xFFA9BFAE),
        onPrimary = Color(0xFF1B3320),
        primaryContainer = Color(0xFF3E5142),
        onPrimaryContainer = Color(0xFFDDEADF),
        secondaryContainer = Color(0xFF3E5142),
        onSecondaryContainer = Color(0xFFDDEADF)
    ),
    AccentColor.DUSTY_BLUE to darkColorScheme(
        primary = Color(0xFFA5C2D2),
        onPrimary = Color(0xFF162A35),
        primaryContainer = Color(0xFF3A5462),
        onPrimaryContainer = Color(0xFFD9E9F2),
        secondaryContainer = Color(0xFF3A5462),
        onSecondaryContainer = Color(0xFFD9E9F2)
    ),
    AccentColor.TERRACOTTA to darkColorScheme(
        primary = Color(0xFFE0B49F),
        onPrimary = Color(0xFF3C1F14),
        primaryContainer = Color(0xFF6E4331),
        onPrimaryContainer = Color(0xFFF5E0D8),
        secondaryContainer = Color(0xFF6E4331),
        onSecondaryContainer = Color(0xFFF5E0D8)
    )
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
        accentColor != AccentColor.SYSTEM && darkTheme -> AccentDarkColors.getValue(accentColor)
        else -> AccentLightColors.getValue(accentColor)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
