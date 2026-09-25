package com.example.knowledgecards.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY sortOrder ASC, id ASC")
    fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY sortOrder ASC, id ASC")
    suspend fun getBooks(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Book?

    @Query("SELECT * FROM books WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Book?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM books")
    suspend fun maxSortOrder(): Int

    /**
     * Card / category counts per book in one pass. [BookStats.categoryCount]
     * counts real category paths only — the "未分类" bucket (empty path) is a
     * display pseudo-category, not something the user created.
     */
    @Query(
        "SELECT bookId AS bookId, COUNT(*) AS cardCount, " +
            "COUNT(DISTINCT CASE WHEN path != '' THEN path END) AS categoryCount " +
            "FROM cards GROUP BY bookId"
    )
    fun observeBookStats(): Flow<List<BookStats>>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cards WHERE bookId = :bookId")
    suspend fun deleteCardsOf(bookId: Long)

    @Query("DELETE FROM category_order WHERE bookId = :bookId")
    suspend fun deleteCategoryOrdersOf(bookId: Long)

    /** Removes a book with everything inside it, atomically. */
    @Transaction
    suspend fun deleteBookCascade(bookId: Long) {
        deleteCardsOf(bookId)
        deleteCategoryOrdersOf(bookId)
        deleteById(bookId)
    }
}
