package com.exp.hentai1.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parsePayload6
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val imageUrls: List<String>? = null,
    val error: String? = null
)

class ReaderViewModel(application: Application, private val comicId: String) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    init {
        fetchReaderContent()
    }

    private fun fetchReaderContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = NetworkUtils.viewerUrl(comicId)
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                if (html != null && !html.startsWith("Error")) {
                    // Correctly extract payloads from the HTML
                    val payloads = NextFParser.extractPayloadsFromHtml(html)
                    // Get the specific payload for ID "6"
                    val payload6 = payloads["6"]
                    if (payload6 != null) {
                        val imageUrls = parsePayload6(payload6)
                        _uiState.update { it.copy(isLoading = false, imageUrls = imageUrls) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "未能找到图片链接数据 (Payload 6 is missing)") }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = html ?: "未能获取到阅读链接") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
            }
        }
    }
}