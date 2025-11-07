package com.exp.hentai1.ui.favorites

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.Favorite
import com.exp.hentai1.data.FavoriteFolder
import com.exp.hentai1.data.remote.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 新的数据结构，用于表示一个收藏夹分组
data class FavoriteGroup(
    val folder: FavoriteFolder?,
    val comics: List<Comic>,
    var isExpanded: Boolean = false
)

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favoriteGroups: List<FavoriteGroup> = emptyList(),
    val error: String? = null,
    // 管理模式状态
    val isManagementMode: Boolean = false,
    // 选中的漫画ID集合
    val selectedComicIds: Set<String> = emptySet(),
    // 滚动位置
    val listScrollPosition: Int = 0,
    val listScrollOffset: Int = 0
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    private val favoriteFolderDao = AppDatabase.getDatabase(application).favoriteFolderDao()

    fun loadFavorites() {
        viewModelScope.launch {
            val currentExpansionState = _uiState.value.favoriteGroups.associate { (it.folder?.id) to it.isExpanded }
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val folders = favoriteFolderDao.getAllFavoriteFolders().first()
                val allFavorites = favoriteDao.getAll()
                val favoritesByFolderId = allFavorites.groupBy { it.folderId }

                val groups = folders.map { folder ->
                    val comicsInFolder = (favoritesByFolderId[folder.id] ?: emptyList())
                        .map(::mapFavoriteToComic)
                        .sortedByDescending { it.timestamp }
                    FavoriteGroup(
                        folder = folder,
                        comics = comicsInFolder,
                        isExpanded = currentExpansionState[folder.id] ?: false
                    )
                }

                val uncategorizedComics = (favoritesByFolderId[null] ?: emptyList())
                    .map(::mapFavoriteToComic)
                    .sortedByDescending { it.timestamp }

                val allGroups = mutableListOf<FavoriteGroup>()
                if (uncategorizedComics.isNotEmpty()) {
                    allGroups.add(
                        FavoriteGroup(
                            folder = null,
                            comics = uncategorizedComics,
                            isExpanded = currentExpansionState[null] ?: true // 默认展开未分类
                        )
                    )
                }
                allGroups.addAll(groups)

                _uiState.update { it.copy(isLoading = false, favoriteGroups = allGroups) }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载收藏失败") }
            }
        }
    }

    // --- 管理模式相关方法 ---

    fun toggleManagementMode() {
        val isInMgmtMode = _uiState.value.isManagementMode
        _uiState.update {
            it.copy(
                isManagementMode = !isInMgmtMode,
                // 退出管理模式时清空选项
                selectedComicIds = if (isInMgmtMode) emptySet() else it.selectedComicIds
            )
        }
    }

    fun toggleComicSelection(comicId: String) {
        val currentSelection = _uiState.value.selectedComicIds.toMutableSet()
        if (currentSelection.contains(comicId)) {
            currentSelection.remove(comicId)
        } else {
            currentSelection.add(comicId)
        }
        _uiState.update { it.copy(selectedComicIds = currentSelection) }
    }

    fun toggleSelectAllInFolder(folderId: Long?) {
        val group = _uiState.value.favoriteGroups.find { it.folder?.id == folderId } ?: return
        val comicIdsInGroup = group.comics.map { it.id }.toSet()
        val currentSelection = _uiState.value.selectedComicIds.toMutableSet()

        // 如果当前文件夹的所有漫画都已被选中，则取消全选，否则全选
        if (currentSelection.containsAll(comicIdsInGroup)) {
            currentSelection.removeAll(comicIdsInGroup)
        } else {
            currentSelection.addAll(comicIdsInGroup)
        }
        _uiState.update { it.copy(selectedComicIds = currentSelection) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val idsToDelete = _uiState.value.selectedComicIds
            if (idsToDelete.isEmpty()) return@launch

            // 1. 删除选中的漫画
            favoriteDao.deleteByIds(idsToDelete.toList())

            // 2. 检查并删除空收藏夹
            val allFolders = favoriteFolderDao.getAllFavoriteFolders().first()
            for (folder in allFolders) {
                val comicsInFolder = favoriteDao.getFavoritesByFolderId(folder.id).first()
                if (comicsInFolder.isEmpty()) {
                    favoriteFolderDao.deleteFolder(folder)
                }
            }

            // 退出管理模式并刷新列表
            _uiState.update { it.copy(selectedComicIds = emptySet(), isManagementMode = false) }
            loadFavorites()
        }
    }

    fun exportSelected() {
        // TODO: 实现导出为JSON的功能
        Log.d("FavoritesViewModel", "Exporting IDs: ${_uiState.value.selectedComicIds}")
    }

    // --- 其他方法 ---

    fun toggleFolderExpansion(folderId: Long?) {
        val updatedGroups = _uiState.value.favoriteGroups.map { group ->
            if (group.folder?.id == folderId) {
                group.copy(isExpanded = !group.isExpanded)
            } else {
                group
            }
        }
        _uiState.update { it.copy(favoriteGroups = updatedGroups) }
    }
    
    fun saveScrollState(position: Int, offset: Int) {
        _uiState.update { it.copy(listScrollPosition = position, listScrollOffset = offset) }
    }

    private fun mapFavoriteToComic(favorite: Favorite): Comic {
        return Comic(
            id = favorite.comicId,
            title = favorite.title,
            coverUrl = NetworkUtils.thumbnailsUrl(favorite.comicId),
            artists = emptyList(),
            groups = emptyList(),
            parodies = emptyList(),
            characters = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            categories = emptyList(),
            imageList = emptyList(),
            timestamp = favorite.timestamp,
            language = favorite.language
        )
    }
}
