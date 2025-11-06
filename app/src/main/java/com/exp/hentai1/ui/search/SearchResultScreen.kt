package com.exp.hentai1.ui.search

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exp.hentai1.ui.home.LatestUpdateItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreen(
    query: String,
    onComicClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(application)
    )

    // 【修改】从 query 中解析出真正的搜索查询和标题
    val (searchQuery, title) = remember(query) {
        if (query.startsWith("tag:")) {
            val parts = query.split(":", limit = 4)
            if (parts.size == 4) {
                val tagType = parts[1]
                val tagId = parts[2]
                val tagName = parts[3]
                // 真正的搜索查询是 "type:id"
                val actualQuery = "$tagType:$tagId"
                // 标题是标签名
                val screenTitle = tagName
                actualQuery to screenTitle
            } else {
                // 格式不正确，按原样处理
                query to "搜索: $query"
            }
        } else {
            // 普通文本搜索
            query to "搜索: $query"
        }
    }

    LaunchedEffect(searchQuery) {
        viewModel.setQuery(searchQuery)
    }

    val uiState by viewModel.uiState.collectAsState()
    val selectedType = uiState.selectedType
    val searchState = uiState.searchResults[selectedType]!!

    val listStates = remember {
        SearchType.entries.associateWith { LazyListState() }
    }
    val listState = listStates[selectedType]!!

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= listState.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged()
            .collect { 
                viewModel.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(title) } // 【修改】使用新的 title
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
                SearchType.entries.forEach { searchType ->
                    Button(
                        onClick = { viewModel.selectSearchType(searchType) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == searchType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (selectedType == searchType) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(text = searchType.displayName)
                    }
                }
            }

            when {
                searchState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                searchState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "发生错误: ${searchState.error}",
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
                        items(searchState.comics) { comic ->
                            LatestUpdateItem(
                                comic = comic,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onComicClick = onComicClick
                            )
                        }

                        item {
                            if (searchState.isLoadingMore) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Text(text = "正在加载更多，请稍候...")
                                }
                            } else if (!searchState.canLoadMore && searchState.comics.isNotEmpty()) {
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
