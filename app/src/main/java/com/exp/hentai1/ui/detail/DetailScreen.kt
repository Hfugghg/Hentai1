package com.exp.hentai1.ui.detail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.FavoriteFolder
import com.exp.hentai1.data.Tag
import com.exp.hentai1.ui.screen.FavoriteFolderDialog

// 为 DetailViewModel 创建一个 Factory
class DetailViewModelFactory(private val application: Application, private val comicId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(application, comicId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun DetailScreen(
    comicId: String,
    onNavigateToReader: (String) -> Unit,
    onNavigateToTagSearch: (String) -> Unit // 【修改】标签搜索导航回调
) {
    // 使用 Factory 来创建 ViewModel 实例
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModelFactory(LocalContext.current.applicationContext as Application, comicId)
    )
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "正在加载漫画详情...")
            }
        }
        uiState.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "发生错误: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        uiState.comic != null -> {
            ComicDetailContent(
                comic = uiState.comic!!,
                onReadClick = {
                    onNavigateToReader(comicId)
                },
                comicId = comicId,
                isFavorite = uiState.isFavorite,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onNavigateToTagSearch = onNavigateToTagSearch, // 【修改】传递回调
                foldersWithCount = uiState.foldersWithCount,
                onAddToFavoriteFolder = { folder -> viewModel.addToFavoriteFolder(folder) },
                onCreateNewFolder = { folderName -> viewModel.createNewFolderAndAddToFavorites(folderName) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailContent(
    comic: Comic,
    onReadClick: () -> Unit,
    comicId: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToTagSearch: (String) -> Unit, // 【修改】标签搜索导航回调
    foldersWithCount: List<FavoriteFolderWithCount>,
    onAddToFavoriteFolder: (FavoriteFolder) -> Unit,
    onCreateNewFolder: (String) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        FavoriteFolderDialog(
            foldersWithCount = foldersWithCount,
            onDismiss = { showDialog = false },
            onFolderSelected = {
                onAddToFavoriteFolder(it)
                showDialog = false
            },
            onCreateNewFolder = {
                onCreateNewFolder(it)
                showDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = comic.coverUrl,
                    contentDescription = comic.title,
                    modifier = Modifier.height(400.dp) // 增加高度以获得更好的视觉效果
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 添加 comicId 和复制按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comic ID: $comicId",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Comic ID", comicId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Comic ID 已复制", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("复制")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 【修改】统一并排序详细信息区域 ---
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailTagListRow("作者", "artists", comic.artists, onNavigateToTagSearch) // 【修改】传递回调
                DetailTagListRow("社团", "groups", comic.groups, onNavigateToTagSearch)   // 【修改】传递回调
                DetailTagListRow("原作", "parodies", comic.parodies, onNavigateToTagSearch) // 【修改】传递回调
                DetailTagListRow("角色", "characters", comic.characters, onNavigateToTagSearch) // 【修改】传递回调
                DetailTagListRow("标签", "tags", comic.tags, onNavigateToTagSearch)     // 【修改】传递回调
                DetailTagListRow("语言", "languages", comic.languages, onNavigateToTagSearch) // 【修改】传递回调
                DetailTagListRow("分类", "categories", comic.categories, onNavigateToTagSearch) // 【修改】传递回调
            }
            // --- 【修改结束】---

            Spacer(modifier = Modifier.height(16.dp))

            // --- 【修改】功能按钮区域 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp), // 左右留出间距，让按钮组居中且不会过宽
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onReadClick,
                    modifier = Modifier.weight(1f) // 使用 weight 让按钮平分宽度，实现等宽
                ) {
                    Text("阅读")
                }

                // 根据收藏状态，动态改变按钮的颜色和文字
                val (buttonText, buttonColors) = if (isFavorite) {
                    // 已收藏：显示“取消收藏”，使用次要颜色，降低视觉强调
                    "取消收藏" to androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    // 未收藏：显示“加入收藏”，使用主要颜色，吸引用户点击
                    "加入收藏" to androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Button(
                    onClick = {
                        if (isFavorite) {
                            onToggleFavorite()
                        } else {
                            showDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f), // 使用 weight 让按钮平分宽度，实现等宽
                    colors = buttonColors
                ) {
                    Text(buttonText)
                }
            }
            // --- 【修改结束】---

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 图片列表
        if (comic.imageList.isNotEmpty()) {
            item {
                Text(text = "图片列表:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(comic.imageList) { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailTagListRow(
    label: String,
    tagType: String, // 【新增】标签类型，用于构建搜索URL
    tags: List<Tag>,
    onNavigateToTagSearch: (String) -> Unit // 【修改】标签搜索导航回调
) {
    if (tags.isNotEmpty()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$label: ",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.forEach { tag ->
                    Button(
                        onClick = { onNavigateToTagSearch("tag:${tagType}:${tag.id}:${tag.name}") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = tag.name)
                    }
                }
            }
        }
    }
}
