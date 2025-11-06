package com.exp.hentai1.ui.ranking

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RankingMoreViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RankingMoreViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RankingMoreViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}