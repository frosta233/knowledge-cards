package com.example.knowledgecards.domain

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.knowledgecards.data.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/** Theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Immutable snapshot of all user settings and the browse progress. */
data class AppSettings(
    val lastCardId: Long = 0L,
    val sortMode: SortMode = SortMode.TITLE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSizeSp: Float = 18f,
    val widgetPathExpanded: Boolean = false
)

/**
 * Shared progress + settings store, used by the app and the widget so both
 * always show the same "current card".
 */
class ProgressStore(private val context: Context) {

    private object Keys {
        val LAST_CARD_ID = longPreferencesKey("last_card_id")
        val SORT_MODE = intPreferencesKey("sort_mode")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val WIDGET_PATH_EXPANDED = booleanPreferencesKey("widget_path_expanded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            lastCardId = prefs[Keys.LAST_CARD_ID] ?: 0L,
            sortMode = SortMode.entries.getOrElse(prefs[Keys.SORT_MODE] ?: 0) { SortMode.TITLE },
            themeMode = ThemeMode.entries.getOrElse(prefs[Keys.THEME_MODE] ?: 0) { ThemeMode.SYSTEM },
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 18f,
            widgetPathExpanded = prefs[Keys.WIDGET_PATH_EXPANDED] ?: false
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLastCardId(cardId: Long) {
        context.dataStore.edit { it[Keys.LAST_CARD_ID] = cardId }
    }

    suspend fun setSortMode(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_MODE] = mode.ordinal }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.ordinal }
    }

    suspend fun setFontSize(sp: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = sp }
    }

    suspend fun setWidgetPathExpanded(expanded: Boolean) {
        context.dataStore.edit { it[Keys.WIDGET_PATH_EXPANDED] = expanded }
    }
}
