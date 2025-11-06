package com.exp.hentai1.ui.ranking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parsePayload7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RankingType(val displayName: String) {
    Daily("每日"),
    Weekly("每周"),
    Monthly("每月"),
    AllTime("全部")
}

data class RankingCategoryState(
    val isLoading: Boolean = true,
    val comics: List<Comic> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false
)

data class RankingUiState(
    val selectedType: RankingType = RankingType.Daily,
    val rankings: Map<RankingType, RankingCategoryState> = RankingType.entries.associateWith { RankingCategoryState() }
)

class RankingMoreViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        loadRanking(RankingType.Daily)
    }

    fun selectRankingType(type: RankingType) {
        if (_uiState.value.selectedType == type) return
        _uiState.update { it.copy(selectedType = type) }

        val newState = _uiState.value.rankings[type]
        if (newState != null && newState.comics.isEmpty() && newState.error == null) {
            loadRanking(type)
        }
    }

    fun loadMore() {
        val type = _uiState.value.selectedType
        val state = _uiState.value.rankings[type] ?: return

        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        _uiState.update { currentState ->
            val newRankings = currentState.rankings.toMutableMap()
            newRankings[type] = state.copy(isLoadingMore = true)
            currentState.copy(rankings = newRankings)
        }
        loadRanking(type, page = state.currentPage + 1)
    }

    private fun loadRanking(type: RankingType, page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            if (page == 1) {
                _uiState.update { currentState ->
                    val state = currentState.rankings[type]!!
                    val newRankings = currentState.rankings.toMutableMap()
                    newRankings[type] = state.copy(isLoading = true)
                    currentState.copy(rankings = newRankings)
                }
            }
            try {
                val url = when (type) {
                    RankingType.Daily -> NetworkUtils.daylyRankUrl(page)
                    RankingType.Weekly -> NetworkUtils.weeklyRankUrl(page)
                    RankingType.Monthly -> NetworkUtils.monthlyRankUrl(page)
                    RankingType.AllTime -> NetworkUtils.allTimeRankUrl(page)
                }
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                val newComics = html?.let {
                    val payloads = NextFParser.extractPayloadsFromHtml(it)
                    payloads["7"]?.let { payload ->
                        parsePayload7(payload)
                    } ?: emptyList()
                } ?: emptyList()

                _uiState.update { currentState ->
                    val state = currentState.rankings[type]!!
                    val oldComics = if (page == 1) emptyList() else state.comics
                    val updatedState = state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        comics = oldComics + newComics,
                        currentPage = page,
                        canLoadMore = newComics.isNotEmpty(),
                        error = null
                    )
                    val newRankings = currentState.rankings.toMutableMap()
                    newRankings[type] = updatedState
                    currentState.copy(rankings = newRankings)
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    val state = currentState.rankings[type]!!
                    val updatedState = state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message
                    )
                    val newRankings = currentState.rankings.toMutableMap()
                    newRankings[type] = updatedState
                    currentState.copy(rankings = newRankings)
                }
            }
        }
    }
}