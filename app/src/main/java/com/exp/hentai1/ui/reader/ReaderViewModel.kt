package com.exp.hentai1.ui.reader

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
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
                val localImageUrls = getLocalImageUrls(comicId)
                if (localImageUrls.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = false, imageUrls = localImageUrls) }
                } else {
                    // 如果本地没有，则从网络获取
                    val url = NetworkUtils.viewerUrl(comicId)
                    val html = NetworkUtils.fetchHtml(getApplication(), url)
                    if (html != null && !html.startsWith("Error")) {
                        val payloads = NextFParser.extractPayloadsFromHtml(html)
                        val payload6 = payloads["6"]
                        var imageUrls: List<String> = emptyList()

                        // 尝试 Next.js Payload 解析
                        if (payload6 != null) {
                            try {
                                imageUrls = parsePayload6(payload6)
                            } catch (e: Exception) {
                                Log.e(TAG, "parsePayload6 失败，尝试 Jsoup 备用", e)
                                // 失败后 imageUrls 保持 emptyList()
                            }
                        }

                        // --- Jsoup 备用逻辑 ---
                        if (imageUrls.isEmpty()) {
                            Log.w(TAG, "Next.js Payload 6 丢失或解析失败，回退到 Jsoup 解析。")
                            // 假设有一个 Jsoup 备用函数，暂时还没做TODO
//                            imageUrls = parseImagesFromHtmlStructure(html)
                        }
                        // -----------------------

                        if (imageUrls.isNotEmpty()) {
                            _uiState.update { it.copy(isLoading = false, imageUrls = imageUrls) }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = "未能找到图片链接数据 (Payload 6 & Jsoup 备用均失败)") }
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
