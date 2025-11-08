package com.exp.hentai1.ui.parseTags

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exp.hentai1.data.TagInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// 定义排序类型枚举
enum class SortType {
    NAME,
    COUNT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ParseTagsScreen(
    onTagClick: (String) -> Unit,
    entityType: String
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ParseTagsViewModel = viewModel(
        factory = ParseTagsViewModelFactory(application, entityType)
    )

    val uiState by viewModel.uiState.collectAsState()

    // 多选状态
    var inSelectionMode by remember { mutableStateOf(false) }
    val selectedTagKeys = remember { mutableStateSetOf<Pair<String, String>>() }

    // 退出多选模式的通用方法
    fun exitSelectionMode() {
        inSelectionMode = false
        selectedTagKeys.clear()
    }

    // 在多选模式下，处理返回键事件
    BackHandler(enabled = inSelectionMode) {
        exitSelectionMode()
    }

    // 为每种排序类型记住一个独立的 LazyListState
    val nameListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (uiState.scrollPositions[SortType.NAME] ?: ScrollPosition()).index,
        initialFirstVisibleItemScrollOffset = (uiState.scrollPositions[SortType.NAME] ?: ScrollPosition()).offset
    )
    val countListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (uiState.scrollPositions[SortType.COUNT] ?: ScrollPosition()).index,
        initialFirstVisibleItemScrollOffset = (uiState.scrollPositions[SortType.COUNT] ?: ScrollPosition()).offset
    )

    // 根据当前的 sortType 选择活跃的 listState
    val currentListState = when (uiState.sortType) {
        SortType.NAME -> nameListState
        SortType.COUNT -> countListState
    }

    // 监听当前活跃的 listState 的滚动状态，并更新 ViewModel 中的滚动位置
    LaunchedEffect(currentListState, uiState.sortType) {
        snapshotFlow {
            currentListState.firstVisibleItemIndex to currentListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.updateScrollPosition(uiState.sortType, index, offset)
            }
    }

    // 监听当前活跃的 listState 的滚动，触发加载更多
    LaunchedEffect(currentListState, uiState.sortType) {
        snapshotFlow { currentListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= currentListState.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged()
            .collect {
                viewModel.loadMore()
            }
    }

    val title = when (entityType) {
        "tags" -> "标签"
        "parodies" -> "原作"
        "characters" -> "角色"
        "artists" -> "作者"
        "groups" -> "团队"
        else -> "列表"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (inSelectionMode) "已选择 ${selectedTagKeys.size} 项" else "$title" + "列表") },
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出多选模式")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (inSelectionMode) {
                val selectedAndBlocked = selectedTagKeys.all { uiState.blockedTagKeys.contains(it) }
                val selectedAndFavorited = selectedTagKeys.all { uiState.favoritedTagKeys.contains(it) }

                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (selectedTagKeys.isNotEmpty() && selectedAndBlocked) {
                            Button(
                                onClick = {
                                    viewModel.unblockTags(selectedTagKeys.toSet())
                                    exitSelectionMode()
                                }
                            ) {
                                Text("移除拉黑")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.blockTags(selectedTagKeys.toSet())
                                    exitSelectionMode()
                                },
                                enabled = selectedTagKeys.isNotEmpty()
                            ) {
                                Text("拉黑")
                            }
                        }

                        if (selectedTagKeys.isNotEmpty() && selectedAndFavorited) {
                            Button(
                                onClick = {
                                    viewModel.unfavoriteTags(selectedTagKeys.toSet())
                                    exitSelectionMode()
                                }
                            ) {
                                Text("移除收藏")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.favoriteTags(selectedTagKeys.toSet())
                                    exitSelectionMode()
                                },
                                enabled = selectedTagKeys.isNotEmpty()
                            ) {
                                Text("收藏")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 添加排序按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.setSortType(SortType.NAME) },
                    enabled = uiState.sortType != SortType.NAME && !inSelectionMode
                ) {
                    Text("名称排序")
                }
                Button(
                    onClick = { viewModel.setSortType(SortType.COUNT) },
                    enabled = uiState.sortType != SortType.COUNT && !inSelectionMode
                ) {
                    Text("作品数量")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    uiState.error != null -> {
                        Text(
                            text = "发生错误: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    uiState.currentTags.isEmpty() -> {
                        Text(
                            text = "主人，没有找到任何${title}呢~",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = currentListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.currentTags, key = { "${it.id}-${it.category}" }) { tag ->
                                val tagKey = tag.id to tag.category
                                val isSelected = selectedTagKeys.contains(tagKey)
                                val isBlocked = uiState.blockedTagKeys.contains(tagKey)
                                val isFavorited = uiState.favoritedTagKeys.contains(tagKey)
                                TagItem(
                                    tag = tag,
                                    isSelected = isSelected,
                                    isBlocked = isBlocked,
                                    isFavorited = isFavorited,
                                    onItemClick = {
                                        if (inSelectionMode) {
                                            if (isSelected) {
                                                selectedTagKeys.remove(tagKey)
                                                if (selectedTagKeys.isEmpty()) {
                                                    inSelectionMode = false
                                                }
                                            } else {
                                                selectedTagKeys.add(tagKey)
                                            }
                                        } else {
                                            val searchEntityType = "tag"
                                            val query = "$searchEntityType:${tag.category}:${tag.id}:${tag.name}"
                                            onTagClick(query)
                                        }
                                    },
                                    onItemLongClick = {
                                        if (!inSelectionMode) {
                                            inSelectionMode = true
                                            selectedTagKeys.add(tagKey)
                                        }
                                    }
                                )
                            }

                            item {
                                if (uiState.isLoadingMore) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Text(text = "正在加载更多，请稍候...")
                                    }
                                } else if (!uiState.canLoadMore) {
                                    Text(
                                        text = "没有更多了，主人~",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagItem(
    tag: TagInfo,
    isSelected: Boolean,
    isBlocked: Boolean,
    isFavorited: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tag.name, style = MaterialTheme.typography.bodyLarge)
                if (tag.englishName.isNotBlank() && tag.englishName != tag.name) {
                    Text(
                        text = tag.englishName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = "Category: ${tag.category}", style = MaterialTheme.typography.bodySmall)
            }
            if (isBlocked) {
                Text(
                    text = "已拉黑",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else if (isFavorited) {
                Text(
                    text = "已收藏",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
