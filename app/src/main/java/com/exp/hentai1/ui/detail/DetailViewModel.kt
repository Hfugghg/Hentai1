package com.exp.hentai1.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.Favorite
import com.exp.hentai1.data.FavoriteFolder
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteFolderWithCount(
    val folder: FavoriteFolder,
    val count: Int
)

data class DetailUiState(
    val isLoading: Boolean = true,
    val comic: Comic? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val foldersWithCount: List<FavoriteFolderWithCount> = emptyList()
)

class DetailViewModel(application: Application, private val comicId: String) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    private val favoriteFolderDao = AppDatabase.getDatabase(application).favoriteFolderDao()

    init {
        fetchComicDetail()
        checkIfFavorite()
        loadFavoriteFolders()
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
                folderId = folder.id
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
                folderId = newFolderId
            )
            favoriteDao.insert(favorite)
            checkIfFavorite()
        }
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