package com.exp.hentai1.ui.detail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
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
class DetailViewModelFactory(private val application: Application, private val comicId: String, private val isLocal: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(application, comicId, isLocal) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun DetailScreen(
    comicId: String,
    onNavigateToReader: (String, Boolean) -> Unit, // 修改签名，接受 isLocal 参数
    onNavigateToTagSearch: (String) -> Unit,
    isLocal: Boolean = false // 新增 isLocal 参数，默认为 false
) {
    // 使用 Factory 来创建 ViewModel 实例，并传入 isLocal
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModelFactory(LocalContext.current.applicationContext as Application, comicId, isLocal)
    )
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    // 监听错误状态，并在有错误时显示 Toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    when {
        uiState.isLoading && uiState.comic == null -> { // 只在初次加载时显示全屏加载
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
        uiState.comic != null -> {
            ComicDetailContent(
                comic = uiState.comic!!,
                coverImageModel = uiState.coverImageModel, // 传递 coverImageModel
                onReadClick = {
                    onNavigateToReader(comicId, isLocal) // 传递 isLocal 参数
                },
                onDownloadClick = {
                    Toast.makeText(context, "正在准备下载数据...", Toast.LENGTH_SHORT).show()
                    viewModel.downloadComic()
                },
                comicId = comicId,
                isFavorite = uiState.isFavorite,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onNavigateToTagSearch = onNavigateToTagSearch,
                foldersWithCount = uiState.foldersWithCount,
                onAddToFavoriteFolder = { folder -> viewModel.addToFavoriteFolder(folder) },
                onCreateNewFolder = { folderName -> viewModel.createNewFolderAndAddToFavorites(folderName) }
            )
        }
        // 当 comic 为 null 且有错误时，显示错误页面
        uiState.error != null && uiState.comic == null -> {
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailContent(
    comic: Comic,
    coverImageModel: Any?, // 接收 coverImageModel
    onReadClick: () -> Unit,
    onDownloadClick: () -> Unit,
    comicId: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToTagSearch: (String) -> Unit,
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
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    AsyncImage(
                        model = coverImageModel, // 使用 coverImageModel
                        contentDescription = comic.title,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                DetailTagListRow("作者", "artists", comic.artists, onNavigateToTagSearch)
                DetailTagListRow("社团", "groups", comic.groups, onNavigateToTagSearch)
                DetailTagListRow("原作", "parodies", comic.parodies, onNavigateToTagSearch)
                DetailTagListRow("角色", "characters", comic.characters, onNavigateToTagSearch)
                DetailTagListRow("标签", "tags", comic.tags, onNavigateToTagSearch)
                DetailTagListRow("语言", "languages", comic.languages, onNavigateToTagSearch)
                DetailTagListRow("分类", "categories", comic.categories, onNavigateToTagSearch)
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onReadClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = "阅读",
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("阅读", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "下载",
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("下载", style = MaterialTheme.typography.labelSmall)
                    }
                }

                val (buttonText, buttonIcon, buttonColors) = if (isFavorite) {
                    Triple(
                        "已收藏",
                        Icons.Filled.Favorite,
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                } else {
                    Triple(
                        "收藏",
                        Icons.Outlined.FavoriteBorder,
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
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
                    modifier = Modifier.weight(1f),
                    colors = buttonColors,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = buttonText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(buttonText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (comic.imageList.isNotEmpty()) {
            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
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
    tagType: String,
    tags: List<Tag>,
    onNavigateToTagSearch: (String) -> Unit
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
                    ElevatedAssistChip(
                        onClick = { onNavigateToTagSearch("tag:${tagType}:${tag.id}:${tag.name}") },
                        label = { Text(text = tag.name) },
                        colors = AssistChipDefaults.elevatedAssistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}
