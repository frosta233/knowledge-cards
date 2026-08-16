package com.example.knowledgecards.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun observeAllByTitle(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY sortOrder ASC, id ASC")
    fun observeAllByImportOrder(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY title COLLATE NOCASE ASC, id ASC")
    suspend fun getAllByTitle(): List<Card>

    @Query("SELECT * FROM cards ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllByImportOrder(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Card?

    @Query("SELECT * FROM cards WHERE path = :path AND title = :title LIMIT 1")
    suspend fun findByPathAndTitle(path: String, title: String): Card?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM cards")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Category ordering ----

    @Query("SELECT * FROM category_order")
    fun observeCategoryOrders(): Flow<List<CategoryOrder>>

    @Query("SELECT * FROM category_order")
    suspend fun getAllCategoryOrders(): List<CategoryOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryOrder(order: CategoryOrder)

    @Query("DELETE FROM category_order WHERE path = :path")
    suspend fun deleteCategoryOrder(path: String)

    // ---- Batch path operations for category management ----

    @Query("UPDATE cards SET path = :newPath WHERE path = :oldPath")
    suspend fun renameExactPath(oldPath: String, newPath: String)

    @Query("UPDATE cards SET path = :newPrefix || SUBSTR(path, :oldPrefixLength + 1) WHERE path = :oldPrefix OR path LIKE :oldPrefix || '/%'")
    suspend fun renamePathPrefix(oldPrefix: String, newPrefix: String, oldPrefixLength: Int)

    @Query("UPDATE cards SET path = '' WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun clearPath(path: String)

    @Query("DELETE FROM cards WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun deleteByPath(path: String)
}
