package com.exp.hentai1.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_tags")
data class UserTag(
    @PrimaryKey
    val id: String,
    val name: String,
    val englishName: String,
    val category: String,
    val timestamp: Long,
    val type: Int // 0 for blocked, 1 for favorite
)
