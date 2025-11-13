package com.exp.hentai1.ui.tagcollection

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exp.hentai1.data.UserTag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagCollectionScreen(
    onNavigateToTagSearch: (String) -> Unit
) {
    val viewModel: TagCollectionViewModel = viewModel(
        factory = TagCollectionViewModelFactory(LocalContext.current.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    var showTodoDialog by remember { mutableStateOf(false) }
    var selectedTagForDialog by remember { mutableStateOf<UserTag?>(null) }

    if (showTodoDialog && selectedTagForDialog != null) {
        // 传递 ViewModel 中的操作函数
        TagOperationDialog(
            tag = selectedTagForDialog!!,
            onDeleteTag = {
                viewModel.deleteTag(it)
                showTodoDialog = false
                selectedTagForDialog = null
            },
            onSaveAsFavorite = {
                viewModel.saveAsFavorite(it)
                showTodoDialog = false
                selectedTagForDialog = null
            },
            onSaveAsBlock = {
                viewModel.saveAsBlock(it)
                showTodoDialog = false
                selectedTagForDialog = null
            },
            onDismiss = {
                showTodoDialog = false
                selectedTagForDialog = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("我的标签收藏") })
        }
    ) { paddingValues ->

        AnimatedVisibility(
            visible = uiState.isDataLoaded,
            modifier = Modifier.padding(paddingValues),
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(durationMillis = 500)
            ) + fadeIn(animationSpec = tween(durationMillis = 500)),
            exit = slideOutVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val onLongClickLambda = { tag: UserTag ->
                    selectedTagForDialog = tag
                    showTodoDialog = true
                }

                tagCategorySection(
                    title = "作者",
                    tags = uiState.artists,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "社团",
                    tags = uiState.groups,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "原作",
                    tags = uiState.parodies,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "角色",
                    tags = uiState.characters,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "标签",
                    tags = uiState.tags,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "语言",
                    tags = uiState.languages,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
                tagCategorySection(
                    title = "分类",
                    tags = uiState.categories,
                    onTagClick = { onNavigateToTagSearch("tag:${it.category}:${it.id}:${it.name}") },
                    onTagLongClick = onLongClickLambda
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.tagCategorySection(
    title: String,
    tags: List<UserTag>,
    onTagClick: (UserTag) -> Unit,
    onTagLongClick: (UserTag) -> Unit
) {
    if (tags.isNotEmpty()) {
        stickyHeader {
            Text(
                text = "$title (${tags.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        items(tags, key = { it.id }) { tag ->
            TagButton(
                tag = tag,
                onClick = { onTagClick(tag) },
                onLongClick = { onTagLongClick(tag) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagButton(
    tag: UserTag,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (tag.type) {
                1 -> Icons.Outlined.Favorite
                0 -> Icons.Outlined.Block
                else -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
            }
            val iconColor = when (tag.type) {
                1 -> MaterialTheme.colorScheme.primary
                0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Icon(
                imageVector = icon,
                contentDescription = if (tag.type == 1) "已收藏" else "已拉黑",
                tint = iconColor
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 标签操作弹窗，取代 TodoTagDialog
 */
@Composable
private fun TagOperationDialog(
    tag: UserTag,
    onDeleteTag: (UserTag) -> Unit,
    onSaveAsFavorite: (UserTag) -> Unit,
    onSaveAsBlock: (UserTag) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签操作：${tag.name}") },
        text = {
            Column {
                Text("你长按了标签：${tag.name}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        // 操作按钮
        dismissButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 删除/取消操作 (收藏/拉黑标签都可删除，即从数据库移除)
                TextButton(onClick = { onDeleteTag(tag) }) {
                    Text(
                        text = when (tag.type) {
                            1 -> "取消收藏" // type=1 是收藏
                            0 -> "取消拉黑" // type=0 是拉黑
                            else -> "删除标签"
                        },
                        color = MaterialTheme.colorScheme.error // 红色警告
                    )
                }

                // 切换状态操作 (已收藏的标签不显示收藏选项，已拉黑的标签不显示拉黑选项)
                if (tag.type == 0) { // 当前为拉黑，可切换为收藏
                    TextButton(onClick = { onSaveAsFavorite(tag) }) {
                        Text("切换为收藏")
                    }
                } else if (tag.type == 1) { // 当前为收藏，可切换为拉黑
                    TextButton(onClick = { onSaveAsBlock(tag) }) {
                        Text("切换为拉黑")
                    }
                }
            }
        }
    )
}

class TagCollectionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TagCollectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TagCollectionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}