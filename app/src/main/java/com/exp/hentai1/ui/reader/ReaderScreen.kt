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

// 保持 ReaderViewModelFactory 不变

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

    // --- 新增状态用于管理保存确认对话框 ---
    var imageToSave by remember { mutableStateOf<String?>(null) }
    // ----------------------------------------

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
                    }
                )
            }
        }

        // SnackbarHost 保持不变
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // --- 新增图片保存确认对话框 ---
        imageToSave?.let { imageUrl ->
            AlertDialog(
                onDismissRequest = {
                    // 点击外部或返回键时关闭对话框
                    imageToSave = null
                },
                title = { Text(text = "保存图片") },
                text = { Text("确定要将此页漫画保存到手机图库吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            imageToSave = null // 关闭对话框
                            // 调用 ViewModel 的保存逻辑，并在完成后显示 Snackbar
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
    onImageLongPress: (String) -> Unit // 新增长按回调参数
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val scrollableState = rememberScrollableState { delta ->
        // 只有在未缩放时才允许滚动 (scale == 1f)
        if (scale == 1f) {
            coroutineScope.launch {
                lazyListState.dispatchRawDelta(-delta)
            }
            delta
        } else {
            0f // 缩放状态下，滚动交给 detectTransformGestures 处理
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 使用 detectTransformGestures 来同时处理缩放和平移
                detectTransformGestures { centroid, pan, zoom, rotation -> // rotation 未使用，但保留作为手势检测的一部分

                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(1f, 5f)

                    if (newScale > 1f) {
                        // 1. 垂直平移 (Pan-Y): 转换为 LazyColumn 的滚动
                        coroutineScope.launch {
                            lazyListState.dispatchRawDelta(-pan.y)
                        }

                        // 2. 缩放 (Zoom) 和 水平平移 (Pan-X): 更新 offset
                        val newOffsetX = (offset.x + centroid.x - centroid.x * newScale / oldScale) + pan.x
                        // 垂直偏移量 (Pan-Y) 不用于 offset.y 的更新，因为它被用于滚动
                        val newOffsetY = (offset.y + centroid.y - centroid.y * newScale / oldScale)

                        // 限制水平偏移量，防止移出屏幕
                        val maxOffsetX = (size.width * (newScale - 1)) / 2
                        // 限制垂直偏移量 (用于缩放居中锚定，但不是列表平移)
                        val maxOffsetY = (size.height * (newScale - 1)) / 2

                        offset = Offset(
                            x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                            // 由于垂直滚动由 LazyColumn 负责，我们让 offset.y 保持相对居中
                            y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                        )

                    } else {
                        // 缩放回 1f 时，重置所有状态
                        scale = 1f
                        offset = Offset.Zero
                    }

                    // 在所有计算完成后再更新 scale
                    scale = newScale
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    orientation = Orientation.Vertical,
                    state = scrollableState,
                    enabled = scale == 1f // 只在未缩放时启用外部滚动
                )
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                    // 默认的 transformOrigin 是 Center，这对于基于 centroid 的计算是正确的
                )
        ) {
            ReaderContent(
                imageUrls = imageUrls,
                lazyListState = lazyListState,
                onImageLongPress = onImageLongPress // 传递长按回调
            )
        }
    }
}

@Composable
fun ReaderContent(
    imageUrls: List<String>,
    lazyListState: LazyListState,
    onImageLongPress: (String) -> Unit // 新增长按回调参数
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        userScrollEnabled = false // 禁用LazyColumn自带的滚动，使用外部的scrollable
    ) {
        items(items = imageUrls, key = { it }) { imageUrl ->
            ImageItem(
                imageUrl = imageUrl,
                onLongPress = { onImageLongPress(imageUrl) } // 绑定长按事件和回调
            )
        }
    }
}

@Composable
fun ImageItem(imageUrl: String, onLongPress: () -> Unit) { // 新增长按回调参数
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(imageUrl) { // 使用 imageUrl 作为 key 以便在 URL 改变时重新绑定手势
                detectTapGestures(
                    onLongPress = {
                        onLongPress()
                    }
                )
            },
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("图片加载失败")
            }
        }
    )
}