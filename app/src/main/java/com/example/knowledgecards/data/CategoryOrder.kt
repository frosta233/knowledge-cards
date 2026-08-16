package com.example.knowledgecards.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Manual ordering of sibling category nodes. [path] is a full category path
 * (e.g. "方剂学/解表剂"); nodes without a record fall back to name ordering
 * and are placed after manually ordered ones.
 */
@Entity(tableName = "category_order")
data class CategoryOrder(
    @PrimaryKey val path: String,
    val position: Int
)
