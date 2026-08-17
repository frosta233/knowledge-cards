package com.example.knowledgecards.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.data.export.CardExporter
import com.example.knowledgecards.domain.AccentColor
import com.example.knowledgecards.domain.AppSettings
import com.example.knowledgecards.domain.ThemeMode
import com.example.knowledgecards.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp
    private val store = app.container.progressStore

    val settings = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _exportResult = MutableStateFlow<Pair<Int, Int>?>(null)
    val exportResult: StateFlow<Pair<Int, Int>?> = _exportResult

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            store.setThemeMode(mode)
            WidgetUpdater.update(getApplication())
        }
    }

    fun setAccentColor(color: com.example.knowledgecards.domain.AccentColor) {
        viewModelScope.launch {
            store.setAccentColor(color)
            WidgetUpdater.update(getApplication())
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            store.setSortMode(mode)
            // The widget shares the browse order: re-render it immediately so
            // its card sequence and counter match the app's new order.
            WidgetUpdater.update(getApplication())
        }
    }

    fun setFontSize(sp: Float) {
        viewModelScope.launch { store.setFontSize(sp) }
    }

    fun setWidgetFontSize(sp: Float) {
        viewModelScope.launch {
            store.setWidgetFontSize(sp)
            // The widget has its own font size setting: re-render it so the
            // change shows up immediately.
            WidgetUpdater.update(getApplication())
        }
    }

    fun exportTo(treeUri: Uri) {
        viewModelScope.launch {
            _exporting.value = true
            try {
                val cards = app.container.cardRepository.getCards(SortMode.TITLE)
                val result = CardExporter.export(getApplication(), treeUri, cards)
                _exportResult.value = result.exported to result.failed
            } finally {
                _exporting.value = false
            }
        }
    }

    fun consumeExportResult() {
        _exportResult.value = null
    }
}
