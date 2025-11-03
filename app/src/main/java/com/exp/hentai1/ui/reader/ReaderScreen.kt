package com.exp.hentai1.ui.reader

import android.app.Application
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

// 保持 ReaderViewModelFactory 和 ReaderScreen 不变

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
            ZoomableReader(imageUrls = uiState.imageUrls!!)
        }
    }
}

@Composable
fun ZoomableReader(imageUrls: List<String>) {
    var scale by remember { mutableStateOf(1f) }
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
                detectTransformGestures { centroid, pan, zoom, rotation ->

                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(1f, 5f)

                    if (newScale > 1f) {
                        // --- 核心修改 ---
                        // 1. 垂直平移 (Pan-Y): 转换为 LazyColumn 的滚动
                        //    我们不更新 offset.y，而是滚动列表
                        coroutineScope.launch {
                            lazyListState.dispatchRawDelta(-pan.y)
                        }

                        // 2. 缩放 (Zoom) 和 水平平移 (Pan-X): 更新 offset
                        //    我们计算新的 offset，使其围绕手势中心点 (centroid) 缩放

                        // 计算缩放导致的偏移变化
                        // (offset + centroid) = 将原点移动到手势中心
                        // (... * newScale / oldScale) = 在新原点上进行缩放
                        // (... - centroid) = 将原点移回
                        // (...+ pan.x) = 应用水平平移
                        // (我们忽略 pan.y, 因为它被用于滚动)

                        val newOffsetX = (offset.x + centroid.x - centroid.x * newScale / oldScale) + pan.x
                        val newOffsetY = (offset.y + centroid.y - centroid.y * newScale / oldScale)

                        // 限制水平偏移量，防止移出屏幕
                        val maxOffsetX = (size.width * (newScale - 1)) / 2
                        // 垂直偏移量也需要限制，但它现在只用于“锚定”缩放中心，而不是平移
                        val maxOffsetY = (size.height * (newScale - 1)) / 2

                        offset = Offset(
                            x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
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
                scaleState = scale
            )
        }
    }
}

@Composable
fun ReaderContent(imageUrls: List<String>, lazyListState: LazyListState, scaleState: Float) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        userScrollEnabled = false // 禁用LazyColumn自带的滚动，使用外部的scrollable
    ) {
        items(items = imageUrls, key = { it }) { imageUrl ->
            ImageItem(imageUrl = imageUrl)
        }
    }
}

@Composable
fun ImageItem(imageUrl: String) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
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