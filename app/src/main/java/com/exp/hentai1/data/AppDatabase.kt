package com.exp.hentai1.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Favorite::class, FavoriteFolder::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteFolderDao(): FavoriteFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
    }
}
