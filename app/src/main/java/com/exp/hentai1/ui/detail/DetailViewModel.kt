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
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** 标签状态枚举 */
enum class TagStatus {
    NONE, // 无状态
    FAVORITE, // 收藏/关注
    BLOCKED // 拉黑
}

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
    val coverImageModel: Any? = null, // 新增：用于 AsyncImage 的模型，可以是 String (URL) 或 File (本地路径)
    val tagStatuses: Map<String, TagStatus> = emptyMap() // 新增：用于存储标签状态
)

class DetailViewModel(application: Application, private val comicId: String, private val loadLocal: Boolean) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    private val favoriteFolderDao = AppDatabase.getDatabase(application).favoriteFolderDao()
    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val userTagDao = AppDatabase.getDatabase(application).userTagDao()

    init {
        if (loadLocal) {
            loadLocalComicDetail()
        } else {
            fetchComicDetailFromNetwork()
        }
        checkIfFavorite()
        loadFavoriteFolders()
        loadUserTags() // 加载用户标签状态
    }

    private fun loadUserTags() {
        viewModelScope.launch {
            userTagDao.getAllTags().collect { userTags ->
                val statusMap = userTags.associate { userTag ->
                    userTag.id to when (userTag.type) {
                        1 -> TagStatus.FAVORITE
                        0 -> TagStatus.BLOCKED
                        else -> TagStatus.NONE
                    }
                }
                _uiState.update { it.copy(tagStatuses = statusMap) }
            }
        }
    }

    fun toggleTagStatus(tag: Tag, tagType: String, currentStatus: TagStatus) {
        viewModelScope.launch {
            val newStatus = when (currentStatus) {
                TagStatus.NONE -> TagStatus.FAVORITE
                TagStatus.FAVORITE -> TagStatus.BLOCKED
                TagStatus.BLOCKED -> TagStatus.NONE
            }

            if (newStatus == TagStatus.NONE) {
                userTagDao.delete(tag.id)
            } else {
                val type = if (newStatus == TagStatus.FAVORITE) 1 else 0
                val userTag = UserTag(
                    id = tag.id,
                    name = tag.name,
                    englishName = "", // 传空值
                    category = tagType,
                    timestamp = System.currentTimeMillis(),
                    type = type
                )
                userTagDao.insert(userTag)
            }

            // --- 新增：手动更新 UI 状态 ---
            _uiState.update { currentState ->
                val mutableMap = currentState.tagStatuses.toMutableMap()
                if (newStatus == TagStatus.NONE) {
                    mutableMap.remove(tag.id)
                } else {
                    mutableMap[tag.id] = newStatus
                }
                currentState.copy(tagStatuses = mutableMap)
            }
            // --- 结束新增 ---
        }
    }

    private fun loadLocalComicDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val localDownload = downloadDao.getDownloadById(comicId)
            if (localDownload != null) {
                val comic = convertDownloadToComic(localDownload)

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val comicDir = File(downloadDir, "Hentai1/$comicId")
                val coverFile = File(comicDir, "cover.webp")

                val imageModel: Any = if (coverFile.exists()) {
                    coverFile
                } else {
                    comic.coverUrl
                }

                _uiState.update { it.copy(isLoading = false, comic = comic, coverImageModel = imageModel) }
            } else {
                fetchComicDetailFromNetwork()
            }
        }
    }

    private fun convertDownloadToComic(download: Download): Comic {
        return Comic(
            id = download.comicId,
            title = download.title,
            coverUrl = download.coverUrl,
            imageList = download.imageList,
            artists = download.artists,
            groups = download.groups,
            parodies = download.parodies,
            characters = download.characters,
            tags = download.tags,
            languages = download.languages,
            categories = download.categories,
            timestamp = download.timestamp
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

    private fun fetchComicDetailFromNetwork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = NetworkUtils.detailUrl(comicId)
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                if (html != null && !html.startsWith("Error")) {
                    val comic = ComicDataParser.parseComicDetail(html)
                    _uiState.update { it.copy(isLoading = false, comic = comic, coverImageModel = comic?.coverUrl) }
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
            .putString(DownloadWorker.KEY_COMIC_ID, comicToDownload.id)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag(DownloadWorker::class.java.name)
            .addTag("id:${comicToDownload.id}")
            .build()

        WorkManager.getInstance(getApplication()).enqueue(downloadWorkRequest)
    }
}
