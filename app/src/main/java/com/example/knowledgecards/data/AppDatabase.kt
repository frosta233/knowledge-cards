package com.example.knowledgecards.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Card::class, CategoryOrder::class, Book::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: new category_order table for manual card-set ordering. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_order` (" +
                        "`path` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`path`))"
                )
            }
        }

        /**
         * v2 -> v3: the bookshelf. Cards gain a `bookId`, uniqueness becomes
         * (bookId, path, title) so two books may hold the same card, and the
         * category order table is rebuilt with a per-book primary key.
         *
         * Cards that existed before the bookshelf move into a single book
         * called "我的卡片", so nothing is lost on upgrade.
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `bookId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_cards_path_title`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_bookId_path_title` " +
                        "ON `cards` (`bookId`, `path`, `title`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_order_new` (" +
                        "`bookId` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`bookId`, `path`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `category_order_new` (`bookId`, `path`, `position`) " +
                        "SELECT 0, `path`, `position` FROM `category_order`"
                )
                db.execSQL("DROP TABLE `category_order`")
                db.execSQL("ALTER TABLE `category_order_new` RENAME TO `category_order`")

                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `books` (`name`, `createdAt`, `sortOrder`) " +
                        "SELECT '我的卡片', $now, 0 WHERE EXISTS (SELECT 1 FROM `cards`)"
                )
                db.execSQL(
                    "UPDATE `cards` SET `bookId` = " +
                        "(SELECT `id` FROM `books` ORDER BY `id` ASC LIMIT 1) WHERE `bookId` = 0"
                )
                db.execSQL(
                    "UPDATE `category_order` SET `bookId` = " +
                        "(SELECT `id` FROM `books` ORDER BY `id` ASC LIMIT 1) WHERE `bookId` = 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "knowledge_cards.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
