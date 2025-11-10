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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.data.remote.parser.getLastParsedPage
import com.exp.hentai1.ui.common.AppDrawer
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle
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
    onMenuClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSite by viewModel.currentSite.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val (searchText, setSearchText) = remember { mutableStateOf("") }
    val lastPage = remember { mutableStateOf<Int?>(null) } // <--- 新增: 存储 lastPage

    // 控制页码输入弹窗的显示状态
    val showPageInputDialog = remember { mutableStateOf(false) }

    // 为每个站点统一管理 LazyListState，以在切换后恢复滚动位置
    val mainListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val rankingListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val currentMainListState = mainListStates[currentSite]!!
    val currentRankingListState = rankingListStates[currentSite]!!

    // 当站点切换且数据加载完毕后，恢复滚动位置
    LaunchedEffect(currentSite, uiState.loadedPages, uiState.rankingComics) { // <--- 修复：使用 uiState.loadedPages
        // 只有当数据实际存在时才滚动，避免在空列表上操作
        if (uiState.loadedPages.isNotEmpty()) { // <--- 修复：使用 uiState.loadedPages
            scope.launch {
                currentMainListState.scrollToItem(uiState.mainListScrollIndex, uiState.mainListScrollOffset)
            }
            // <--- 新增: 当漫画列表更新时，获取 lastPage
            lastPage.value = getLastParsedPage()
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
                    onMenuClick = { menuItemTitle ->
                        scope.launch {
                            drawerState.close()
                        }
                        val route = when (menuItemTitle) {
                            "标签" -> "list/tags"
                            "原作" -> "list/parodies"
                            "角色" -> "list/characters"
                            "作者" -> "list/artists"
                            "团队" -> "list/groups"
                            "排行" -> "rankingMore"
                            "本地" -> "local"
                            "收藏" -> "favorites"
                            "设置" -> "settings"
                            else -> ""
                        }
                        if (route.isNotEmpty()) {
                            onMenuClick(route)
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
                        // 使用 BasicTextField 自定义搜索框，以精确控制内边距和高度
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
                        // 为了防止因数据源中可能存在的重复ID导致应用崩溃，在渲染列表前先进行去重。
                        val distinctRankingComics = uiState.rankingComics.distinctBy { it.id }
                        // 注意：我们直接使用 uiState.loadedPages，并在其内部的 items 块中去重。

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
                                    comics = distinctRankingComics, // 使用去重后的列表
                                    onComicClick = onComicClick,
                                    rankingListState = currentRankingListState // 传递当前站点的排行列表状态
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                // <--- 修改点: 传递 lastPage 并添加点击事件
                                SectionTitle(
                                    title = "每日更新",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    extraText = lastPage.value?.let { "共 $it 页" },
                                    onExtraTextClick = { showPageInputDialog.value = true } // 点击页数时显示弹窗
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // 每日更新 Section
                            latestUpdatesSection(
                                loadedPages = uiState.loadedPages, // <--- 修复：直接传递结构化的 loadedPages
                                onComicClick = onComicClick
                            )


                            // 加载更多或没有更多数据的提示
                            item {
                                // 重新计算 distinctLatestComics 用于底部提示的判断
                                val distinctLatestComics = uiState.loadedPages.flatMap { it.comics }.distinctBy { it.id }
                                if (uiState.isLoadingMore) {
                                    CenteredIndicatorWithText(
                                        text = "正在加载更多，请稍候...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                } else if (!uiState.canLoadMore && distinctLatestComics.isNotEmpty()) { // 使用去重后的列表进行判断
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

    // 页码输入弹窗
    if (showPageInputDialog.value) {
        PageInputDialog(
            maxPage = lastPage.value ?: 1, // 如果没有获取到最大页数，默认为1
            onDismiss = { showPageInputDialog.value = false },
            onPageSelected = { page ->
                viewModel.loadLatestComicsByPage(page)
                showPageInputDialog.value = false
                scope.launch {
                    currentMainListState.scrollToItem(0) // 跳转到指定页后滚动到顶部
                }
            }
        )
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
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    extraText: String? = null, // <--- 新增: 额外的文本
    onExtraTextClick: (() -> Unit)? = null // <--- 新增: 额外文本的点击事件
) {
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

        // <--- 修改点: 显示 "更多" 或 extraText，并处理 extraText 的点击事件
        if (onMoreClick != null) {
            Text(
                text = "更多",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onMoreClick)
            )
        } else if (extraText != null) {
            Text(
                text = extraText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, // 让页数文本也使用主题色，表示可点击
                modifier = Modifier.clickable(enabled = onExtraTextClick != null, onClick = { onExtraTextClick?.invoke() })
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
        // 使用 BoxWithConstraints 来获取父容器的宽度信息
        BoxWithConstraints {
            val screenWidth = maxWidth
            // 定义响应式断点
            val cardWidth = if (screenWidth < 600.dp) {
                100.dp // 手机竖屏等窄屏幕
            } else {
                140.dp // 平板或手机横屏等宽屏幕
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                state = rankingListState
            ) {
                items(comics, key = { it.id }) { comic ->
                    ComicCard(
                        comic = comic,
                        style = ComicCardStyle.GRID,
                        modifier = Modifier.width(cardWidth), // 应用动态计算的宽度
                        onComicClick = onComicClick
                    )
                }
            }
        }
    }
}

// *** 删除旧的、错误的 latestUpdatesSection 函数 ***

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageInputDialog(
    maxPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到指定页") },
        text = {
            Column {
                Text("当前最大页数: $maxPage")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { newValue ->
                        pageInput = newValue
                        isError = false // 每次输入改变时重置错误状态
                    },
                    label = { Text("页码") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val page = pageInput.toIntOrNull()
                        if (page != null && page > 0 && page <= maxPage) {
                            onPageSelected(page)
                        } else {
                            isError = true
                        }
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "请输入 1 到 $maxPage 之间的有效页码",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val page = pageInput.toIntOrNull()
                    if (page != null && page > 0 && page <= maxPage) {
                        onPageSelected(page)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 每日更新列表的扩展函数，用于在 LazyColumn 中构建分页内容。
 */
private fun LazyListScope.latestUpdatesSection(
    loadedPages: List<LoadedPage>, // <--- 修复：接收结构化的 LoadedPage 列表
    onComicClick: (String) -> Unit
) {
    if (loadedPages.isEmpty()) return

    // 遍历每一个已加载的页面
    loadedPages.forEach { loadedPage -> // <--- 核心修改：遍历 loadedPages
        // 1. 插入分页条
        item {
            // 直接使用 LoadedPage 中存储的 page 属性作为页码
            Text(
                text = "--- 第 ${loadedPage.page} 页 --- ", // <--- 修复：使用 loadedPage.page，确保页码与数据块匹配
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // 2. 插入当前页的漫画列表
        items(loadedPage.comics.distinctBy { it.id }, key = { "latest-${it.id}" }) { comic -> // <--- 修复：遍历 loadedPage.comics 并去重
            ComicCard(
                comic = comic,
                style = ComicCardStyle.LIST,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onComicClick = onComicClick
            )
        }
    }
}