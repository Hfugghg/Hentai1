package com.exp.hentai1.ui.parseTags

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.exp.hentai1.data.AppDatabase

class ParseTagsViewModelFactory(
    private val application: Application,
    private val entityType: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParseTagsViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            @Suppress("UNCHECKED_CAST")
            return ParseTagsViewModel(application, entityType, database.userTagDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
