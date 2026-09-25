package com.example.knowledgecards.data

import androidx.room.Entity

/**
 * Manual ordering of sibling category nodes inside one book. [path] is a full
 * category path (e.g. "方剂学/解表剂"); nodes without a record fall back to
 * name ordering and are placed after manually ordered ones.
 *
 * The primary key is (bookId, path): two books may well define the same
 * category path, and their manual orders must stay independent.
 */
@Entity(tableName = "category_order", primaryKeys = ["bookId", "path"])
data class CategoryOrder(
    val bookId: Long = 0L,
    val path: String,
    val position: Int
)
