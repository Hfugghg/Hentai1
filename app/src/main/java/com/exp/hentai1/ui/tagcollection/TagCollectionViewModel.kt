package com.exp.hentai1.ui.tagcollection

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.UserTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 标签收藏页面的 UI 状态
 */
data class TagCollectionUiState(
    val artists: List<UserTag> = emptyList(),
    val groups: List<UserTag> = emptyList(),
    val parodies: List<UserTag> = emptyList(),
    val characters: List<UserTag> = emptyList(),
    val tags: List<UserTag> = emptyList(),
    val languages: List<UserTag> = emptyList(),
    val categories: List<UserTag> = emptyList(),
    val isDataLoaded: Boolean = false // 用于控制动画
)

class TagCollectionViewModel(application: Application) : AndroidViewModel(application) {

    private val userTagDao = AppDatabase.getDatabase(application).userTagDao()

    private val _uiState = MutableStateFlow(TagCollectionUiState())
    val uiState: StateFlow<TagCollectionUiState> = _uiState.asStateFlow()

    init {
        Log.d("TagCollectionViewModel", "ViewModel initialized. Starting to load tags.")
        loadCollectedTags()
    }

    private fun loadCollectedTags() {
        Log.d("TagCollectionViewModel", "loadCollectedTags called.")
        viewModelScope.launch {
            Log.d("TagCollectionViewModel", "Coroutine launched. Collecting tags from DAO.")
            userTagDao.getAllTags()
                .catch { e ->
                    Log.e("TagCollectionViewModel", "Error collecting tags", e)
                    // 即使出错也要更新状态，以便 UI 可以显示错误或空状态
                    _uiState.update { it.copy(isDataLoaded = true) }
                }
                .collect { allTags ->
                    Log.d("TagCollectionViewModel", "Collected ${allTags.size} tags from database.")
                    val groupedTags = allTags.groupBy { it.category }
                    _uiState.update {
                        it.copy(
                            artists = groupedTags["artists"] ?: emptyList(),
                            groups = groupedTags["groups"] ?: emptyList(),
                            parodies = groupedTags["parodies"] ?: emptyList(),
                            characters = groupedTags["characters"] ?: emptyList(),
                            tags = groupedTags["tags"] ?: emptyList(),
                            languages = groupedTags["languages"] ?: emptyList(),
                            categories = groupedTags["categories"] ?: emptyList(),
                            isDataLoaded = true // 在此处设置
                        )
                    }
                    Log.d("TagCollectionViewModel", "UI state updated. isDataLoaded is now true.")
                }
        }
    }

    /**
     * 删除一个标签 (取消收藏或取消拉黑)
     */
    fun deleteTag(tag: UserTag) {
        viewModelScope.launch {
            try {
                // 删除操作，UserTagDao中我们新增了根据ID删除的方法
                userTagDao.deleteTag(tag.id)
                Log.d("TagCollectionViewModel", "Deleted tag: ${tag.name} (ID: ${tag.id})")
                // 数据库的Flow会自动更新，因此UI也会自动刷新
            } catch (e: Exception) {
                Log.e("TagCollectionViewModel", "Failed to delete tag: ${tag.name}", e)
                // 可以在这里添加一个向UI报告错误的机制，但为简化起见，这里只记录日志
            }
        }
    }

    fun saveAsFavorite(tag: UserTag) {
        viewModelScope.launch {
            try {
                val favoriteTag = tag.copy(
                    type = 1,
                    timestamp = System.currentTimeMillis()
                )
                userTagDao.insert(favoriteTag)
                Log.d("TagCollectionViewModel", "Saved tag as favorite: ${tag.name}")
            } catch (e: Exception) {
                Log.e("TagCollectionViewModel", "Failed to save tag as favorite: ${tag.name}", e)
            }
        }
    }

    /**
     * 将标签类型改为拉黑 (type = 0)
     * 注意：此功能在“我的标签收藏”页面可能不常用，通常用于在已收藏/删除的标签上执行拉黑操作。
     */
    fun saveAsBlock(tag: UserTag) {
        viewModelScope.launch {
            try {
                val blockTag = tag.copy(
                    type = 0,
                    timestamp = System.currentTimeMillis()
                )
                userTagDao.insert(blockTag)
                Log.d("TagCollectionViewModel", "Saved tag as block: ${tag.name}")
            } catch (e: Exception) {
                Log.e("TagCollectionViewModel", "Failed to save tag as block: ${tag.name}", e)
            }
        }
    }
}