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
 * (这里是原文件的注释...)
 */
fun LazyListScope.latestUpdatesSection(
    loadedPages: List<LoadedPage>,
    onComicClick: (String) -> Unit
) {
    if (loadedPages.isEmpty()) return

    // --- 新增：用于跟踪所有已显示的漫画ID，防止跨页重复 ---
    val displayedComicIds = mutableSetOf<String>()

    // 遍历每一个已加载的页面
    loadedPages.forEach { loadedPage ->

        // --- 修改：在渲染漫画之前，先过滤掉所有已显示过的漫画 ---
        val uniqueComicsForThisPage = loadedPage.comics.filter { comic ->
            // Set.add() 方法：如果元素不存在，则添加并返回 true；如果已存在，则返回 false。
            // 我们利用这个特性来过滤。
            displayedComicIds.add(comic.id)
        }

        // --- 修改：只有当过滤后本页“真正”有新漫画时，才显示分页符和列表 ---
        if (uniqueComicsForThisPage.isNotEmpty()) {

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
                // --- 修改：使用过滤后的 uniqueComicsForThisPage 列表 ---
                items = uniqueComicsForThisPage,
                // 现在的 key 保证是全局唯一的，因为列表项本身是唯一的
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
}