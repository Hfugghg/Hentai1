package com.exp.hentai1.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFolderDao {
    @Insert
    suspend fun insert(favoriteFolder: FavoriteFolder): Long

    @Query("SELECT * FROM favorite_folders")
    fun getAllFavoriteFolders(): Flow<List<FavoriteFolder>>

    @Query("SELECT COUNT(*) FROM favorites WHERE folderId = :folderId")
    fun getFavoriteCountInFolder(folderId: Long): Flow<Int>

    @Delete
    suspend fun deleteFolder(folder: FavoriteFolder)
}
