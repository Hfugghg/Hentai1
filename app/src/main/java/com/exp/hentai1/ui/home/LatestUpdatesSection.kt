package com.exp.hentai1.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle

/**
 * 每日更新列表的扩展函数，用于在 LazyColumn 中构建分页内容。
 *
 * 注意：由于原始文件没有提供 LoadedPage 的定义，这里提供一个简单的占位定义，
 * 以确保 LazyListScope.latestUpdatesSection 函数能编译通过。
 * 在实际项目中，需要确保其在 data/ 或 ui/home/ 定义。
 */
fun LazyListScope.latestUpdatesSection(
    loadedPages: List<LoadedPage>,
    onComicClick: (String) -> Unit
) {
    if (loadedPages.isEmpty()) return

    // 遍历每一个已加载的页面
    loadedPages.forEach { loadedPage ->
        // 1. 插入分页条
        item(key = "page-separator-${loadedPage.page}") {
            Text(
                text = "--- 第 ${loadedPage.page} 页 --- ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // 2. 插入当前页的漫画列表
        items(
            items = loadedPage.comics.distinctBy { it.id },
            key = { "latest-${it.id}" }
        ) { comic ->
            ComicCard(
                comic = comic,
                style = ComicCardStyle.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onComicClick = onComicClick
            )
        }
    }
}