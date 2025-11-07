package com.exp.hentai1.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.ui.common.AppDrawer
import com.exp.hentai1.ui.common.singleClickable
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
    onRankingMoreClick: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSite by viewModel.currentSite.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val (searchText, setSearchText) = remember { mutableStateOf("") }

    // 为每个站点统一管理 LazyListState，以在切换后恢复滚动位置
    val mainListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val rankingListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val currentMainListState = mainListStates[currentSite]!!
    val currentRankingListState = rankingListStates[currentSite]!!

    // 当站点切换且数据加载完毕后，恢复滚动位置
    LaunchedEffect(currentSite, uiState.latestComics, uiState.rankingComics) {
        // 只有当数据实际存在时才滚动，避免在空列表上操作
        if (uiState.latestComics.isNotEmpty()) {
            scope.launch {
                currentMainListState.scrollToItem(uiState.mainListScrollIndex, uiState.mainListScrollOffset)
            }
        }
        if (uiState.rankingComics.isNotEmpty()) {
            scope.launch {
                currentRankingListState.scrollToItem(uiState.rankingListScrollIndex, uiState.rankingListScrollOffset)
            }
        }
    }

    // 当列表滚动时，持续保存其位置
    // 使用 DisposableEffect 确保在站点切换时，旧的监听器被正确清理
    DisposableEffect(currentSite) {
        val mainJob = scope.launch {
            snapshotFlow { currentMainListState.firstVisibleItemIndex to currentMainListState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { (index, offset) ->
                    viewModel.updateMainListScrollPosition(currentSite, index, offset)
                }
        }
        val rankingJob = scope.launch {
            snapshotFlow { currentRankingListState.firstVisibleItemIndex to currentRankingListState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { (index, offset) ->
                    viewModel.updateRankingListScrollPosition(currentSite, index, offset)
                }
        }
        onDispose {
            mainJob.cancel()
            rankingJob.cancel()
        }
    }

    // 监听滚动到底部，加载更多数据
    LaunchedEffect(currentMainListState, uiState.canLoadMore, uiState.isLoadingMore) {
        snapshotFlow { currentMainListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= currentMainListState.layoutInfo.totalItemsCount - 5 } // 当滚动到倒数第5个item时开始加载
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
            Column(modifier = Modifier.padding(paddingValues)) {
                // 站点切换栏
                SiteSwitchBar(
                    currentSite = currentSite,
                    onSiteSelected = { viewModel.switchSite(it) }
                )

                when {
                    uiState.isLoading -> {
                        CenteredIndicatorWithText(
                            text = "正在获取数据，请稍候...",
                            modifier = modifier.fillMaxSize()
                        )
                    }

                    uiState.error != null -> {
                        Column(
                            modifier = modifier
                                .fillMaxSize()
                                .clickable { viewModel.fetchAllComics(currentSite) },
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
                            modifier = modifier.fillMaxSize(),
                            state = currentMainListState // 将当前站点的列表状态传递给 LazyColumn
                        ) {
                            // 每日排行 Section
                            item {
                                SectionTitle(
                                    title = "每日排行",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onMoreClick = onRankingMoreClick // 传入新的点击事件
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                RankingSection(
                                    comics = uiState.rankingComics,
                                    onComicClick = onComicClick,
                                    rankingListState = currentRankingListState // 传递当前站点的排行列表状态
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                SectionTitle("每日更新", Modifier.padding(horizontal = 16.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // 每日更新 Section
                            latestUpdatesSection(
                                comics = uiState.latestComics,
                                onComicClick = onComicClick
                            )


                            // 加载更多或没有更多数据的提示
                            item {
                                if (uiState.isLoadingMore) {
                                    CenteredIndicatorWithText(
                                        text = "正在加载更多，请稍候...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                } else if (!uiState.canLoadMore && uiState.latestComics.isNotEmpty()) {
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

@Composable
private fun CenteredIndicatorWithText(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text)
    }
}

@Composable
fun SiteSwitchBar(
    currentSite: HentaiOneSite,
    onSiteSelected: (HentaiOneSite) -> Unit,
    modifier: Modifier = Modifier
) {
    // 将站点与其显示名称关联起来，便于维护
    val siteDisplayNames = remember {
        mapOf(
            HentaiOneSite.MAIN to "日本語",
            HentaiOneSite.CHINESE to "中文",
            HentaiOneSite.ENGLISH to "English"
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp), // 移除了垂直方向的 padding
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 使用 HentaiOneSite.entries 动态创建按钮，更具扩展性
        HentaiOneSite.entries.forEach { site ->
            val isSelected = currentSite == site
            val siteName = siteDisplayNames[site] ?: site.name // 如果没有在 map 中定义，则使用枚举名作为后备

            TextButton(
                onClick = { onSiteSelected(site) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = siteName,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
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
fun RankingSection(comics: List<Comic>, onComicClick: (String) -> Unit, rankingListState: LazyListState) {
    if (comics.isEmpty()) {
        Text(
            text = "没有找到排行数据",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            textAlign = TextAlign.Center
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            state = rankingListState // 使用从 HomeScreen 传递过来的独立列表状态
        ) {
            items(comics, key = { it.id }) { comic -> // 添加 key 参数
                RankingComicItem(comic, onComicClick = onComicClick)
            }
        }
    }
}

/**
 * 每日更新列表的扩展函数，用于在 LazyColumn 中构建分页内容。
 */
private fun LazyListScope.latestUpdatesSection(
    comics: List<Comic>,
    onComicClick: (String) -> Unit
) {
    // 计算每个“页面”的起始索引，以便正确显示分页隔条
    val comicsPerPage = 20 // 假设每页显示20个漫画，与后端逻辑或UI设计保持一致
    val totalPages = (comics.size + comicsPerPage - 1) / comicsPerPage

    if (comics.isEmpty()) return

    for (pageIndex in 0 until totalPages) {
        val startIdx = pageIndex * comicsPerPage
        val endIdx = (startIdx + comicsPerPage).coerceAtMost(comics.size)
        val pageComics = comics.subList(startIdx, endIdx)

        item {
            Text(
                text = "--- 第 ${pageIndex + 1} 页 --- ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        items(pageComics, key = { "latest-${it.id}" }) { comic -> // 为 key 添加前缀以避免与排行中的 key 冲突
            LatestUpdateItem(
                comic,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onComicClick = onComicClick
            )
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
            overflow = TextOverflow.Ellipsis
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
