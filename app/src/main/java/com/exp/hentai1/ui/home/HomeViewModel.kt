package com.exp.hentai1.ui.home

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit

data class LoadedPage(
    val page: Int,
    val comics: List<Comic>
)

enum class SearchMode {
    TEXT,
    ID
}

data class SearchHistoryItem(val query: String, val type: SearchMode)

data class HomeUiState(
    val isLoading: Boolean = true,
    val rankingComics: List<Comic> = emptyList(),
    val loadedPages: List<LoadedPage> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val mainListScrollIndex: Int = 0,
    val mainListScrollOffset: Int = 0,
    val rankingListScrollIndex: Int = 0,
    val rankingListScrollOffset: Int = 0
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _siteStates: MutableMap<HentaiOneSite, MutableStateFlow<HomeUiState>> =
        HentaiOneSite.entries.associateWith { MutableStateFlow(HomeUiState()) }.toMutableMap()

    private val _navigateToDetail = MutableSharedFlow<String>()
    val navigateToDetail: SharedFlow<String> = _navigateToDetail.asSharedFlow()

    private val _currentSite = MutableStateFlow(HentaiOneSite.MAIN)
    val currentSite: StateFlow<HentaiOneSite> = _currentSite

    val uiState: StateFlow<HomeUiState> = _currentSite.flatMapLatest { site ->
        _siteStates.getValue(site)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _siteStates.getValue(HentaiOneSite.MAIN).value
    )

    private val SEARCH_HISTORY_KEY = "search_history"
    private val MAX_SEARCH_HISTORY_SIZE = 30
    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences("hentai1_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory

    init {
        NetworkUtils.setSite(HentaiOneSite.MAIN)
        fetchAllComics(HentaiOneSite.MAIN)
        loadSearchHistory()
    }

    fun findAndNavigateToComicId(comicId: String) {
        val originalSite = _currentSite.value

        _siteStates.getValue(originalSite).update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val foundSite = NetworkUtils.findComicSite(getApplication(), comicId)

            if (foundSite != null) {
                NetworkUtils.setSite(foundSite)

                addSearchHistory(comicId, SearchMode.ID)

                _navigateToDetail.emit(comicId)

                _siteStates.getValue(originalSite).update { it.copy(isLoading = false) }
            } else {
                _siteStates.getValue(originalSite).update {
                    it.copy(isLoading = false, error = "未在任何站点找到漫画ID: $comicId")
                }
            }
        }
    }

    fun switchSite(newSite: HentaiOneSite) {
        if (_currentSite.value == newSite) return

        _currentSite.value = newSite
        NetworkUtils.setSite(newSite)

        val currentSiteState = _siteStates.getValue(newSite).value
        if (currentSiteState.isLoading && currentSiteState.loadedPages.isEmpty() && currentSiteState.rankingComics.isEmpty()) {
            fetchAllComics(newSite)
        }
    }

    fun fetchAllComics(site: HentaiOneSite) {
        viewModelScope.launch {
            _siteStates.getValue(site).update { it.copy(isLoading = true, error = null, loadedPages = emptyList()) }
            try {
                NetworkUtils.setSite(site)

                val rankingDeferred = async { fetchRankingComics() }
                val latestDeferred = async { fetchLatestComics(1) }

                val rankingResult = rankingDeferred.await()
                val latestResult = latestDeferred.await()

                val firstError =
                    rankingResult.exceptionOrNull()?.message ?: latestResult.exceptionOrNull()?.message

                if (firstError != null) {
                    _siteStates.getValue(site).update { it.copy(isLoading = false, error = firstError) }
                } else {
                    val latestComicsList = latestResult.getOrNull() ?: emptyList()

                    val newLoadedPages = if (latestComicsList.isNotEmpty()) {
                        listOf(LoadedPage(page = 1, comics = latestComicsList))
                    } else {
                        emptyList()
                    }

                    _siteStates.getValue(site).update {
                        it.copy(
                            isLoading = false,
                            rankingComics = rankingResult.getOrNull() ?: emptyList(),
                            loadedPages = newLoadedPages,
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
        val html = NetworkUtils.fetchHtml(getApplication(), NetworkUtils.latestUrl())
        if (html != null && !html.startsWith("Error")) {
            ComicDataParser.parseRankingComics(html)
        } else {
            throw Exception(html ?: "未能获取到排行内容")
        }
    }

    private suspend fun fetchLatestComics(page: Int): Result<List<Comic>> = runCatching {
        val url = NetworkUtils.latestUrl(page)
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
                NetworkUtils.setSite(currentSiteValue)

                val nextPage = currentUiState.currentPage + 1
                val newComics = fetchLatestComics(nextPage).getOrNull() ?: emptyList()

                val newLoadedPage = if (newComics.isNotEmpty()) {
                    LoadedPage(page = nextPage, comics = newComics)
                } else null

                _siteStates.getValue(currentSiteValue).update { currentState ->
                    currentState.copy(
                        loadedPages = currentState.loadedPages + listOfNotNull(newLoadedPage),
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
        _siteStates.getValue(currentSiteValue).update {
            it.copy(
                isLoading = true,
                error = null,
                loadedPages = emptyList(),
                currentPage = page
            )
        }

        viewModelScope.launch {
            try {
                NetworkUtils.setSite(currentSiteValue)
                val result = fetchLatestComics(page)
                val newComics = result.getOrNull()

                if (newComics != null) {
                    val newLoadedPages = if (newComics.isNotEmpty()) {
                        listOf(LoadedPage(page = page, comics = newComics))
                    } else emptyList()

                    _siteStates.getValue(currentSiteValue).update {
                        it.copy(
                            isLoading = false,
                            loadedPages = newLoadedPages,
                            currentPage = page,
                            canLoadMore = newComics.isNotEmpty()
                        )
                    }
                } else {
                    _siteStates.getValue(currentSiteValue).update {
                        it.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "加载指定页码失败",
                            canLoadMore = false
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

    private fun loadSearchHistory() {
        val json = sharedPreferences.getString(SEARCH_HISTORY_KEY, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
                _searchHistory.value = gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {
                _searchHistory.value = emptyList()
                saveSearchHistory(emptyList())
            }
        }
    }

    private fun saveSearchHistory(history: List<SearchHistoryItem>) {
        val json = gson.toJson(history)
        sharedPreferences.edit { putString(SEARCH_HISTORY_KEY, json) }
    }

    fun addSearchHistory(query: String, type: SearchMode) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return
        val newItem = SearchHistoryItem(trimmedQuery, type)

        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(newItem)
        currentHistory.add(0, newItem)

        if (currentHistory.size > MAX_SEARCH_HISTORY_SIZE) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        _searchHistory.value = currentHistory
        saveSearchHistory(currentHistory)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    fun removeSearchHistoryItem(item: SearchHistoryItem) {
        val currentHistory = _searchHistory.value.toMutableList()
        if (currentHistory.remove(item)) {
            _searchHistory.value = currentHistory
            saveSearchHistory(currentHistory)
        }
    }
}