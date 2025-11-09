package com.exp.hentai1.data

import androidx.room.TypeConverter

class DownloadStatusConverter {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(status: String): DownloadStatus {
        return DownloadStatus.valueOf(status)
    }
}