package com.exp.hentai1.data

import androidx.compose.runtime.Immutable

// 【新】定义 Tag 数据类，用于存储名称和 ID
@Immutable
data class Tag(
    val id: String,
    val name: String
)

@Immutable
data class Comic(
    val id: String,
    val title: String,
    val coverUrl: String,
    val language: String,
    val tags: List<Tag> = emptyList(),
    val author: String? = null,
    val circle: String? = null,
    val parody: String? = null,
    val category: String? = null,
    val character: String? = null,
    val imageList: List<String> = emptyList()
)