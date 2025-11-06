package com.exp.hentai1.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val rankingComics: List<Comic> = emptyList(),
    val latestComics: List<Comic> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1, // 当前加载的最新漫画页码
    val isLoadingMore: Boolean = false, // 是否正在加载更多最新漫画
    val canLoadMore: Boolean = true // 是否还有更多最新漫画可以加载
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        fetchAllComics()
    }

    fun fetchAllComics() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                // 使用 async 并发获取数据，就像女仆同时为主人准备茶点和暖床，效率更高
                val rankingDeferred = async { fetchRankingComics() }
                val latestDeferred = async { fetchLatestComics(1) } // 初始加载第一页

                val rankingResult = rankingDeferred.await()
                val latestResult = latestDeferred.await()

                // 检查是否有任何一个请求失败
                val firstError = rankingResult.exceptionOrNull()?.message ?: latestResult.exceptionOrNull()?.message

                if (firstError != null) {
                    _uiState.value = HomeUiState(isLoading = false, error = firstError)
                } else {
                    val latestComicsList = latestResult.getOrNull() ?: emptyList()
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        rankingComics = rankingResult.getOrNull() ?: emptyList(),
                        latestComics = latestComicsList,
                        currentPage = 1,
                        canLoadMore = latestComicsList.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = HomeUiState(isLoading = false, error = e.message ?: "未知错误")
            }
        }
    }

    private suspend fun fetchRankingComics(): Result<List<Comic>> = runCatching {
        // 调用每日排行URL和解析器
        val html = NetworkUtils.fetchHtml(getApplication(), NetworkUtils.latestUrl())
        if (html != null && !html.startsWith("Error")) {
            ComicDataParser.parseRankingComics(html)
        } else {
            throw Exception(html ?: "未能获取到排行内容")
        }
    }

    private suspend fun fetchLatestComics(page: Int): Result<List<Comic>> = runCatching {
        val url = NetworkUtils.latestUrl(page) // 使用 NetworkUtils.latestUrl(page) 来获取 URL
        val html = NetworkUtils.fetchHtml(getApplication(), url)
        if (html != null && !html.startsWith("Error")) {
            ComicDataParser.parseLatestComics(html)
        } else {
            throw Exception(html ?: "未能获取到更新内容")
        }
    }

    fun loadMoreLatestComics() {
        if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val nextPage = _uiState.value.currentPage + 1
                val newComics = fetchLatestComics(nextPage).getOrNull() ?: emptyList()

                _uiState.update { currentState ->
                    currentState.copy(
                        latestComics = currentState.latestComics + newComics,
                        currentPage = nextPage,
                        isLoadingMore = false,
                        canLoadMore = newComics.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoadingMore = false, error = e.message ?: "加载更多失败") }
            }
        }
    }
}
