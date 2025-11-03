package com.exp.hentai1.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.local.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val comics: List<Comic> = emptyList(),
    val error: String? = null
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesRepository = FavoritesRepository(application)

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    init {
        fetchFavorites()
    }

    private fun fetchFavorites() {
        viewModelScope.launch {
            _uiState.value = FavoritesUiState(isLoading = true)
            favoritesRepository.getFavorites()
                .onSuccess {
                    _uiState.value = FavoritesUiState(isLoading = false, comics = it)
                }
                .onFailure {
                    _uiState.value = FavoritesUiState(isLoading = false, error = it.message)
                }
        }
    }
}