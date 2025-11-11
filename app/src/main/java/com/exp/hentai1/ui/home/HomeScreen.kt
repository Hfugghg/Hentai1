package com.exp.hentai1.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    // 为每个站点统一管理 LazyListState，以在切换后恢复滚动位置
    // 尽管 LazyListState() 看起来一样，但我们需要保证它们是每个站点的独立实例。
    // 在实际项目中，应确保 HomeViewModel 中的 loadedPages 类型 LoadedPage 已经被定义。
    val mainListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val rankingListStates = remember { HentaiOneSite.entries.associateWith { LazyListState() } }
    val currentMainListState = mainListStates[currentSite] ?: rememberLazyListState()
    val currentRankingListState = rankingListStates[currentSite] ?: rememberLazyListState()


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
                        BasicTextField(
                            value = searchText,
                            onValueChange = setSearchText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
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
                        val distinctRankingComics = uiState.rankingComics.distinctBy { it.id }

                        LazyColumn(
                            modifier = modifier.fillMaxSize(),
                            state = currentMainListState
                        ) {
                            // 每日排行 Section
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

                            // 每日更新 Section
                            latestUpdatesSection(
                                loadedPages = uiState.loadedPages,
                                onComicClick = onComicClick
                            )


                            // 加载更多或没有更多数据的提示
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

    // 页码输入弹窗
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