package com.exp.hentai1.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parsePayload7
import com.exp.hentai1.data.remote.parser.parsePayload7ForTagSearch // 【修改】导入正确的顶层解析函数
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchType(val displayName: String) {
    Latest("最新"),
    Popular("人气")
}

data class SearchCategoryState(
    val isLoading: Boolean = true,
    val comics: List<Comic> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)

data class SearchUiState(
    val selectedType: SearchType = SearchType.Latest,
    val searchResults: Map<SearchType, SearchCategoryState> = SearchType.entries.associateWith { SearchCategoryState() }
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var currentQuery: String = ""

    fun onScrollPositionChanged(type: SearchType, index: Int, offset: Int) {
        _uiState.update { currentState ->
            val categoryState = currentState.searchResults[type] ?: return@update currentState
            val updatedCategoryState = categoryState.copy(
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = offset
            )
            val updatedSearchResults = currentState.searchResults.toMutableMap().apply {
                this[type] = updatedCategoryState
            }
            currentState.copy(searchResults = updatedSearchResults)
        }
    }

    fun setQuery(query: String) {
        if (currentQuery == query) return
        currentQuery = query
        // Reset states for new query
        _uiState.update {
            it.copy(
                selectedType = SearchType.Latest,
                searchResults = SearchType.entries.associateWith { SearchCategoryState() }
            )
        }
        loadSearchResults(SearchType.Latest)
        loadSearchResults(SearchType.Popular) // Load both initially
    }

    fun selectSearchType(type: SearchType) {
        if (_uiState.value.selectedType == type) return
        _uiState.update { it.copy(selectedType = type) }

        val newState = _uiState.value.searchResults[type]
        if (newState != null && newState.comics.isEmpty() && newState.error == null) {
            loadSearchResults(type)
        }
    }

    fun loadMore() {
        val type = _uiState.value.selectedType
        val state = _uiState.value.searchResults[type] ?: return

        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        _uiState.update { currentState ->
            val newSearchResults = currentState.searchResults.toMutableMap()
            newSearchResults[type] = state.copy(isLoadingMore = true)
            currentState.copy(searchResults = newSearchResults)
        }
        loadSearchResults(type, page = state.currentPage + 1)
    }

    private fun loadSearchResults(type: SearchType, page: Int = 1) {
        if (currentQuery.isBlank()) return // Don't search if query is empty

        viewModelScope.launch(Dispatchers.IO) {
            if (page == 1) {
                _uiState.update { currentState ->
                    val state = currentState.searchResults[type]!!
                    val newSearchResults = currentState.searchResults.toMutableMap()
                    newSearchResults[type] = state.copy(isLoading = true)
                    currentState.copy(searchResults = newSearchResults)
                }
            }
            try {
                val isTagSearch = currentQuery.contains(":")
                val url = if (isTagSearch) {
                    val parts = currentQuery.split(":", limit = 2)
                    val tagType = parts[0]
                    val tagId = parts[1]
                    val isPopular = type == SearchType.Popular

                    when (tagType) {
                        "artists" -> NetworkUtils.artistsUrl(tagId.toInt(), popular = isPopular, page = page)
                        "groups" -> NetworkUtils.groupsUrl(tagId.toInt(), popular = isPopular, page = page)
                        "parodies" -> NetworkUtils.parodiesUrl(tagId.toInt(), popular = isPopular, page = page)
                        "characters" -> NetworkUtils.charactersUrl(tagId.toInt(), popular = isPopular, page = page)
                        "tags" -> NetworkUtils.tagsUrl(tagId.toInt(), popular = isPopular, page = page)
                        "languages" -> NetworkUtils.languagesUrl(tagId.toInt(), popular = isPopular, page = page)
                        "categories" -> NetworkUtils.categoriesUrl(tagId.toInt(), popular = isPopular, page = page)
                        else -> NetworkUtils.searchUrl(currentQuery, popular = type == SearchType.Popular, page = page)
                    }
                } else {
                    NetworkUtils.searchUrl(currentQuery, popular = type == SearchType.Popular, page = page)
                }

                val html = NetworkUtils.fetchHtml(getApplication(), url)
                val newComics = html?.let {
                    val payloads = NextFParser.extractPayloadsFromHtml(it)
                    payloads["7"]?.let { payload ->
                        // 【修改】根据是否是标签搜索，调用不同的顶层解析函数
                        if (isTagSearch) {
                            parsePayload7ForTagSearch(payload)
                        } else {
                            parsePayload7(payload)
                        }
                    } ?: emptyList()
                } ?: emptyList()

                _uiState.update { currentState ->
                    val state = currentState.searchResults[type]!!
                    val oldComics = if (page == 1) emptyList() else state.comics
                    val updatedState = state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        comics = oldComics + newComics,
                        currentPage = page,
                        canLoadMore = newComics.isNotEmpty(),
                        error = null
                    )
                    val newSearchResults = currentState.searchResults.toMutableMap()
                    newSearchResults[type] = updatedState
                    currentState.copy(searchResults = newSearchResults)
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    val state = currentState.searchResults[type]!!
                    val updatedState = state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message
                    )
                    val newSearchResults = currentState.searchResults.toMutableMap()
                    newSearchResults[type] = updatedState
                    currentState.copy(searchResults = newSearchResults)
                }
            }
        }
    }
}
