package com.exp.hentai1.ui.reader

import android.app.Application
import android.content.ContentResolver // <-- NEW
import android.content.ContentUris // <-- NEW
import android.content.ContentValues
import android.net.Uri // <-- NEW
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import com.exp.hentai1.data.remote.parser.parsePayload6
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /**
     * 将指定的图片URL（网络或本地）保存到图库。
     * @param imageUrl 图片的 URL (http/https/file://)
     * @param onComplete 回调函数，提供操作结果信息
     */
    fun saveImageToGallery(imageUrl: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            // <-- MODIFIED BLOCK: 获取页码和 ComicID -->
            val imageUrls = _uiState.value.imageUrls ?: emptyList()
            val pageIndex = imageUrls.indexOf(imageUrl)
            val pageNumber = if (pageIndex != -1) pageIndex + 1 else 0 // 1-based index

            if (pageNumber == 0) {
                onComplete("保存失败: 无法确定页码")
                return@launch
            }
            // <-- END MODIFIED BLOCK -->

            if (imageUrl.startsWith("file://")) {
                // 如果是本地文件
                val path = imageUrl.substring("file://".length)
                val file = File(path)
                if (file.exists()) {
                    // <-- MODIFIED: 传入 comicId 和 pageNumber -->
                    val success = saveLocalFileToGallery(file, comicId, pageNumber)
                    val fileName = "${comicId}_$pageNumber.${file.extension}"
                    onComplete(if (success) "图片 $fileName 已保存" else "保存失败: 无法写入媒体库")
                } else {
                    onComplete("保存失败: 本地文件不存在")
                }
            } else {
                // 如果是网络图片
                // <-- MODIFIED: 传入 comicId 和 pageNumber -->
                val (success, fileName) = saveRemoteImageToGallery(imageUrl, comicId, pageNumber)
                onComplete(if (success) "图片 $fileName 已保存" else "保存失败: 下载或写入媒体库失败")
            }
        }
    }

    // --- 辅助函数：图库保存逻辑 ---

    /**
     * 将本地文件复制到 MediaStore (图库)
     */
    private suspend fun saveLocalFileToGallery(file: File, comicId: String, pageNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val application = getApplication<Application>()
        val contentResolver = application.contentResolver

        val extension = file.extension
        val fileName = "${comicId}_$pageNumber.$extension"
        // <-- 优化: 确保相对路径末尾有斜杠，这对于查询和插入的一致性至关重要 -->
        val relativePath = Environment.DIRECTORY_PICTURES + File.separator + "Hentai1" + File.separator

        // --- 核心修改：执行 "删除并替换" 策略 ---
        val existingUri = findMediaUri(contentResolver, fileName, relativePath)
        existingUri?.let {
            try {
                // 1. 如果找到，先删除旧条目
                contentResolver.delete(it, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "无法删除现有媒体存储条目: $it", e)
                // 即使删除失败，也继续尝试插入，MediaStore 可能会处理冲突
            }
        }
        // --- 结束修改 ---

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, getMimeType(extension))
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1) // 标记为正在写入
        }

        // 2. 插入新条目
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        var success = false
        uri?.let { imageUri ->
            try {
                // 3. 将数据写入新条目 (使用 "w" - write 模式)
                contentResolver.openOutputStream(imageUri, "w")?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 4. 标记为写入完成
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "保存本地文件到图库失败", e)
                // 写入失败时，清理掉新插入的空条目
                try { contentResolver.delete(imageUri, null, null) } catch (eDelete: Exception) {}
            }
        }
        return@withContext success
    }

    /**
     * 下载网络图片并保存到 MediaStore (图库)
     */
    private suspend fun saveRemoteImageToGallery(imageUrl: String, comicId: String, pageNumber: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val application = getApplication<Application>()
        val client = OkHttpClient()
        val request = Request.Builder().url(imageUrl).build()

        var success = false
        var fileName = "" // 默认文件名

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "下载图片失败: $imageUrl, code: ${response.code}")
                    return@withContext Pair(false, "")
                }

                val body = response.body!!
                val extension = getFileExtensionFromUrl(imageUrl, body.contentType()?.subtype)
                val mimeType = body.contentType()?.toString() ?: getMimeType(extension)

                fileName = "${comicId}_$pageNumber.$extension"
                // <-- 优化: 确保相对路径末尾有斜杠 -->
                val relativePath = Environment.DIRECTORY_PICTURES + File.separator + "Hentai1" + File.separator
                val contentResolver = application.contentResolver

                // --- 核心修改：执行 "删除并替换" 策略 ---
                val existingUri = findMediaUri(contentResolver, fileName, relativePath)
                existingUri?.let {
                    try {
                        // 1. 如果找到，先删除旧条目
                        contentResolver.delete(it, null, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete existing media store entry: $it", e)
                    }
                }
                // --- 结束修改 ---

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                // 2. 插入新条目
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { imageUri ->
                    try {
                        // 3. 将数据写入新条目 (使用 "w" - write 模式)
                        contentResolver.openOutputStream(imageUri, "w")?.use { outputStream ->
                            body.byteStream().copyTo(outputStream)
                        }

                        // 4. 标记为写入完成
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(imageUri, contentValues, null, null)
                        success = true
                    } catch (e: Exception) {
                        Log.e(TAG, "写入媒体库失败", e)
                        // 写入失败时，清理掉新插入的空条目
                        try { contentResolver.delete(imageUri, null, null) } catch (eDelete: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载网络图片失败", e)
        }
        return@withContext Pair(success, fileName)
    }

    // <-- NEW: 辅助函数，用于查找 MediaStore 中的现有文件 -->
    /**
     * 查找具有给定名称和相对路径的 MediaStore URI。
     */
    private fun findMediaUri(contentResolver: ContentResolver, fileName: String, relativePath: String): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relativePath)
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    return ContentUris.withAppendedId(queryUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询 MediaStore 失败", e)
        }
        return null // 未找到
    }

    // 简单的 MIME/Extension 推断
    private fun getMimeType(extension: String?): String {
        return when (extension?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg" // 默认
        }
    }

    private fun getFileExtensionFromUrl(url: String, mimeSubtype: String?): String {
        val urlLower = url.lowercase()
        return when {
            urlLower.endsWith(".jpg") -> "jpg"
            urlLower.endsWith(".png") -> "png"
            urlLower.endsWith(".gif") -> "gif"
            urlLower.endsWith(".webp") -> "webp"
            mimeSubtype != null && mimeSubtype.contains("jpeg") -> "jpg"
            mimeSubtype != null && mimeSubtype.contains("png") -> "png"
            mimeSubtype != null && mimeSubtype.contains("gif") -> "gif"
            mimeSubtype != null && mimeSubtype.contains("webp") -> "webp"
            else -> "jpg" // 默认扩展名
        }
    }
}