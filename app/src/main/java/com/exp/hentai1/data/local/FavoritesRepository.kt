package com.exp.hentai1.data.local

import android.content.Context
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.ComicDataParser

class FavoritesRepository(private val context: Context) {

    suspend fun getFavorites(): Result<List<Comic>> {
        return try {
            val html = NetworkUtils.fetchHtml(context, NetworkUtils.favoritesUrl())
            if (html != null && !html.startsWith("Error")) {
                val comics = ComicDataParser.parseFavoritesComics(html)
                Result.success(comics)
            } else {
                Result.failure(Exception(html ?: "Failed to fetch favorites"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}