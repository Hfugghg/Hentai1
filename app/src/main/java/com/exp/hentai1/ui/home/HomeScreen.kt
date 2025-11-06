package com.exp.hentai1.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.ui.common.AppDrawer
import com.exp.hentai1.ui.common.singleClickable // 导入 singleClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onComicClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onRankingMoreClick: () -> Unit, // 新增的参数
    onSearch: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val (searchText, setSearchText) = remember { mutableStateOf("") }
    val listState = rememberLazyListState() // 记住列表状态

    // 监听滚动到底部，加载更多数据
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= listState.layoutInfo.totalItemsCount - 5 } // 当滚动到倒数第5个item时开始加载
            .distinctUntilChanged()
            .collect {
                if (uiState.canLoadMore && !uiState.isLoadingMore) {
                    viewModel.loadMoreLatestComics()
                }
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawer(
                    onMenuClick = {
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        // 使用 BasicTextField 来自定义搜索框，以精确控制内边距和高度
                        BasicTextField(
                            value = searchText,
                            onValueChange = setSearchText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp), // <-- 显著减小高度 (默认 TextField 约 56.dp)
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(searchText) }),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant, // 模拟 TextField 的背景
                                            RoundedCornerShape(20.dp) // <--- 使用圆角
                                        )
                                        .padding(horizontal = 16.dp), // <-- 控制水平内边距
                                    contentAlignment = Alignment.CenterStart // 垂直居中
                                ) {
                                    innerTextField() // 文本输入区域
                                    if (searchText.isEmpty()) {
                                        // 自定义 Placeholder
                                        Text(
                                            text = "搜索...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.apply {
                                    if (isClosed) open() else close()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = onFavoritesClick) {
                            Icon(Icons.Default.Favorite, contentDescription = "收藏")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "正在获取数据，请稍候...")
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .clickable { viewModel.fetchAllComics() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "发生错误: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(text = "点击屏幕重试")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        state = listState // 将列表状态传递给 LazyColumn
                    ) {
                        // 每日排行 Section
                        item {
                            SectionTitle(
                                title = "每日排行",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onMoreClick = onRankingMoreClick // 传入新的点击事件
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RankingSection(comics = uiState.rankingComics, onComicClick = onComicClick)
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            SectionTitle("每日更新", Modifier.padding(horizontal = 16.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 每日更新 Section
                        // 计算每个“页面”的起始索引，以便正确显示分页隔条
                        val comicsPerPage = 20 // 假设每页显示20个漫画，与后端逻辑或UI设计保持一致
                        val totalPages = (uiState.latestComics.size + comicsPerPage - 1) / comicsPerPage

                        for (pageIndex in 0 until totalPages) {
                            val startIdx = pageIndex * comicsPerPage
                            val endIdx = (startIdx + comicsPerPage).coerceAtMost(uiState.latestComics.size)
                            val pageComics = uiState.latestComics.subList(startIdx, endIdx)

                            item {
                                Text(
                                    text = "--- 第 ${pageIndex + 1} 页 --- ",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            items(pageComics) { comic ->
                                LatestUpdateItem(
                                    comic,
                                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    onComicClick = onComicClick
                                )
                            }
                        }

                        // 加载更多或没有更多数据的提示
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
                            } else if (!uiState.canLoadMore && uiState.latestComics.isNotEmpty()) {
                                Text(
                                    text = "没有更多了，主人~",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween // 使标题和按钮两端对齐
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        onMoreClick?.let {
            Text(
                text = "更多",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = it)
            )
        }
    }
}

@Composable
fun RankingSection(comics: List<Comic>, onComicClick: (String) -> Unit) {
    if (comics.isEmpty()) {
        Text(
            text = "没有找到排行数据",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(comics) { comic ->
                RankingComicItem(comic, onComicClick = onComicClick)
            }
        }
    }
}

@Composable
fun RankingComicItem(comic: Comic, onComicClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .singleClickable { onComicClick(comic.id) }, // 使用 singleClickable
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = comic.coverUrl,
            contentDescription = comic.title,
            modifier = Modifier.size(100.dp, 150.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LatestUpdateItem(comic: Comic, modifier: Modifier = Modifier, onComicClick: (String) -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .singleClickable { onComicClick(comic.id) }, // 使用 singleClickable
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comic.coverUrl,
            contentDescription = comic.title,
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = comic.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            // 修改此处，使用 languages 列表
            val languageText = if (comic.languages.isNotEmpty()) {
                comic.languages.joinToString { it.name }
            } else {
                "未知"
            }
            Text(text = "语言: $languageText", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
