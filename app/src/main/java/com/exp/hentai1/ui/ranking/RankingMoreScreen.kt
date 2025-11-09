package com.exp.hentai1.ui.ranking

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingMoreScreen(
    onComicClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: RankingMoreViewModel = viewModel(
        factory = RankingMoreViewModelFactory(application)
    )
    val uiState by viewModel.uiState.collectAsState()
    val selectedType = uiState.selectedType
    val rankingState = uiState.rankings[selectedType]!!

    val listStates = RankingType.entries.associateWith { rememberLazyListState() }
    val listState = listStates[selectedType]!!

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= listState.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged()
            .collect {
                viewModel.loadMore()
            }
    }

    LaunchedEffect(selectedType) {
        val scrollState = uiState.scrollStates[selectedType]
        if (scrollState != null) {
            coroutineScope.launch {
                listState.scrollToItem(scrollState.index, scrollState.offset)
            }
        }
    }

    DisposableEffect(selectedType) {
        onDispose {
            val scrollState = listState.firstVisibleItemIndex
            val scrollOffset = listState.firstVisibleItemScrollOffset
            viewModel.updateScrollState(selectedType, scrollState, scrollOffset)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("排行榜") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RankingType.entries.forEach { rankingType ->
                    Button(
                        onClick = { viewModel.selectRankingType(rankingType) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == rankingType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (selectedType == rankingType) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(text = rankingType.displayName)
                    }
                }
            }

            when {
                rankingState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                rankingState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "发生错误: ${rankingState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(rankingState.comics) { comic ->
                            ComicCard(
                                comic = comic,
                                style = ComicCardStyle.LIST,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onComicClick = onComicClick
                            )
                        }

                        item {
                            if (rankingState.isLoadingMore) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Text(text = "正在加载更多，请稍候...")
                                }
                            } else if (!rankingState.canLoadMore && rankingState.comics.isNotEmpty()) {
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