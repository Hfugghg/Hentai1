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

    // --- 【修改】根据主人要求，调整字段顺序并添加 languages --- 
    val artists: List<Tag> = emptyList(),
    val groups: List<Tag> = emptyList(),
    val parodies: List<Tag> = emptyList(),
    val characters: List<Tag> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val languages: List<Tag> = emptyList(),
    val categories: List<Tag> = emptyList(),
    // --- 【修改结束】---

    val imageList: List<String> = emptyList(),
    val timestamp: Long = 0,

    // 【新增】添加这两个字段
    val isError: Boolean = false,
    val errorInfo: String? = null,
    val language: String = "MAIN"
)

/**
 * 定义一个数据类来存储解析后的标签信息
 */
data class TagInfo(
    val name: String,       // 类型名
    val englishName: String,
    val category: String,   // 类型种类
    val id: String          // 类型ID
)
