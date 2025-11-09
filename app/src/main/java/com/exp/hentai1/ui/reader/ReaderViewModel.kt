package com.exp.hentai1.ui.reader

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parsePayload6
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReaderUiState(
    val isLoading: Boolean = true,
    val imageUrls: List<String>? = null,
    val error: String? = null
)

class ReaderViewModel(application: Application, private val comicId: String, private val isLocal: Boolean) : AndroidViewModel(application) { // 接受 isLocal 参数

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()

    init {
        fetchReaderContent()
    }

    private fun fetchReaderContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (isLocal) {
                    // 如果明确指定是本地加载，则只尝试本地加载
                    val localImageUrls = getLocalImageUrls(comicId)
                    if (localImageUrls.isNotEmpty()) {
                        _uiState.update { it.copy(isLoading = false, imageUrls = localImageUrls) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "本地漫画文件未找到或不完整。") }
                    }
                } else {
                    // 如果不是本地加载，或者本地加载失败，则尝试网络加载
                    val url = NetworkUtils.viewerUrl(comicId)
                    val html = NetworkUtils.fetchHtml(getApplication(), url)
                    if (html != null && !html.startsWith("Error")) {
                        val payloads = NextFParser.extractPayloadsFromHtml(html)
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
            }
        }
    }

    /**
     * 获取本地已下载漫画的图片文件URI列表。
     * 图片文件按数字名称排序，并排除封面。
     */
    private fun getLocalImageUrls(comicId: String): List<String> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val comicBaseDir = File(downloadDir, "Hentai1")
        val comicDir = File(comicBaseDir, comicId)

        if (!comicDir.exists() || !comicDir.isDirectory) {
            return emptyList()
        }

        val imageFiles = comicDir.listFiles { _, name ->
            // 过滤出图片文件，并排除封面文件 "cover.webp"
            (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")) && name != "cover.webp"
        }

        return imageFiles
            ?.sortedWith(compareBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }) // 按文件名中的数字部分排序
            ?.map { "file://" + it.absolutePath } // 转换为 Coil 可识别的 file:// URI
            ?: emptyList()
    }
}