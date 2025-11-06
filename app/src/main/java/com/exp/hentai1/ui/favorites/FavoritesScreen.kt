package com.exp.hentai1.ui.favorites

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.ui.common.singleClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavoritesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FavoritesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(onComicClick: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModelFactory(application))
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("收藏") },
                actions = {
                    TextButton(onClick = { viewModel.toggleManagementMode() }) {
                        Text(if (uiState.isManagementMode) "完成" else "管理")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isManagementMode) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { viewModel.deleteSelected() }) {
                            Text("删除选中")
                        }
                        Button(onClick = { viewModel.exportSelected() }) {
                            Text("导出选中 (TODO)")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "发生错误: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        uiState.favoriteGroups.forEach { group ->
                            stickyHeader(key = group.folder?.id ?: "uncategorized") {
                                FavoriteGroupHeader(uiState, group, viewModel)
                            }

                            // 只有当收藏夹展开时，才显示其中的漫画
                            if (group.isExpanded) {
                                items(group.comics, key = { it.id }) { comic ->
                                    FavoriteItem(
                                        comic = comic,
                                        isManagementMode = uiState.isManagementMode,
                                        isSelected = uiState.selectedComicIds.contains(comic.id),
                                        onComicClick = {
                                            if (uiState.isManagementMode) {
                                                viewModel.toggleComicSelection(comic.id)
                                            } else {
                                                onComicClick(comic.id)
                                            }
                                        },
                                        onToggleSelection = { viewModel.toggleComicSelection(comic.id) }
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

@Composable
private fun FavoriteGroupHeader(uiState: FavoritesUiState, group: FavoriteGroup, viewModel: FavoritesViewModel) {
    val folderId = group.folder?.id
    val comicIdsInGroup = group.comics.map { it.id }.toSet()
    val isAllSelected = uiState.selectedComicIds.containsAll(comicIdsInGroup) && comicIdsInGroup.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { viewModel.toggleFolderExpansion(folderId) } // 恢复点击功能
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.isManagementMode) {
            Checkbox(
                checked = isAllSelected,
                onCheckedChange = { viewModel.toggleSelectAllInFolder(folderId) }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        val folderName = group.folder?.name ?: "未分类"
        Text(
            text = "$folderName (${group.comics.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FavoriteItem(
    comic: Comic,
    isManagementMode: Boolean,
    isSelected: Boolean,
    onComicClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onComicClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = comic.coverUrl,
            contentDescription = comic.title,
            modifier = Modifier.size(width = 80.dp, height = 120.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = comic.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "收藏于: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(comic.timestamp))}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (isManagementMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        }
    }
}
