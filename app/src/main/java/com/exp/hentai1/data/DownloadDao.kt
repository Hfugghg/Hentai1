package com.exp.hentai1.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download)

    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE comicId = :comicId")
    suspend fun getDownloadById(comicId: String): Download?

    @Query("DELETE FROM downloads WHERE comicId = :comicId")
    suspend fun deleteById(comicId: String)
}