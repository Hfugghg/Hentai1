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

// <--- 修复: 新增 LoadedPage 数据类
data class LoadedPage(
    val page: Int,
    val comics: List<Comic>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val rankingComics: List<Comic> = emptyList(),
    // 替换为结构化的 loadedPages
    val loadedPages: List<LoadedPage> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1, // 当前加载的最新漫画页码 (用于追踪下次加载哪一页)
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
        // 检查 loadedPages 是否为空
        if (currentSiteState.isLoading && currentSiteState.loadedPages.isEmpty() && currentSiteState.rankingComics.isEmpty()) {
            fetchAllComics(newSite)
        }
    }

    fun fetchAllComics(site: HentaiOneSite) {
        viewModelScope.launch {
            // 清空 loadedPages
            _siteStates.getValue(site).update { it.copy(isLoading = true, error = null, loadedPages = emptyList()) }
            try {
                // 确保NetworkUtils已设置为正确的站点
                NetworkUtils.setSite(site)

                // 使用 async 并发获取数据
                val rankingDeferred = async { fetchRankingComics() }
                val latestDeferred = async { fetchLatestComics(1) } // 初始加载第一页

                val rankingResult = rankingDeferred.await()
                val latestResult = latestDeferred.await()

                // 检查是否有任何一个请求失败
                val firstError =
                    rankingResult.exceptionOrNull()?.message ?: latestResult.exceptionOrNull()?.message

                if (firstError != null) {
                    _siteStates.getValue(site).update { it.copy(isLoading = false, error = firstError) }
                } else {
                    val latestComicsList = latestResult.getOrNull() ?: emptyList()

                    // 将第一页数据结构化
                    val newLoadedPages = if (latestComicsList.isNotEmpty()) {
                        listOf(LoadedPage(page = 1, comics = latestComicsList))
                    } else {
                        emptyList()
                    }

                    _siteStates.getValue(site).update {
                        it.copy(
                            isLoading = false,
                            rankingComics = rankingResult.getOrNull() ?: emptyList(),
                            loadedPages = newLoadedPages, // <--- 使用 newLoadedPages
                            currentPage = 1,
                            canLoadMore = latestComicsList.isNotEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _siteStates.getValue(site)
                    .update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
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

                // 将新数据添加到 loadedPages 列表
                val newLoadedPage = if (newComics.isNotEmpty()) {
                    LoadedPage(page = nextPage, comics = newComics)
                } else null

                _siteStates.getValue(currentSiteValue).update { currentState ->
                    currentState.copy(
                        loadedPages = currentState.loadedPages + listOfNotNull(newLoadedPage), // <--- 追加新的 LoadedPage
                        currentPage = if (newComics.isNotEmpty()) nextPage else currentState.currentPage,
                        isLoadingMore = false,
                        canLoadMore = newComics.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _siteStates.getValue(currentSiteValue)
                    .update { it.copy(isLoadingMore = false, error = e.message ?: "加载更多失败") }
            }
        }
    }

    fun loadLatestComicsByPage(page: Int) {
        val currentSiteValue = _currentSite.value
        // 只清空 loadedPages，保持 rankingComics 不变
        _siteStates.getValue(currentSiteValue).update {
            it.copy(
                isLoading = true,
                error = null,
                loadedPages = emptyList(), // 清空已加载的页面数据
                currentPage = page // <--- 新增: 在开始加载前就更新 currentPage，确保加载指示器显示正确页码
            )
        }

        viewModelScope.launch {
            try {
                NetworkUtils.setSite(currentSiteValue)
                val result = fetchLatestComics(page)
                val newComics = result.getOrNull()

                if (newComics != null) {
                    val newLoadedPages = if (newComics.isNotEmpty()) {
                        listOf(LoadedPage(page = page, comics = newComics)) // <--- 创建新 LoadedPage 列表
                    } else emptyList()

                    _siteStates.getValue(currentSiteValue).update {
                        it.copy(
                            isLoading = false,
                            loadedPages = newLoadedPages, // <--- 替换为新列表
                            currentPage = page, // <--- 确认加载的页码
                            // 修正: 即使只加载了一页，如果该页有内容，理论上还可以加载下一页
                            canLoadMore = newComics.isNotEmpty()
                        )
                    }
                } else {
                    _siteStates.getValue(currentSiteValue).update {
                        it.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "加载指定页码失败",
                            canLoadMore = false // 加载失败，停止加载更多
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _siteStates.getValue(currentSiteValue).update {
                    it.copy(isLoading = false, error = e.message ?: "未知错误", canLoadMore = false)
                }
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