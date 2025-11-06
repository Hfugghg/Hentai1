package com.exp.hentai1.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_folders")
data class FavoriteFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
