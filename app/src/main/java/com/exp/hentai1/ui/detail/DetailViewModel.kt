package com.exp.hentai1.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val comic: Comic? = null,
    val error: String? = null
)

class DetailViewModel(application: Application, private val comicId: String) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        fetchComicDetail()
    }

    private fun fetchComicDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = NetworkUtils.detailUrl(comicId)
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                if (html != null && !html.startsWith("Error")) {
                    val comic = ComicDataParser.parseComicDetail(html)
                    _uiState.update { it.copy(isLoading = false, comic = comic) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = html ?: "未能获取到漫画详情") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
            }
        }
    }
}