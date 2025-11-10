package com.exp.hentai1.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.ui.local.DownloadState
import java.io.File

enum class ComicCardStyle {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicCard(
    comic: Comic,
    style: ComicCardStyle,
    modifier: Modifier = Modifier,
    onComicClick: ((String) -> Unit)?,
    subtitle: String? = null,
    downloadState: DownloadState? = null, // 已更新为使用新的密封接口
    // 新增：一个“动作”插槽，用于在卡片末尾添加按钮等
    actions: @Composable RowScope.() -> Unit = {}
) {
    Card(
        modifier = modifier.then(
            if (onComicClick != null) {
                Modifier.singleClickable { onComicClick(comic.id) }
            } else {
                Modifier // 如果 onComicClick 为 null，则不添加任何点击事件
            }
        ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        when (style) {
            ComicCardStyle.GRID -> GridComicCardContent(comic, downloadState)
            // 将 actions 传递给 List 样式
            ComicCardStyle.LIST -> ListComicCardContent(comic, subtitle, downloadState, actions)
        }
    }
}

@Composable
private fun GridComicCardContent(comic: Comic, downloadState: DownloadState?) {
    // ... (此函数内容不变) ...
    val imageModel = if (downloadState is DownloadState.Completed) {
        File(downloadState.coverPath)
    } else {
        comic.coverUrl
    }

    Column {
        Box {
            AsyncImage(
                model = imageModel,
                contentDescription = comic.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            // 如果正在下载，显示进度覆盖层
            if (downloadState is DownloadState.Downloading) {
                val progress = downloadState.downloadedPages.toFloat() / downloadState.totalPages
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Text(
                    text = "${downloadState.downloadedPages} / ${downloadState.totalPages}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
                LinearProgressIndicator(
                    progress = { progress }, // progress 现在是 lambda 的返回值
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = comic.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListComicCardContent(
    comic: Comic,
    subtitle: String? = null,
    downloadState: DownloadState?,
    // 新增 actions 参数
    actions: @Composable RowScope.() -> Unit
) {
    // 确定图片模型：如果已完成则为本地路径，否则为远程URL
    val imageModel = if (downloadState is DownloadState.Completed) {
        File(downloadState.coverPath)
    } else {
        comic.coverUrl
    }

    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = comic.title,
            modifier = Modifier
                .width(100.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = comic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(8.dp))

            // 根据下载状态显示进度信息
            when (downloadState) {
                is DownloadState.Downloading -> {
                    val progress = downloadState.downloadedPages.toFloat() / downloadState.totalPages
                    LinearProgressIndicator(
                        progress = { progress }, // <-- 修改在这里
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "下载中... ${downloadState.downloadedPages} / ${downloadState.totalPages}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadState.Completed -> {
                    Text(text = "已完成", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    // 否则，显示原始副标题
                    val textToShow = subtitle ?: if (comic.languages.isNotEmpty()) {
                        "语言: " + comic.languages.joinToString { it.name }
                    } else {
                        "语言: 未知"
                    }
                    Text(text = textToShow, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 新增：在 Row 的末尾调用 actions()，显示按钮
        actions()
    }
}