package com.exp.hentai1.ui.detail

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.exp.hentai1.data.*
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parsePayload6
import com.exp.hentai1.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class FavoriteFolderWithCount(
    val folder: FavoriteFolder,
    val count: Int
)

data class DetailUiState(
    val isLoading: Boolean = true,
    val comic: Comic? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val foldersWithCount: List<FavoriteFolderWithCount> = emptyList(),
    val coverImageModel: Any? = null // 新增：用于 AsyncImage 的模型，可以是 String (URL) 或 File (本地路径)
)

class DetailViewModel(application: Application, private val comicId: String, private val loadLocal: Boolean) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    private val favoriteFolderDao = AppDatabase.getDatabase(application).favoriteFolderDao()
    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()

    init {
        if (loadLocal) {
            loadLocalComicDetail()
        } else {
            fetchComicDetailFromNetwork()
        }
        checkIfFavorite()
        loadFavoriteFolders()
    }

    private fun loadLocalComicDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val localDownload = downloadDao.getDownloadById(comicId)
            if (localDownload != null) {
                val comic = convertDownloadToComic(localDownload)

                // 构造本地封面文件路径
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val comicDir = File(downloadDir, "Hentai1/$comicId")
                val coverFile = File(comicDir, "cover.webp")

                val imageModel: Any = if (coverFile.exists()) { // 将 Any? 改为 Any
                    coverFile // 使用 File 对象加载本地图片
                } else {
                    comic.coverUrl // 如果本地文件不存在，回退到远程 URL
                }

                _uiState.update { it.copy(isLoading = false, comic = comic, coverImageModel = imageModel) }
            } else {
                // 如果本地没有，则尝试从网络加载
                fetchComicDetailFromNetwork()
            }
        }
    }

    private fun convertDownloadToComic(download: Download): Comic {
        return Comic(
            id = download.comicId,
            title = download.title,
            coverUrl = download.coverUrl, // 这里的 coverUrl 仍然是远程 URL
            imageList = download.imageList,
            artists = download.artists,
            groups = download.groups,
            parodies = download.parodies,
            characters = download.characters,
            tags = download.tags,
            languages = download.languages,
            categories = download.categories,
            timestamp = download.timestamp // 假设时间戳是相关的
        )
    }

    private fun checkIfFavorite() {
        viewModelScope.launch {
            val favorite = favoriteDao.getById(comicId)
            _uiState.update { it.copy(isFavorite = favorite != null) }
        }
    }

    private fun loadFavoriteFolders() {
        viewModelScope.launch {
            favoriteFolderDao.getAllFavoriteFolders().collect { folders ->
                if (folders.isNotEmpty()) {
                    val countFlows = folders.map { folder ->
                        favoriteFolderDao.getFavoriteCountInFolder(folder.id)
                    }
                    combine(countFlows) { counts ->
                        folders.zip(counts).map { (folder, count) ->
                            FavoriteFolderWithCount(folder, count)
                        }
                    }.collect { foldersWithCount ->
                        _uiState.update { it.copy(foldersWithCount = foldersWithCount) }
                    }
                } else {
                    _uiState.update { it.copy(foldersWithCount = emptyList()) }
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                favoriteDao.delete(comicId)
                checkIfFavorite()
            }
        }
    }

    fun addToFavoriteFolder(folder: FavoriteFolder) {
        viewModelScope.launch {
            val comic = _uiState.value.comic ?: return@launch
            val favorite = Favorite(
                comicId = comicId,
                title = comic.title,
                timestamp = System.currentTimeMillis(),
                folderId = folder.id,
                language = NetworkUtils.getCurrentSite().name
            )
            favoriteDao.insert(favorite)
            checkIfFavorite()
        }
    }

    fun createNewFolderAndAddToFavorites(folderName: String) {
        viewModelScope.launch {
            val comic = _uiState.value.comic ?: return@launch
            val newFolderId = favoriteFolderDao.insert(FavoriteFolder(name = folderName))
            val favorite = Favorite(
                comicId = comicId,
                title = comic.title,
                timestamp = System.currentTimeMillis(),
                folderId = newFolderId,
                language = NetworkUtils.getCurrentSite().name
            )
            favoriteDao.insert(favorite)
            checkIfFavorite()
        }
    }

    private fun fetchComicDetailFromNetwork() { // 从 fetchComicDetail 重命名而来
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = NetworkUtils.detailUrl(comicId)
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                if (html != null && !html.startsWith("Error")) {
                    val comic = ComicDataParser.parseComicDetail(html)
                    _uiState.update { it.copy(isLoading = false, comic = comic, coverImageModel = comic?.coverUrl) } // 修复空安全问题
                } else {
                    _uiState.update { it.copy(isLoading = false, error = html ?: "未能获取到漫画详情") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "未知错误") }
            }
        }
    }

    fun downloadComic() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val url = NetworkUtils.viewerUrl(comicId)
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                if (html != null && !html.startsWith("Error")) {
                    val payloads = NextFParser.extractPayloadsFromHtml(html)
                    val payload6 = payloads["6"]
                    if (payload6 != null) {
                        val imageUrls = parsePayload6(payload6)
                        val currentComic = _uiState.value.comic
                        if (currentComic != null) {
                            val completeComic = currentComic.copy(imageList = imageUrls)

                            val downloadEntry = Download(
                                comicId = completeComic.id,
                                title = completeComic.title,
                                coverUrl = completeComic.coverUrl,
                                totalPages = completeComic.imageList.size,
                                timestamp = System.currentTimeMillis(),
                                imageList = completeComic.imageList,
                                status = DownloadStatus.DOWNLOADING,
                                artists = completeComic.artists,
                                groups = completeComic.groups,
                                parodies = completeComic.parodies,
                                characters = completeComic.characters,
                                tags = completeComic.tags,
                                languages = completeComic.languages,
                                categories = completeComic.categories
                            )
                            downloadDao.insert(downloadEntry)

                            startDownloadWorker(completeComic)
                        } else {
                             _uiState.update { it.copy(error = "漫画数据为空，无法开始下载") }
                        }
                    } else {
                        _uiState.update { it.copy(error = "未能为下载获取图片列表") }
                    }
                } else {
                    _uiState.update { it.copy(error = html ?: "未能获取下载数据") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(error = e.message ?: "下载准备时发生未知错误") }
            }
        }
    }

    private fun startDownloadWorker(comicToDownload: Comic) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_COMIC_ID, comicToDownload.id) // 修复 KEY_COMIC_ID 引用
            .build()

        // 为工作请求添加一个唯一标签，以便于观察
        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag(DownloadWorker::class.java.name) // 所有下载工作器的通用标签
            .addTag("id:${comicToDownload.id}") // 此漫画的特定标签
            .build()

        WorkManager.getInstance(getApplication()).enqueue(downloadWorkRequest)
    }
}
