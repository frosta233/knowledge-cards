package com.example.knowledgecards.data.import

/** Outcome of an import batch. */
data class ImportResult(
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    /** Files that failed, with the reason. */
    val failed: List<Pair<String, String>> = emptyList()
) {
    val total: Int get() = newCount + updatedCount + failed.size
}
