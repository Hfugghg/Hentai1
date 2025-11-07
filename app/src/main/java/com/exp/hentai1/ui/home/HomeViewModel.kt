package com.exp.hentai1.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val rankingComics: List<Comic> = emptyList(),
    val latestComics: List<Comic> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1, // 当前加载的最新漫画页码
    val isLoadingMore: Boolean = false, // 是否正在加载更多最新漫画
    val canLoadMore: Boolean = true, // 是否还有更多最新漫画可以加载
    val mainListScrollIndex: Int = 0, // 主列表滚动位置：第一个可见项的索引
    val mainListScrollOffset: Int = 0, // 主列表滚动位置：第一个可见项的偏移量
    val rankingListScrollIndex: Int = 0, // 排行榜列表滚动位置：第一个可见项的索引
    val rankingListScrollOffset: Int = 0 // 排行榜列表滚动位置：第一个可见项的偏移量
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // 为每个站点维护一个独立的UI状态
    private val _siteStates: MutableMap<HentaiOneSite, MutableStateFlow<HomeUiState>> = 
        HentaiOneSite.entries.associateWith { MutableStateFlow(HomeUiState()) }.toMutableMap()

    // 当前选中的站点
    private val _currentSite = MutableStateFlow(HentaiOneSite.MAIN)
    val currentSite: StateFlow<HentaiOneSite> = _currentSite

    // 暴露给UI的StateFlow，它会根据当前站点动态更新
    val uiState: StateFlow<HomeUiState> = _currentSite.flatMapLatest { site ->
        _siteStates.getValue(site)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _siteStates.getValue(HentaiOneSite.MAIN).value // 初始值
    )

    init {
        // 设置初始站点给NetworkUtils
        NetworkUtils.setSite(HentaiOneSite.MAIN)
        // 为初始站点加载数据
        fetchAllComics(HentaiOneSite.MAIN)
    }

    // 切换站点的方法
    fun switchSite(newSite: HentaiOneSite) {
        if (_currentSite.value == newSite) return

        _currentSite.value = newSite
        NetworkUtils.setSite(newSite) // 更新NetworkUtils的站点

        // 如果新站点的数据尚未加载，则触发加载
        val currentSiteState = _siteStates.getValue(newSite).value
        if (currentSiteState.isLoading && currentSiteState.rankingComics.isEmpty() && currentSiteState.latestComics.isEmpty()) {
            fetchAllComics(newSite)
        }
    }

    fun fetchAllComics(site: HentaiOneSite) {
        viewModelScope.launch {
            _siteStates.getValue(site).update { it.copy(isLoading = true, error = null) }
            try {
                // 确保NetworkUtils已设置为正确的站点
                NetworkUtils.setSite(site)

                // 使用 async 并发获取数据
                val rankingDeferred = async { fetchRankingComics() }
                val latestDeferred = async { fetchLatestComics(1) } // 初始加载第一页

                val rankingResult = rankingDeferred.await()
                val latestResult = latestDeferred.await()

                // 检查是否有任何一个请求失败
                val firstError = rankingResult.exceptionOrNull()?.message ?: latestResult.exceptionOrNull()?.message

                if (firstError != null) {
                    _siteStates.getValue(site).update { it.copy(isLoading = false, error = firstError) }
                } else {
                    val latestComicsList = latestResult.getOrNull() ?: emptyList()
                    _siteStates.getValue(site).update {
                        it.copy(
                            isLoading = false,
                            rankingComics = rankingResult.getOrNull() ?: emptyList(),
                            latestComics = latestComicsList,
                            currentPage = 1,
                            canLoadMore = latestComicsList.isNotEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _siteStates.getValue(site).update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
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
        val currentSiteValue = _currentSite.value
        val currentUiState = _siteStates.getValue(currentSiteValue).value

        if (currentUiState.isLoadingMore || !currentUiState.canLoadMore) {
            return
        }

        _siteStates.getValue(currentSiteValue).update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                // 确保NetworkUtils已设置为正确的站点
                NetworkUtils.setSite(currentSiteValue)

                val nextPage = currentUiState.currentPage + 1
                val newComics = fetchLatestComics(nextPage).getOrNull() ?: emptyList()

                _siteStates.getValue(currentSiteValue).update { currentState ->
                    currentState.copy(
                        latestComics = currentState.latestComics + newComics,
                        currentPage = nextPage,
                        isLoadingMore = false,
                        canLoadMore = newComics.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _siteStates.getValue(currentSiteValue).update { it.copy(isLoadingMore = false, error = e.message ?: "加载更多失败") }
            }
        }
    }

    fun updateMainListScrollPosition(site: HentaiOneSite, index: Int, offset: Int) {
        _siteStates.getValue(site).update {
            it.copy(mainListScrollIndex = index, mainListScrollOffset = offset)
        }
    }

    fun updateRankingListScrollPosition(site: HentaiOneSite, index: Int, offset: Int) {
        _siteStates.getValue(site).update {
            it.copy(rankingListScrollIndex = index, rankingListScrollOffset = offset)
        }
    }
}
