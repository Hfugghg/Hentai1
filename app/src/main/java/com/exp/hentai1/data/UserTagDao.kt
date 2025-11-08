package com.exp.hentai1.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userTag: UserTag)

    @Query("DELETE FROM user_tags WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM user_tags WHERE id = :id AND category = :category AND type = :type")
    suspend fun deleteByIdCategoryAndType(id: String, category: String, type: Int)

    @Query("SELECT * FROM user_tags WHERE type = :type ORDER BY timestamp DESC")
    fun getTagsByType(type: Int): Flow<List<UserTag>>

    @Query("SELECT * FROM user_tags")
    fun getAllTags(): Flow<List<UserTag>>
}
