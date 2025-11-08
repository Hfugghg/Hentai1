package com.exp.hentai1.ui.parseTags

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.TagInfo
import com.exp.hentai1.data.UserTag
import com.exp.hentai1.data.UserTagDao
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser
import com.exp.hentai1.data.remote.parser.parseTagsList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

// 新增 ScrollPosition 数据类，用于存储滚动位置
data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

data class ParseTagsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val sortType: SortType = SortType.NAME, // 添加 sortType 字段
    // 存储不同排序类型下的标签列表缓存
    val cachedTags: Map<SortType, List<TagInfo>> = mapOf(
        SortType.NAME to emptyList(),
        SortType.COUNT to emptyList()
    ),
    // 存储不同排序类型下的当前页码
    val currentPages: Map<SortType, Int> = mapOf(
        SortType.NAME to 1,
        SortType.COUNT to 1
    ),
    // 存储不同排序类型下是否还能加载更多
    val canLoadMoreMap: Map<SortType, Boolean> = mapOf(
        SortType.NAME to true,
        SortType.COUNT to true
    ),
    // 添加 scrollPositions 字段，用于存储不同排序类型下的滚动位置
    val scrollPositions: Map<SortType, ScrollPosition> = mapOf(
        SortType.NAME to ScrollPosition(),
        SortType.COUNT to ScrollPosition()
    ),
    // 用于存储被拉黑的标签的 (id, category) 对
    val blockedTagKeys: Set<Pair<String, String>> = emptySet(),
    // 用于存储被收藏的标签的 (id, category) 对
    val favoritedTagKeys: Set<Pair<String, String>> = emptySet()
) {
    // 计算属性，返回当前 sortType 对应的标签列表
    val currentTags: List<TagInfo>
        get() = cachedTags[sortType] ?: emptyList()

    // 计算属性，返回当前 sortType 对应的页码
    val currentPage: Int
        get() = currentPages[sortType] ?: 1

    // 计算属性，返回当前 sortType 是否还能加载更多
    val canLoadMore: Boolean
        get() = canLoadMoreMap[sortType] ?: true
}

class ParseTagsViewModel(
    application: Application,
    private val entityType: String,
    private val userTagDao: UserTagDao // 注入DAO
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ParseTagsUiState())
    val uiState: StateFlow<ParseTagsUiState> = _uiState.asStateFlow()

    init {
        // 初始加载
        loadItems(sortType = SortType.NAME)
        // 监听用户标签变化
        observeUserTags()
    }

    private fun observeUserTags() {
        viewModelScope.launch {
            userTagDao.getAllTags().collect { userTags ->
                val blocked = userTags.filter { it.type == 0 }.map { it.id to it.category }.toSet()
                val favorited = userTags.filter { it.type == 1 }.map { it.id to it.category }.toSet()
                _uiState.update { currentState ->
                    currentState.copy(
                        blockedTagKeys = blocked,
                        favoritedTagKeys = favorited
                    )
                }
            }
        }
    }

    fun blockTags(tagKeysToBlock: Set<Pair<String, String>>) {
        addTags(tagKeysToBlock, 0)
    }

    fun favoriteTags(tagKeysToFavorite: Set<Pair<String, String>>) {
        addTags(tagKeysToFavorite, 1)
    }

    fun unblockTags(tagKeysToUnblock: Set<Pair<String, String>>) {
        removeTags(tagKeysToUnblock, 0)
    }

    fun unfavoriteTags(tagKeysToUnfavorite: Set<Pair<String, String>>) {
        removeTags(tagKeysToUnfavorite, 1)
    }

    private fun removeTags(tagKeys: Set<Pair<String, String>>, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            tagKeys.forEach { (id, category) ->
                userTagDao.deleteByIdCategoryAndType(id, category, type)
            }
        }
    }

    private fun addTags(tagKeys: Set<Pair<String, String>>, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val allTagsInCache = _uiState.value.cachedTags.values.flatten()
            val tagsToAdd = allTagsInCache.filter { (it.id to it.category) in tagKeys }

            if (tagsToAdd.isNotEmpty()) {
                tagsToAdd.forEach {
                    val userTag = UserTag(
                        id = it.id,
                        name = it.name,
                        englishName = it.englishName,
                        category = it.category,
                        timestamp = System.currentTimeMillis(),
                        type = type
                    )
                    userTagDao.insert(userTag)
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        loadItems(page = state.currentPage + 1, sortType = state.sortType)
    }

    fun setSortType(newSortType: SortType) {
        if (_uiState.value.sortType == newSortType) return

        _uiState.update { currentState ->
            val hasCachedData = currentState.cachedTags[newSortType]?.isNotEmpty() == true
            currentState.copy(
                sortType = newSortType,
                isLoading = !hasCachedData
            )
        }

        if (_uiState.value.cachedTags[newSortType]?.isEmpty() == true) {
            loadItems(page = 1, sortType = newSortType)
        }
    }

    fun updateScrollPosition(sortType: SortType, index: Int, offset: Int) {
        _uiState.update { currentState ->
            val updatedScrollPositions = currentState.scrollPositions.toMutableMap()
            updatedScrollPositions[sortType] = ScrollPosition(index, offset)
            currentState.copy(scrollPositions = updatedScrollPositions)
        }
    }

    private fun loadItems(page: Int = 1, sortType: SortType) {
        viewModelScope.launch(Dispatchers.IO) {
            if (page == 1 && _uiState.value.cachedTags[sortType]?.isEmpty() == true) {
                _uiState.update { it.copy(isLoading = true) }
            }
            try {
                val popular = sortType == SortType.COUNT
                val url = when (entityType) {
                    "tags" -> NetworkUtils.tagsUrl(popular = popular, page = page)
                    "parodies" -> NetworkUtils.parodiesUrl(popular = popular, page = page)
                    "characters" -> NetworkUtils.charactersUrl(popular = popular, page = page)
                    "artists" -> NetworkUtils.artistsUrl(popular = popular, page = page)
                    "groups" -> NetworkUtils.groupsUrl(popular = popular, page = page)
                    else -> throw IllegalArgumentException("Unknown entity type: $entityType")
                }
                val html = NetworkUtils.fetchHtml(getApplication(), url)
                val newTags = html?.let {
                    val payloads = NextFParser.extractPayloadsFromHtml(it)
                    payloads["7"]?.let { payload ->
                        val jsonString = NextFParser.cleanPayloadString(payload)
                        val jsonArray = JSONArray(jsonString)
                        parseTagsList(jsonArray)
                    } ?: emptyList()
                } ?: emptyList()

                _uiState.update { currentState ->
                    val currentCachedList = currentState.cachedTags[sortType] ?: emptyList()
                    val updatedCachedList = if (page == 1) newTags else currentCachedList + newTags

                    val updatedCachedTags = currentState.cachedTags.toMutableMap()
                    updatedCachedTags[sortType] = updatedCachedList

                    val updatedCurrentPages = currentState.currentPages.toMutableMap()
                    updatedCurrentPages[sortType] = page

                    val updatedCanLoadMoreMap = currentState.canLoadMoreMap.toMutableMap()
                    updatedCanLoadMoreMap[sortType] = newTags.isNotEmpty()

                    currentState.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        cachedTags = updatedCachedTags,
                        currentPages = updatedCurrentPages,
                        canLoadMoreMap = updatedCanLoadMoreMap,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            }
        }
    }
}
