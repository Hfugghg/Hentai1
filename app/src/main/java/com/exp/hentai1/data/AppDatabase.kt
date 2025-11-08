package com.exp.hentai1.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Favorite::class, FavoriteFolder::class, UserTag::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteFolderDao(): FavoriteFolderDao
    abstract fun userTagDao(): UserTagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建收藏文件夹表
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorite_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")

                // 2. 创建临时表来存储现有数据
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites_temp` (`comicId` TEXT PRIMARY KEY NOT NULL, `title` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `folderId` INTEGER)")

                // 3. 将数据从旧表复制到临时表
                database.execSQL("INSERT INTO `favorites_temp` (`comicId`, `title`, `timestamp`, `folderId`) SELECT `comicId`, `title`, `timestamp`, NULL FROM `favorites`")

                // 4. 删除旧表
                database.execSQL("DROP TABLE `favorites`")

                // 5. 重新创建带有外键和索引的新表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `comicId` TEXT PRIMARY KEY NOT NULL,
                        `title` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `folderId` INTEGER,
                        FOREIGN KEY(`folderId`) REFERENCES `favorite_folders`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 6. 创建索引
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_folderId` ON `favorites` (`folderId`)")

                // 7. 将数据从临时表复制回新表
                database.execSQL("INSERT INTO `favorites` (`comicId`, `title`, `timestamp`, `folderId`) SELECT `comicId`, `title`, `timestamp`, `folderId` FROM `favorites_temp`")

                // 8. 删除临时表
                database.execSQL("DROP TABLE `favorites_temp`")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'MAIN'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS `blocked_tags` (
                `id` TEXT NOT NULL, 
                `name` TEXT NOT NULL, 
                `englishName` TEXT NOT NULL, 
                `category` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create the new table with the desired schema
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_tags` (
                `id` TEXT NOT NULL, 
                `name` TEXT NOT NULL, 
                `englishName` TEXT NOT NULL, 
                `category` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL, 
                `type` INTEGER NOT NULL DEFAULT 0, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

                // 2. Copy the data from the old table to the new table
                database.execSQL("""
            INSERT INTO `user_tags` (`id`, `name`, `englishName`, `category`, `timestamp`)
            SELECT `id`, `name`, `englishName`, `category`, `timestamp` FROM `blocked_tags`
        """.trimIndent())

                // 3. Drop the old table
                database.execSQL("DROP TABLE `blocked_tags`")
            }
        }
    }
}
