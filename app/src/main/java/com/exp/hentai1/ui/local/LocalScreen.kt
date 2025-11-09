package com.exp.hentai1.ui.local

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.exp.hentai1.data.Comic
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle
import com.exp.hentai1.worker.DownloadWorker

sealed interface DownloadState {
    data class Downloading(val downloadedPages: Int, val totalPages: Int) : DownloadState
    data class Completed(val coverPath: String) : DownloadState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalScreen(
    onNavigateToDetail: (String) -> Unit
) {
    val viewModel: LocalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    val workInfos by WorkManager.getInstance(viewModel.getApplication())
        .getWorkInfosByTagLiveData(DownloadWorker::class.java.name)
        .observeAsState()

    viewModel.observeWorkManager(workInfos)

    // 弹窗状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var comicIdToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地收藏") },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.comics, key = {
                    when (it) {
                        is LocalComicState.Completed -> it.comic.comicId
                        is LocalComicState.Downloading -> it.comic.comicId
                        is LocalComicState.Incomplete -> it.comic.comicId
                    }
                }) { localComicState ->
                    // 复杂的逻辑已移至新的可组合函数
                    LocalComicCard(
                        localComicState = localComicState,
                        onComicClick = onNavigateToDetail,
                        onPauseClick = { comicId -> viewModel.pauseDownload(comicId) },
                        onResumeClick = { comicId -> viewModel.resumeDownload(comicId) },
                        onLongClick = { comicId -> // 修改长按行为，显示弹窗
                            comicIdToDelete = comicId
                            showDeleteDialog = true
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                comicIdToDelete = null
            },
            title = { Text("确认删除") },
            text = { Text("您确定要删除此漫画及其所有本地数据吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        comicIdToDelete?.let {
                            viewModel.deleteComic(it)
                        }
                        showDeleteDialog = false
                        comicIdToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        comicIdToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 一个私有的包装可组合函数，用于处理 LocalComicState
 * 并将其转换为通用的 ComicCard。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalComicCard(
    localComicState: LocalComicState,
    onComicClick: (String) -> Unit,
    // 新增：暂停和继续的回调
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onLongClick: (String) -> Unit, // 新增长按回调
    modifier: Modifier = Modifier
) {
    val download = when (localComicState) {
        is LocalComicState.Completed -> localComicState.comic
        is LocalComicState.Downloading -> localComicState.comic
        is LocalComicState.Incomplete -> localComicState.comic
    }

    // 只填充 ComicCard 在 GRID 样式下所需的字段
    val comicForCard = Comic(
        id = download.comicId,
        title = download.title,
        coverUrl = download.coverUrl,
        imageList = emptyList(),
        artists = emptyList(),
        groups = emptyList(),
        parodies = emptyList(),
        characters = emptyList(),
        tags = emptyList(),
        languages = emptyList(),
        categories = emptyList()
    )

    val downloadStateForCard = when (localComicState) {
        is LocalComicState.Downloading -> DownloadState.Downloading(
            downloadedPages = localComicState.downloadedPages,
            totalPages = localComicState.totalPages
        )
        is LocalComicState.Completed -> DownloadState.Completed(
            coverPath = localComicState.coverPath
        )
        is LocalComicState.Incomplete -> DownloadState.Downloading(
            downloadedPages = localComicState.downloadedPages,
            totalPages = download.totalPages
        )
    }

    ComicCard(
        comic = comicForCard,
        style = ComicCardStyle.LIST,
        onComicClick = null,
        downloadState = downloadStateForCard,
        modifier = modifier.combinedClickable(
            onClick = { onComicClick(comicForCard.id) },
            onLongClick = { onLongClick(comicForCard.id) }
        ),
        // 新增：根据下载状态提供 IconButton
        actions = {
            when (localComicState) {
                is LocalComicState.Downloading -> {
                    // 处于下载中，显示“暂停”按钮
                    IconButton(onClick = { onPauseClick(comicForCard.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "暂停"
                        )
                    }
                }
                is LocalComicState.Incomplete -> {
                    // 处于未完成（已暂停），显示“继续”按钮
                    IconButton(onClick = { onResumeClick(comicForCard.id) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "继续"
                        )
                    }
                }
                is LocalComicState.Completed -> {
                    // 已完成，不显示任何按钮
                }
            }
        }
    )
}