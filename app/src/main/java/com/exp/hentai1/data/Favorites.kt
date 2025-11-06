package com.exp.hentai1.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(

    tableName = "favorites",
    indices = [Index("folderId")],
    foreignKeys = [ForeignKey(
        entity = FavoriteFolder::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Favorite(
    @PrimaryKey val comicId: String,
    val title: String,
    val timestamp: Long,
    val folderId: Long?
)
