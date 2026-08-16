package com.example.knowledgecards.ui

/** Screens of the single-activity navigation stack. */
sealed interface Screen {
    data object Browse : Screen
    data object Directory : Screen
    data class Editor(val cardId: Long) : Screen
    data object Settings : Screen
}
