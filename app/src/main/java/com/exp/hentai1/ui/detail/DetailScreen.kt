package com.exp.hentai1.ui.detail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onNavigateToTagSearch: (String) -> Unit
) {
    // 使用 Factory 来创建 ViewModel 实例
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModelFactory(LocalContext.current.applicationContext as Application, comicId)
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
                    onNavigateToReader(comicId)
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
                onCreateNewFolder = { folderName -> viewModel.createNewFolderAndAddToFavorites(folderName) },
                viewModel = viewModel,
                tagStatuses = uiState.tagStatuses // 传递标签状态
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
    onCreateNewFolder: (String) -> Unit,
    viewModel: DetailViewModel,
    tagStatuses: Map<String, TagStatus> // 接收标签状态
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    // 新增状态，用于控制标签长按弹窗
    var selectedTag by remember { mutableStateOf<Pair<Tag, String>?>(null) }


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

    // 标签长按弹窗
    selectedTag?.let { (tag, tagType) ->
        TagActionDialog(
            tag = tag,
            onDismiss = { selectedTag = null },
            onToggleStatus = { currentStatus ->
                viewModel.toggleTagStatus(tag, tagType, currentStatus)
                selectedTag = null // 切换后关闭弹窗
            },
            currentStatus = tagStatuses[tag.id] ?: TagStatus.NONE // 传递当前状态
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                DetailTagListRow("作者", "artists", comic.artists, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("社团", "groups", comic.groups, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("原作", "parodies", comic.parodies, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("角色", "characters", comic.characters, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("标签", "tags", comic.tags, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("语言", "languages", comic.languages, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
                DetailTagListRow("分类", "categories", comic.categories, onNavigateToTagSearch, tagStatuses) { tag, tagType -> selectedTag = Pair(tag, tagType) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailTagListRow(
    label: String,
    tagType: String,
    tags: List<Tag>,
    onNavigateToTagSearch: (String) -> Unit,
    tagStatuses: Map<String, TagStatus>, // 接收标签状态
    onTagLongClick: (Tag, String) -> Unit // 新增长按事件回调
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
                    val tagStatus = tagStatuses[tag.id] ?: TagStatus.NONE
                    val containerColor = when (tagStatus) {
                        TagStatus.BLOCKED -> MaterialTheme.colorScheme.errorContainer
                        TagStatus.FAVORITE -> Color(0xFFFFD700)
                        TagStatus.NONE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when (tagStatus) {
                        TagStatus.BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
                        TagStatus.FAVORITE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        TagStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    // --- ⬇️ 这是修改后的代码块 ⬇️ ---

                    Surface(
                        // 1. 应用与 ElevatedAssistChip 相同的样式
                        shape = AssistChipDefaults.shape,
                        color = containerColor,
                        contentColor = contentColor,
                        border = if (tagStatus == TagStatus.FAVORITE) BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.7f)) else null,

                        // --- 这里是修复 ---
                        tonalElevation = 0.dp, // 替换 Unresolved reference
                        shadowElevation = 1.dp, // 替换 Unresolved reference (这是 ElevatedAssistChip 的默认阴影)
                        // --- 修复结束 ---

                        // 2. 使用 combinedClickable 作为唯一的手势处理器
                        modifier = Modifier
                            .height(AssistChipDefaults.Height) // 使用 Chip 的默认高度
                            .combinedClickable(
                                // 这里是我们的点击逻辑
                                onClick = {
                                    onNavigateToTagSearch("tag:${tagType}:${tag.id}:${tag.name}")
                                },
                                // 这里是我们的长按逻辑
                                onLongClick = {
                                    onTagLongClick(tag, tagType)
                                }
                            )
                    ) {
                        // 3. 复制 Chip 内部的布局（带 padding 的 Text）
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp), // AssistChip 的标准水平内边距
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelLarge // Chip 的标准字体
                            )
                        }
                    }
                    // --- ⬆️ 这是修改后的代码块 ⬆️ ---
                }
            }
        }
    }
}

/** 标签长按操作弹窗 */
@Composable
fun TagActionDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onToggleStatus: (TagStatus) -> Unit,
    currentStatus: TagStatus // 接收当前状态
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("标签操作: ${tag.name}", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 按钮 1: 复制标签名
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Tag Name", tag.name)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "标签名已复制", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制标签名")
                    Spacer(Modifier.width(8.dp))
                    Text("复制标签名")
                }
                Spacer(Modifier.height(8.dp))

                // 按钮 2: 切换状态
                val (buttonText, icon) = when (currentStatus) {
                    TagStatus.NONE -> Pair("收藏/关注标签", Icons.Outlined.FavoriteBorder)
                    TagStatus.FAVORITE -> Pair("拉黑标签", Icons.Outlined.Block)
                    TagStatus.BLOCKED -> Pair("取消拉黑", Icons.Outlined.Check)
                }
                Button(
                    onClick = {
                        onToggleStatus(currentStatus)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (currentStatus) {
                            TagStatus.NONE -> MaterialTheme.colorScheme.primary
                            TagStatus.FAVORITE -> MaterialTheme.colorScheme.error // 准备切换到拉黑，使用红色
                            TagStatus.BLOCKED -> MaterialTheme.colorScheme.tertiary // 准备切换到无状态，使用一个不同颜色
                        }
                    )
                ) {
                    Icon(icon, contentDescription = buttonText)
                    Spacer(Modifier.width(8.dp))
                    Text(buttonText)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
