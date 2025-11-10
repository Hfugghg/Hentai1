package com.exp.hentai1.ui.local

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.exp.hentai1.data.Comic
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle
import com.exp.hentai1.util.EventObserver
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
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val workInfos by WorkManager.getInstance(viewModel.getApplication())
        .getWorkInfosByTagLiveData(DownloadWorker::class.java.name)
        .observeAsState()

    viewModel.observeWorkManager(workInfos)

    // 弹窗状态
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedComicId by remember { mutableStateOf<String?>(null) }

    // 观察打开目录的事件
    LaunchedEffect(Unit) {
        viewModel.openDirectoryEvent.observe(lifecycleOwner, EventObserver { intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "没有找到可以打开此目录的应用", Toast.LENGTH_SHORT).show()
            }
        })
    }


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
                        onLongClick = { comicId -> // 修改长按行为，显示选项弹窗
                            selectedComicId = comicId
                            showOptionsDialog = true
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // 选项弹窗
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = {
                showOptionsDialog = false
                selectedComicId = null
            },
            title = { Text("操作选项") },
            text = {
                Column {
                    TextButton(onClick = {
                        showOptionsDialog = false
                        showDeleteDialog = true // 显示删除确认弹窗
                    }) {
                        Text("删除漫画")
                    }
                    TextButton(onClick = {
                        selectedComicId?.let { viewModel.openComicDirectory(it) }
                        showOptionsDialog = false
                        selectedComicId = null
                    }) {
                        Text("打开本地路径")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOptionsDialog = false
                        selectedComicId = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }


    // 删除确认弹窗
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedComicId = null
            },
            title = { Text("确认删除") },
            text = { Text("您确定要删除此漫画及其所有本地数据吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedComicId?.let {
                            viewModel.deleteComic(it)
                        }
                        showDeleteDialog = false
                        selectedComicId = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedComicId = null
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