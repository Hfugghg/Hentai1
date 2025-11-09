package com.exp.hentai1.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Favorite::class, FavoriteFolder::class, UserTag::class, Download::class], version = 8) // 版本号增加到8
@TypeConverters(TagListConverter::class, StringListConverter::class, DownloadStatusConverter::class) // 添加 DownloadStatusConverter
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteFolderDao(): FavoriteFolderDao
    abstract fun userTagDao(): UserTagDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build() // 添加新的迁移
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorite_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites_temp` (`comicId` TEXT PRIMARY KEY NOT NULL, `title` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `folderId` INTEGER)")
                database.execSQL("INSERT INTO `favorites_temp` (`comicId`, `title`, `timestamp`, `folderId`) SELECT `comicId`, `title`, `timestamp`, NULL FROM `favorites`")
                database.execSQL("DROP TABLE `favorites`")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `comicId` TEXT PRIMARY KEY NOT NULL,
                        `title` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `folderId` INTEGER,
                        FOREIGN KEY(`folderId`) REFERENCES `favorite_folders`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_folderId` ON `favorites` (`folderId`)")
                database.execSQL("INSERT INTO `favorites` (`comicId`, `title`, `timestamp`, `folderId`) SELECT `comicId`, `title`, `timestamp`, `folderId` FROM `favorites_temp`")
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
                database.execSQL("""
            INSERT INTO `user_tags` (`id`, `name`, `englishName`, `category`, `timestamp`)
            SELECT `id`, `name`, `englishName`, `category`, `timestamp` FROM `blocked_tags`
        """.trimIndent())
                database.execSQL("DROP TABLE `blocked_tags`")
            }
        }
        
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `comicId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `coverUrl` TEXT NOT NULL, 
                        `totalPages` INTEGER NOT NULL, 
                        `timestamp` INTEGER NOT NULL,
                        `artists` TEXT NOT NULL,
                        `groups` TEXT NOT NULL,
                        `parodies` TEXT NOT NULL,
                        `characters` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `languages` TEXT NOT NULL,
                        `categories` TEXT NOT NULL,
                        PRIMARY KEY(`comicId`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `downloads` ADD COLUMN `imageList` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // 新增 MIGRATION_7_8
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 为 'downloads' 表添加 'status' 字段，并为现有行设置默认值
                database.execSQL("ALTER TABLE `downloads` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'")
            }
        }
    }
}