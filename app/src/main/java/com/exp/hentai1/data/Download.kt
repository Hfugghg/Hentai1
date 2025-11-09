package com.exp.hentai1.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "downloads")
@TypeConverters(TagListConverter::class, StringListConverter::class, DownloadStatusConverter::class)
data class Download(
    @PrimaryKey val comicId: String,
    val title: String,
    val coverUrl: String,
    val totalPages: Int,
    val timestamp: Long,
    val imageList: List<String>,
    val status: DownloadStatus, // 添加此字段

    // 存储所有相关的元数据，如建议
    val artists: List<Tag>,
    val groups: List<Tag>,
    val parodies: List<Tag>,
    val characters: List<Tag>,
    val tags: List<Tag>,
    val languages: List<Tag>,
    val categories: List<Tag>
)
