package com.exp.hentai1.ui.local

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.Download
import com.exp.hentai1.data.DownloadStatus
import com.exp.hentai1.util.Event
import com.exp.hentai1.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 使用密封类来更清晰地描述状态
sealed class LocalComicState {
    data class Downloading(
        val comic: Download,
        val downloadedPages: Int,
        val totalPages: Int
    ) : LocalComicState()

    data class Completed(
        val comic: Download,
        val coverPath: String
    ) : LocalComicState()

    data class Incomplete( // 用于已暂停或失败的下载
        val comic: Download,
        val downloadedPages: Int
    ) : LocalComicState()
}

data class LocalUiState(
    val comics: List<LocalComicState> = emptyList(),
    val isLoading: Boolean = true
)

class LocalViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LocalUiState())
    val uiState: StateFlow<LocalUiState> = _uiState

    private val _openDirectoryEvent = MutableLiveData<Event<Intent>>()
    val openDirectoryEvent: LiveData<Event<Intent>> = _openDirectoryEvent

    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val workManager = WorkManager.getInstance(application)

    // 观察所有标记了我们 worker 名称的 LiveData
    private val workInfos: LiveData<List<WorkInfo>> = workManager.getWorkInfosByTagLiveData(DownloadWorker::class.java.name)


    init {
        viewModelScope.launch {
            // 结合数据库的 flow 和文件系统扫描
            downloadDao.getAllDownloads().collect { dbDownloads ->
                _uiState.value = _uiState.value.copy(isLoading = true)
                val localComicStates = scanFileSystemAndCreateStates(dbDownloads)
                _uiState.value = LocalUiState(comics = localComicStates, isLoading = false)
            }
        }
    }

    // 此函数将由 Composable 中的观察者调用
    fun observeWorkManager(workInfos: List<WorkInfo>?) {
        viewModelScope.launch {
            // 我们只关心正在运行的任务，以更新UI进度条
            val runningWork = workInfos?.filter { it.state == WorkInfo.State.RUNNING }

            if (runningWork.isNullOrEmpty()) {
                // 如果没有正在运行的任务，就不做任何事情。
                // init块中的 scanFileSystemAndCreateStates 已经处理了初始状态（Completed或Incomplete）。
                // 我们不需要在这里重新扫描，否则可能会导致不必要的UI刷新或状态覆盖。
                return@launch
            }

            val currentComics = _uiState.value.comics.mapNotNull {
                // 从当前的UI状态中获取漫画信息
                when (it) {
                    is LocalComicState.Downloading -> it.comic
                    is LocalComicState.Incomplete -> it.comic
                    is LocalComicState.Completed -> it.comic // 也包括已完成的，以防万一
                }
            }.associateBy { it.comicId }

            runningWork.forEach { workInfo ->
                val comicId = workInfo.tags.firstOrNull { it.startsWith("id:") }?.substring(3)
                val progress = workInfo.progress
                val downloadedPages = progress.getInt(DownloadWorker.KEY_PROGRESS_PAGES, -1)
                val totalPages = progress.getInt(DownloadWorker.KEY_TOTAL_PAGES, -1)

                if (comicId != null && downloadedPages != -1 && totalPages != -1) {
                    val comic = currentComics[comicId]
                    if (comic != null) {
                        // 找到对应的漫画，并更新为“正在下载”状态
                        val newState = LocalComicState.Downloading(comic, downloadedPages, totalPages)
                        updateComicState(comicId, newState)
                    }
                }
            }
        }
    }

    fun openComicDirectory(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseDir = File(downloadDir, "Hentai1")
            val comicDir = File(baseDir, comicId)

            if (comicDir.exists() && comicDir.isDirectory) {
                val application = getApplication<Application>()
                val authority = "${application.packageName}.provider"
                val uri = FileProvider.getUriForFile(application, authority, comicDir)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    // 使用标准的目录 MIME 类型
                    setDataAndType(uri, "application/vnd.android.directory")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "选择一个应用打开")
                _openDirectoryEvent.postValue(Event(chooser))
            }
        }
    }

    fun pauseDownload(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadDao.getDownloadById(comicId)
            if (download != null) {
                // 1. 更新数据库状态为 PAUSED
                downloadDao.insert(download.copy(status = DownloadStatus.PAUSED))
                // 2. 不再取消 WorkManager 任务，让其自行检测数据库状态并暂停
            }
        }
    }

    fun resumeDownload(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadDao.getDownloadById(comicId)
            if (download != null) {
                // 1. 更新数据库状态为 DOWNLOADING
                downloadDao.insert(download.copy(status = DownloadStatus.DOWNLOADING))

                // 2. 重新启动 WorkManager 任务
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(DownloadWorker.KEY_COMIC_ID to comicId))
                    .addTag(DownloadWorker::class.java.name) // 通用标签
                    .addTag("id:$comicId") // 此漫画的特定标签
                    .build()
                workManager.enqueue(workRequest)
            }
        }
    }

    fun deleteComic(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 从数据库中删除记录
            downloadDao.deleteById(comicId)

            // 2. 删除本地文件
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseDir = File(downloadDir, "Hentai1")
            val comicDir = File(baseDir, comicId)
            if (comicDir.exists()) {
                comicDir.deleteRecursively() // 递归删除文件夹及其内容
            }
        }
    }


    private fun updateComicState(comicId: String, newState: LocalComicState) {
        val updatedList = _uiState.value.comics.map {
            val id = when(it) {
                is LocalComicState.Completed -> it.comic.comicId
                is LocalComicState.Downloading -> it.comic.comicId
                is LocalComicState.Incomplete -> it.comic.comicId
            }
            if (id == comicId) newState else it
        }
        _uiState.value = _uiState.value.copy(comics = updatedList)
    }


    private suspend fun scanFileSystemAndCreateStates(downloads: List<Download>): List<LocalComicState> = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(downloadDir, "Hentai1")

        downloads.map { comic ->
            val comicDir = File(baseDir, comic.comicId)
            if (!comicDir.exists()) {
                // 如果目录不存在，则视为未完成（或失败）
                LocalComicState.Incomplete(comic, 0)
            } else {
                val coverFile = File(comicDir, "cover.webp")
                // 计算图片文件数量，排除封面
                val downloadedPages = comicDir.listFiles { _, name ->
                    name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
                }?.count { it.name != "cover.webp" } ?: 0

                if (downloadedPages >= comic.totalPages && coverFile.exists()) {
                    LocalComicState.Completed(comic, coverFile.absolutePath)
                } else {
                    LocalComicState.Incomplete(comic, downloadedPages)
                }
            }
        }
    }
}
