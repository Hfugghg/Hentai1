package com.exp.hentai1.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.data.remote.parser.getLastParsedPage
import com.exp.hentai1.ui.common.AppDrawer
import com.exp.hentai1.ui.home.HomeComponents.CenteredIndicatorWithText
import com.exp.hentai1.ui.home.HomeComponents.RankingSection
import com.exp.hentai1.ui.home.HomeComponents.SectionTitle
import com.exp.hentai1.ui.home.HomeComponents.SiteSwitchBar
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
    val lastPage = remember { mutableStateOf<Int?>(null) }
    val showPageInputDialog = remember { mutableStateOf(false) }

    // 搜索记录与焦点管理
    val searchHistory by viewModel.searchHistory.collectAsState()
    val showSearchHistoryDropdown = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var searchBarWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // 为每个站点统一管理 LazyListState
    val mainListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val rankingListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val currentMainListState = mainListStates[currentSite] ?: rememberLazyListState()
    val currentRankingListState = rankingListStates[currentSite] ?: rememberLazyListState()

    // --- 新增：滚动时清除焦点 ---
    val isMainListScrolling by remember { derivedStateOf { currentMainListState.isScrollInProgress } }
    val isRankingListScrolling by remember { derivedStateOf { currentRankingListState.isScrollInProgress } }

    LaunchedEffect(isMainListScrolling, isRankingListScrolling) {
        if (isMainListScrolling || isRankingListScrolling) {
            focusManager.clearFocus()
            showSearchHistoryDropdown.value = false
        }
    }
    // --- 滚动时清除焦点结束 ---

    // 当站点切换且数据加载完毕后，恢复滚动位置
    LaunchedEffect(currentSite, uiState.loadedPages, uiState.rankingComics) {
        if (uiState.loadedPages.isNotEmpty()) {
            scope.launch {
                currentMainListState.scrollToItem(uiState.mainListScrollIndex, uiState.mainListScrollOffset)
            }
            lastPage.value = getLastParsedPage()
        }
        if (uiState.rankingComics.isNotEmpty()) {
            scope.launch {
                currentRankingListState.scrollToItem(uiState.rankingListScrollIndex, uiState.rankingListScrollOffset)
            }
        }
    }

    // 当列表滚动时，持续保存其位置
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
            .filter { it != null && it >= currentMainListState.layoutInfo.totalItemsCount - 5 }
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
                            "关于" -> "about"
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged {
                                    searchBarWidth = with(density) { it.width.toDp() }
                                }
                        ) {
                            BasicTextField(
                                value = searchText,
                                onValueChange = {
                                    setSearchText(it)
                                    // 当用户输入时，隐藏历史记录
                                    showSearchHistoryDropdown.value = it.isEmpty() && searchHistory.isNotEmpty()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            // 仅当搜索框为空时才显示
                                            showSearchHistoryDropdown.value = searchText.isEmpty() && searchHistory.isNotEmpty()
                                        } else {
                                            // --- 修改：移除此处的 hide 逻辑 ---
                                            // showSearchHistoryDropdown.value = false
                                            // 隐藏逻辑将由点击外部区域、选择项目或开始搜索来处理
                                        }
                                    },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (searchText.isNotBlank()) {
                                        viewModel.addSearchHistory(searchText)
                                        onSearch(searchText)
                                        setSearchText("")
                                        focusManager.clearFocus()
                                        showSearchHistoryDropdown.value = false // 搜索后隐藏
                                    }
                                }),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        innerTextField()
                                        if (searchText.isEmpty()) {
                                            Text(
                                                text = "搜索...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            )

                            // --- 替换：使用自定义的 Popup 替换 DropdownMenu ---
                            SearchHistoryPopup(
                                expanded = showSearchHistoryDropdown.value,
                                history = searchHistory,
                                width = searchBarWidth, // 传入测量好的宽度
                                onSelectItem = { historyItem ->
                                    setSearchText(historyItem)
                                    viewModel.addSearchHistory(historyItem)
                                    onSearch(historyItem)
                                    focusManager.clearFocus()
                                    showSearchHistoryDropdown.value = false
                                },
                                onClearItem = { historyItem ->
                                    viewModel.removeSearchHistoryItem(historyItem)
                                },
                                onClearAll = {
                                    viewModel.clearSearchHistory()
                                    focusManager.clearFocus()
                                    showSearchHistoryDropdown.value = false
                                }
                            )
                        }
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
            // --- 修改：将 clickable 应用于根 Column ---
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        showSearchHistoryDropdown.value = false
                    }
            ) {
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
                        val distinctRankingComics = uiState.rankingComics.distinctBy { it.id }

                        LazyColumn(
                            modifier = modifier.fillMaxSize(),
                            state = currentMainListState
                        ) {
                            item {
                                SectionTitle(
                                    title = "每日排行",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onMoreClick = onRankingMoreClick
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                RankingSection(
                                    comics = distinctRankingComics,
                                    onComicClick = onComicClick,
                                    rankingListState = currentRankingListState
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                SectionTitle(
                                    title = "每日更新",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    extraText = lastPage.value?.let { "共 $it 页" },
                                    onExtraTextClick = { showPageInputDialog.value = true }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            latestUpdatesSection(
                                loadedPages = uiState.loadedPages,
                                onComicClick = onComicClick
                            )

                            item {
                                val distinctLatestComics = uiState.loadedPages.flatMap { it.comics }.distinctBy { it.id }
                                if (uiState.isLoadingMore) {
                                    CenteredIndicatorWithText(
                                        text = "正在加载更多，请稍候...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                } else if (!uiState.canLoadMore && distinctLatestComics.isNotEmpty()) {
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

    if (showPageInputDialog.value) {
        PageInputDialog(
            maxPage = lastPage.value ?: 1,
            onDismiss = { showPageInputDialog.value = false },
            onPageSelected = { page ->
                viewModel.loadLatestComicsByPage(page)
                showPageInputDialog.value = false
                scope.launch {
                    currentMainListState.scrollToItem(0)
                }
            }
        )
    }
}

// --- 新增：自定义的搜索记录 Popup ---
@Composable
private fun SearchHistoryPopup(
    expanded: Boolean,
    history: List<String>,
    width: Dp,
    onSelectItem: (String) -> Unit,
    onClearItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    // 使用 LaunchedEffect + state 来驱动 AnimatedVisibility，以确保退出动画正常播放
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) isVisible = true else isVisible = false
    }

    if (width > 0.dp) { // 仅在宽度测量完毕后显示
        Popup(
            alignment = Alignment.TopStart,
            properties = PopupProperties(
                focusable = false, // 不获取焦点，允许点击搜索框
                dismissOnClickOutside = false // 我们自己处理点击外部
            )
        ) {
            // 动画
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                // 外观
                Surface(
                    modifier = Modifier
                        .width(width) // 使用与搜索框相同的宽度
                        .padding(top = 48.dp), // 40dp (搜索框高度) + 8dp (间距)
                    color = MaterialTheme.colorScheme.surfaceVariant, // 匹配搜索框颜色
                    shape = RoundedCornerShape(16.dp), // 圆角
                    shadowElevation = 8.dp
                ) {
                    // 限制高度
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp) // 最大高度限制
                    ) {
                        items(history) { item ->
                            // 单条记录
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectItem(item) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                // 单条删除按钮
                                IconButton(
                                    onClick = { onClearItem(item) },
                                    modifier = Modifier.size(28.dp) // 较小的点击区域
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除 $item",
                                        modifier = Modifier.size(18.dp) // 较小的图标
                                    )
                                }
                            }
                        }

                        // 清空按钮
                        if (history.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                Text(
                                    text = "清空搜索记录",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onClearAll() }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}