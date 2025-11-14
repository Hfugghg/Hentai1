package com.exp.hentai1.ui.home

// --- 修改：从 ViewModel 导入 ---
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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
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

// --- 删除：搜索模式 ---
// (已移动到 ViewModel)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onComicClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onTagCollectionClick: () -> Unit, // 新增：标签收藏点击事件
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

    // --- 新增：搜索模式状态 ---
    var searchMode by remember { mutableStateOf(SearchMode.TEXT) }
    var showSearchModeMenu by remember { mutableStateOf(false) }
    // ---

    // 为每个站点统一管理 LazyListState
    val mainListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val rankingListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val currentMainListState = mainListStates[currentSite] ?: rememberLazyListState()
    val currentRankingListState = rankingListStates[currentSite] ?: rememberLazyListState()

    // --- 新增：滚动时清除焦点 ---
    val isMainListScrolling by remember { derivedStateOf { currentMainListState.isScrollInProgress } }
    val isRankingListScrolling by remember { derivedStateOf { currentRankingListState.isScrollInProgress } }

    // --- 新增：监听来自 ViewModel 的导航事件 ---
    LaunchedEffect(viewModel.navigateToDetail) {
        viewModel.navigateToDetail.collect { comicId ->
            // 收到事件，执行导航
            onComicClick(comicId)
        }
    }

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
                                    // --- 修改：无论何种模式，只要为空且有历史就显示 ---
                                    showSearchHistoryDropdown.value = it.isEmpty() && searchHistory.isNotEmpty()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            // --- 修改：无论何种模式，只要为空且有历史就显示 ---
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
                                // --- 修改：动态键盘类型 ---
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                    keyboardType = if (searchMode == SearchMode.TEXT) KeyboardType.Text else KeyboardType.Number
                                ),
                                // --- 修改：根据搜索模式执行不同操作 ---
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (searchText.isNotBlank()) {
                                        if (searchMode == SearchMode.TEXT) {
                                            // 文本搜索（原有逻辑）
                                            viewModel.addSearchHistory(searchText, SearchMode.TEXT) // --- 修改：传入类型 ---
                                            onSearch(searchText)
                                            setSearchText("")
                                            focusManager.clearFocus()
                                            showSearchHistoryDropdown.value = false // 搜索后隐藏
                                        } else {
                                            // ID 搜索
                                            // --- 修改：调用 ViewModel 的新函数 ---
                                            viewModel.findAndNavigateToComicId(searchText)
                                            // ---

                                            // --- 修改：VM 成功后会添加历史记录，这里只清空 ---
                                            setSearchText("")
                                            focusManager.clearFocus()
                                            showSearchHistoryDropdown.value = false
                                        }
                                    }
                                }),
                                // --- 修改：decorationBox 以包含模式切换按钮 ---
                                decorationBox = { innerTextField ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 12.dp), // 调整内边距
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // --- 新增：搜索模式切换 ---
                                        Box {
                                            IconButton(
                                                onClick = { showSearchModeMenu = true },
                                                modifier = Modifier.size(24.dp) // 较小的点击区域
                                            ) {
                                                Icon(
                                                    imageVector = if (searchMode == SearchMode.TEXT) Icons.Default.Search else Icons.Default.Tag,
                                                    contentDescription = "切换搜索模式",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp) // 较小的图标
                                                )
                                            }
                                            // 模式选择下拉菜单
                                            DropdownMenu(
                                                expanded = showSearchModeMenu,
                                                onDismissRequest = { showSearchModeMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("搜标题") },
                                                    onClick = {
                                                        searchMode = SearchMode.TEXT
                                                        showSearchModeMenu = false
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Search,
                                                            contentDescription = "搜标题")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("搜ID") },
                                                    onClick = {
                                                        searchMode = SearchMode.ID
                                                        showSearchModeMenu = false
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Tag,
                                                            contentDescription = "搜ID")
                                                    }
                                                )
                                            }
                                        }
                                        // --- 搜索模式切换结束 ---

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 内部文本字段和占位符
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            innerTextField()
                                            if (searchText.isEmpty()) {
                                                Text(
                                                    // 动态占位符
                                                    text = if (searchMode == SearchMode.TEXT) "搜索..." else "输入漫画ID...",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            // --- 替换：使用自定义的 Popup 替换 DropdownMenu ---
                            // (搜索历史)
                            SearchHistoryPopup(
                                expanded = showSearchHistoryDropdown.value, // 依赖于 state
                                history = searchHistory,
                                width = searchBarWidth, // 传入测量好的宽度
                                onSelectItem = { historyItem ->
                                    setSearchText(historyItem.query)
                                    // 重新添加，使其排到最前
                                    viewModel.addSearchHistory(historyItem.query, historyItem.type)

                                    // --- 关键修改：根据类型执行不同操作 ---
                                    if (historyItem.type == SearchMode.TEXT) {
                                        onSearch(historyItem.query)
                                    } else {
                                        viewModel.findAndNavigateToComicId(historyItem.query)
                                    }
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
                    onSiteSelected = { viewModel.switchSite(it) },
                    onTagCollectionClick = onTagCollectionClick // 修改：传递点击事件
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

                        val distinctLatestComics = uiState.loadedPages.flatMap { it.comics }.distinctBy { it.id }

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
                                // --- 修改点 3：这里的 distinctLatestComics 现在引用的是上面定义的变量 ---
                                if (uiState.isLoadingMore) {
                                    CenteredIndicatorWithText(
                                        text = "正在加载更多，请稍候...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                } else if (!uiState.canLoadMore && distinctLatestComics.isNotEmpty()) { // <-- 使用已定义的变量
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
    history: List<SearchHistoryItem>, // --- 修改：类型 ---
    width: Dp,
    onSelectItem: (SearchHistoryItem) -> Unit, // --- 修改：类型 ---
    onClearItem: (SearchHistoryItem) -> Unit, // --- 修改：类型 ---
    onClearAll: () -> Unit
) {
    // 使用 LaunchedEffect + state 来驱动 AnimatedVisibility，以确保退出动画正常播放
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) isVisible = true else isVisible = false
    }

    // --- 修复：获取 density 并计算 offset ---
    val density = LocalDensity.current
    val topOffsetPx = with(density) { 48.dp.roundToPx() } // 40dp (搜索框) + 8dp (间距)

    if (width > 0.dp) { // 仅在宽度测量完毕后显示
        Popup(
            alignment = Alignment.TopStart,
            // --- 修复：使用 offset 移动整个 Popup 容器 ---
            offset = androidx.compose.ui.unit.IntOffset(0, topOffsetPx),
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
                        .width(width), // --- 修复：移除 .padding(top = 48.dp) ---
                    color = MaterialTheme.colorScheme.surfaceVariant, // 匹配搜索框颜色
                    shape = RoundedCornerShape(16.dp), // 圆角
                    shadowElevation = 8.dp
                ) {
                    // 限制高度
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp) // 最大高度限制
                    ) {
                        items(history, key = { it.query + it.type.name }) { item -> // --- 修改：使用 item ---
                            // 单条记录
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectItem(item) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // --- 新增：显示类型的图标 ---
                                Icon(
                                    imageVector = if (item.type == SearchMode.TEXT) Icons.Default.Search else Icons.Default.Tag,
                                    contentDescription = if (item.type == SearchMode.TEXT) "文本搜索" else "ID搜索",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                // --- 新增结束 ---

                                Text(
                                    text = item.query, // --- 修改：使用 item.query ---
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
                                        contentDescription = "删除 ${item.query}", // --- 修改：使用 item.query ---
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