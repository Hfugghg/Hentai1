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

    // 保留现有方法，但注意：ViewModel中实现删除逻辑将依赖于通用的deleteTag
    @Query("DELETE FROM user_tags WHERE id = :id")
    suspend fun delete(id: String)

    // 保留现有方法
    @Query("DELETE FROM user_tags WHERE id = :id AND category = :category AND type = :type")
    suspend fun deleteByIdCategoryAndType(id: String, category: String, type: Int)

    // 新增：根据ID和Category删除标签 (因为一个标签ID在不同category下可能是不同的实体，但通常一个ID+Category应该是唯一的)
    // 实际上，如果id是唯一的key，只需要id即可。如果id在不同category下可能重复，需要两者。
    // 考虑到UserTag的插入是REPLACE，我们假设ID+Category是唯一的组合。
    // 更安全的做法是：直接根据ID删除，如果ID是唯一的PK。但这里沿用现有的查询结构，假设ID是唯一的。
    @Query("DELETE FROM user_tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("SELECT * FROM user_tags WHERE id = :id")
    suspend fun getTagById(id: String): UserTag?

    @Query("SELECT * FROM user_tags WHERE type = :type ORDER BY timestamp DESC")
    fun getTagsByType(type: Int): Flow<List<UserTag>>

    @Query("SELECT * FROM user_tags")
    fun getAllTags(): Flow<List<UserTag>>
}