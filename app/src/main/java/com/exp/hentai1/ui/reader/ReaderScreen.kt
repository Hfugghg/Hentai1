package com.exp.hentai1.ui.reader

import android.app.Application
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

// ReaderViewModelFactory 保持不变
class ReaderViewModelFactory(private val application: Application, private val comicId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(application, comicId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun ReaderScreen(comicId: String) {
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(LocalContext.current.applicationContext as Application, comicId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var imageToSave by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "正在加载图片...")
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
            uiState.imageUrls != null -> {
                ZoomableReader(
                    imageUrls = uiState.imageUrls!!,
                    onImageLongPress = { imageUrl ->
                        // 长按时，设置待保存图片URL，触发对话框显示
                        imageToSave = imageUrl
                    },
                    // --- 新增: 实现中部点击事件 ---
                    onTap = {
                        // TODO: 在此处触发显示/隐藏阅读菜单的逻辑

                        // (临时) 使用 Snackbar 演示点击已捕获
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "中部点击 (TODO: 显示菜单)",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
            }
        }

        // --- 优化: 移除了重复的 SnackbarHost ---

        // 图片保存确认对话框
        imageToSave?.let { imageUrl ->
            AlertDialog(
                onDismissRequest = {
                    imageToSave = null
                },
                title = { Text(text = "保存图片") },
                text = { Text("确定要将此页漫画保存到手机图库吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            imageToSave = null // 关闭对话框
                            viewModel.saveImageToGallery(imageUrl) { message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        withDismissAction = true
                                    )
                                }
                            }
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            imageToSave = null // 关闭对话框
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        // SnackbarHost 放置在 Box 的底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun ZoomableReader(
    imageUrls: List<String>,
    onImageLongPress: (String) -> Unit,
    onTap: () -> Unit // <-- 新增: 点击事件回调
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 这个 ScrollableState 用于处理未缩放 (scale == 1f) 时的标准垂直滚动/Fling
    val scrollableState = rememberScrollableState { delta ->
        if (scale == 1f) {
            coroutineScope.launch {
                lazyListState.dispatchRawDelta(-delta) // 将滚动增量传递给 LazyColumn
            }
            delta
        } else {
            0f // 缩放时，禁用此滚动，交由手势检测处理
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // detectTransformGestures 是现代 Compose 中处理缩放/平移的核心
                // 它允许我们同时响应多点触控（缩放）和单指拖动（平移）
                detectTransformGestures { centroid, pan, zoom, _ ->

                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(1f, 5f) // 限制缩放范围

                    if (newScale > 1f) {
                        // --- 现代阅读器手势处理模式 ---
                        // 1. 垂直平移 (Pan-Y): 当放大时，垂直拖动不应移动画布 (offset.y)，
                        //    而应直接滚动下方的 LazyColumn。
                        coroutineScope.launch {
                            lazyListState.dispatchRawDelta(-pan.y)
                        }

                        // 2. 缩放 (Zoom) 和 水平平移 (Pan-X):
                        //    - (offset.x + centroid.x - centroid.x * newScale / oldScale) 是基于缩放中心 (centroid) 计算新的 X 偏移量
                        //    - + pan.x 是应用手指的水平拖动
                        val newOffsetX = (offset.x + centroid.x - centroid.x * newScale / oldScale) + pan.x

                        //    - Y 偏移量同理计算，但不应用 pan.y，因为 pan.y 用于滚动列表
                        val newOffsetY = (offset.y + centroid.y - centroid.y * newScale / oldScale)

                        // 限制偏移量，防止图片移出边界
                        val maxOffsetX = (size.width * (newScale - 1)) / 2
                        val maxOffsetY = (size.height * (newScale - 1)) / 2

                        offset = Offset(
                            x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                            y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                        )

                    } else {
                        // 当缩回原始大小时，重置所有状态
                        scale = 1f
                        offset = Offset.Zero
                    }

                    // 在所有计算完成后更新 scale
                    scale = newScale
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable( // 附加一个外部 scrollable 以处理未缩放时的滚动
                    orientation = Orientation.Vertical,
                    state = scrollableState,
                    enabled = scale == 1f // 仅在未缩放时启用
                )
                .graphicsLayer( // 使用 graphicsLayer 高效地应用缩放和平移
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            ReaderContent(
                imageUrls = imageUrls,
                lazyListState = lazyListState,
                onImageLongPress = onImageLongPress,
                onTap = onTap // <-- 传递点击事件
            )
        }
    }
}

@Composable
fun ReaderContent(
    imageUrls: List<String>,
    lazyListState: LazyListState,
    onImageLongPress: (String) -> Unit,
    onTap: () -> Unit // <-- 新增: 点击事件回调
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        userScrollEnabled = false // 禁用 LazyColumn 的内置滚动，由外部的 ZoomableReader 完全接管
    ) {
        items(items = imageUrls, key = { it }) { imageUrl ->
            ImageItem(
                imageUrl = imageUrl,
                onLongPress = { onImageLongPress(imageUrl) },
                onTap = onTap // <-- 传递点击事件
            )
        }
    }
}

@Composable
fun ImageItem(
    imageUrl: String,
    onLongPress: () -> Unit,
    onTap: () -> Unit // <-- 新增: 点击事件回调
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            // 将所有手势检测统一放在 ImageItem 上
            .pointerInput(imageUrl, onLongPress, onTap) { // 确保 lambda 作为 key
                detectTapGestures(
                    onLongPress = {
                        onLongPress()
                    },
                    onTap = {
                        onTap() // <-- 新增: 捕获点击事件
                    }
                )
            },
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp), // 保持占位符高度
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp), // 保持占位符高度
                contentAlignment = Alignment.Center
            ) {
                Text("图片加载失败")
            }
        }
    )
}