package com.exp.hentai1.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TagListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromTagList(tags: List<Tag>?): String {
        return gson.toJson(tags ?: emptyList<Tag>())
    }

    @TypeConverter
    fun toTagList(json: String): List<Tag> {
        val listType = object : TypeToken<List<Tag>>() {}.type
        return gson.fromJson(json, listType)
    }
}

class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(strings: List<String>?): String {
        return gson.toJson(strings ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, listType)
    }
}