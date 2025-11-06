package com.exp.hentai1.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    suspend fun getAll(): List<Favorite>

    @Query("DELETE FROM favorites WHERE comicId = :comicId")
    suspend fun delete(comicId: String)

    @Query("SELECT * FROM favorites WHERE comicId = :comicId")
    suspend fun getById(comicId: String): Favorite?

    @Query("DELETE FROM favorites WHERE comicId IN (:comicIds)")
    suspend fun deleteByIds(comicIds: List<String>)
}
