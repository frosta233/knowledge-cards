package com.example.knowledgecards.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * All card queries are scoped to one book: the directory, the flashcards, the
 * widget and the editors only ever see the selected book's cards.
 */
@Dao
interface CardDao {

    @Query(
        "SELECT * FROM cards WHERE bookId = :bookId " +
            "ORDER BY title COLLATE NOCASE ASC, id ASC"
    )
    fun observeAllByTitle(bookId: Long): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    fun observeAllByImportOrder(bookId: Long): Flow<List<Card>>

    @Query(
        "SELECT * FROM cards WHERE bookId = :bookId " +
            "ORDER BY title COLLATE NOCASE ASC, id ASC"
    )
    suspend fun getAllByTitle(bookId: Long): List<Card>

    @Query("SELECT * FROM cards WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllByImportOrder(bookId: Long): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Card?

    @Query(
        "SELECT * FROM cards WHERE bookId = :bookId AND path = :path " +
            "AND title = :title LIMIT 1"
    )
    suspend fun findByPathAndTitle(bookId: Long, path: String, title: String): Card?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM cards WHERE bookId = :bookId")
    suspend fun maxSortOrder(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM cards WHERE bookId = :bookId")
    suspend fun count(bookId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Category ordering ----

    @Query("SELECT * FROM category_order WHERE bookId = :bookId")
    fun observeCategoryOrders(bookId: Long): Flow<List<CategoryOrder>>

    @Query("SELECT * FROM category_order WHERE bookId = :bookId")
    suspend fun getAllCategoryOrders(bookId: Long): List<CategoryOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryOrder(order: CategoryOrder)

    @Query("DELETE FROM category_order WHERE bookId = :bookId AND path = :path")
    suspend fun deleteCategoryOrder(bookId: Long, path: String)

    // ---- Batch path operations for category management ----

    @Query("UPDATE cards SET path = :newPath WHERE bookId = :bookId AND path = :oldPath")
    suspend fun renameExactPath(bookId: Long, oldPath: String, newPath: String)

    @Query(
        "UPDATE cards SET path = :newPrefix || SUBSTR(path, :oldPrefixLength + 1) " +
            "WHERE bookId = :bookId AND (path = :oldPrefix OR path LIKE :oldPrefix || '/%')"
    )
    suspend fun renamePathPrefix(
        bookId: Long,
        oldPrefix: String,
        newPrefix: String,
        oldPrefixLength: Int
    )

    @Query(
        "UPDATE cards SET path = '' WHERE bookId = :bookId " +
            "AND (path = :path OR path LIKE :path || '/%')"
    )
    suspend fun clearPath(bookId: Long, path: String)

    @Query(
        "DELETE FROM cards WHERE bookId = :bookId " +
            "AND (path = :path OR path LIKE :path || '/%')"
    )
    suspend fun deleteByPath(bookId: Long, path: String)
}
