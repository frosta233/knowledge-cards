package com.example.knowledgecards.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single knowledge card. The [path] is the slash-separated category path,
 * e.g. "方剂学/解表剂/辛温解表". An empty path means "未分类".
 *
 * [sortOrder] keeps the import (insertion) order so the user can choose
 * between "title order" and "import order" browsing.
 */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["path", "title"], unique = true)]
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val path: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Root pseudo-category used for cards whose path is empty. */
const val UNCATEGORIZED = "未分类"

/** Normalizes a raw path string to a canonical slash-separated form. */
fun normalizePath(raw: String): String =
    raw.split('/', '\\')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("/")

/** Returns true when [candidate] is [path] itself or a descendant of it. */
fun isPathWithin(path: String, candidate: String): Boolean {
    if (path.isEmpty()) return true
    return candidate == path || candidate.startsWith("$path/")
}
