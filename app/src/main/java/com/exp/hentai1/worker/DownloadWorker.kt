package com.exp.hentai1.worker

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.exp.hentai1.data.AppDatabase
import com.exp.hentai1.data.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger // <-- 1. 导入 AtomicInteger

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_COMIC_ID = "COMIC_ID"
        const val KEY_PROGRESS_PAGES = "PROGRESS_PAGES"
        const val KEY_TOTAL_PAGES = "TOTAL_PAGES"
        private const val MAX_CONCURRENT_IMAGE_DOWNLOADS = 5 // 单个漫画内部同时下载的图片数量
    }

    private val downloadDao = AppDatabase.getDatabase(appContext).downloadDao()
    private val okHttpClient = OkHttpClient.Builder().build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val comicId = inputData.getString(KEY_COMIC_ID) ?: return@withContext Result.failure()

        var downloadEntry = downloadDao.getDownloadById(comicId) ?: return@withContext Result.failure()
        val coverUrl = downloadEntry.coverUrl
        val imageList: List<String> = downloadEntry.imageList // 这是完整的图片列表
        val trueTotalPages = downloadEntry.totalPages

        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val comicDir = File(downloadDir, "Hentai1/$comicId")
            if (!comicDir.exists()) {
                comicDir.mkdirs()
            }

            // 1. 检查封面是否已存在并下载
            val coverFile = File(comicDir, "cover.webp")
            if (!coverFile.exists()) {
                try {
                    downloadImage(coverUrl, coverFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 封面下载失败不影响主任务，但记录一下
                }
            }

            // 2. 预先计数已下载的页面
            // 重新扫描目录以获取最新的已下载文件数量
            var downloadedPages = countDownloadedPages(comicDir)

            // 3. 立即报告初始进度
            setProgress(downloadedPages, trueTotalPages)

            // 检查启动时是否已暂停
            val initialEntry = downloadDao.getDownloadById(comicId)
            if (initialEntry == null || initialEntry.status == DownloadStatus.PAUSED) {
                return@withContext Result.retry() // 任务已暂停，等待下次调度
            }

            // 如果重启时已经下完了
            if (downloadedPages >= trueTotalPages) {
                val updatedEntry = downloadEntry.copy(imageList = emptyList(), status = DownloadStatus.COMPLETED)
                downloadDao.insert(updatedEntry)
                return@withContext Result.success()
            }

            // --- 4. 开始并发循环下载 (重构部分) ---

            val semaphore = Semaphore(MAX_CONCURRENT_IMAGE_DOWNLOADS)

            // 使用 AtomicInteger 来安全地从并发协程中更新计数
            val downloadedPagesAtomic = AtomicInteger(downloadedPages)

            // 过滤掉已经下载的图片, 只下载不存在的文件
            val imagesToDownload = imageList.withIndex().filter { (index, imageUrl) ->
                val extension = imageUrl.substringAfterLast('.')
                val pageFile = File(comicDir, "${index + 1}.$extension")
                !pageFile.exists() // 只保留不存在（需要下载）的
            }

            // 为需要下载的图片创建并发任务
            val deferredDownloads = imagesToDownload.map { (index, imageUrl) ->
                async {
                    // 每次下载前都检查暂停状态
                    val currentEntry = downloadDao.getDownloadById(comicId)
                    if (currentEntry == null || currentEntry.status == DownloadStatus.PAUSED) {
                        return@async false // 暂停了，不下载，返回失败
                    }

                    val extension = imageUrl.substringAfterLast('.')
                    val pageFile = File(comicDir, "${index + 1}.$extension")

                    var success = false
                    semaphore.withPermit {
                        // 在`withPermit`内部（非挂起上下文）执行阻塞的IO操作
                        try {
                            // 再次检查，防止在等待信号量时状态变为暂停
                            val entryBeforeDownload = downloadDao.getDownloadById(comicId)
                            if (entryBeforeDownload != null && entryBeforeDownload.status != DownloadStatus.PAUSED) {
                                downloadImage(imageUrl, pageFile)
                                success = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            success = false // 下载失败
                        }
                    }

                    // 在`withPermit`外部（挂起上下文）报告进度
                    if (success) {
                        // 下载成功，递增计数并报告进度
                        val newCount = downloadedPagesAtomic.incrementAndGet()
                        setProgress(newCount, trueTotalPages) // **关键修复：报告进度**
                    }
                    success // 返回此任务的成功状态
                }
            }

            // 等待所有并发下载任务完成
            deferredDownloads.awaitAll()

            // 重新从文件系统计数，这是最准确的
            downloadedPages = countDownloadedPages(comicDir)

            // 5. 根据最终下载结果更新状态
            if (downloadedPages >= trueTotalPages) {
                // 所有图片都已下载
                val updatedEntry = downloadEntry.copy(imageList = emptyList(), status = DownloadStatus.COMPLETED)
                downloadDao.insert(updatedEntry)
                Result.success()
            } else {
                // 检查是否在过程中被暂停
                val finalEntry = downloadDao.getDownloadById(comicId)
                if (finalEntry == null || finalEntry.status == DownloadStatus.PAUSED) {
                    return@withContext Result.retry() // 是暂停的，直接重试
                }

                // 未完成且未暂停，说明有下载失败，重试
                // 状态保持为 DOWNLOADING，表示仍在进行中
                val updatedEntry = downloadEntry.copy(status = DownloadStatus.DOWNLOADING)
                downloadDao.insert(updatedEntry)
                Result.retry()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 整个任务发生不可恢复的错误
            downloadEntry = downloadEntry.copy(status = DownloadStatus.FAILED)
            downloadDao.insert(downloadEntry)
            Result.failure()
        }
    }

    private suspend fun setProgress(downloaded: Int, total: Int) {
        val progressData = workDataOf(
            KEY_PROGRESS_PAGES to downloaded,
            KEY_TOTAL_PAGES to total
        )
        setProgress(progressData)
    }

    private fun downloadImage(imageUrl: String, destFile: File) {
        val request = Request.Builder().url(imageUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download image: $response")

            response.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Response body is null for $imageUrl")
        }
    }

    private fun countDownloadedPages(comicDir: File): Int {
        return comicDir.listFiles { _, name ->
            name.matches(Regex("\\d+\\.(jpg|png|gif|webp)")) // 匹配数字文件名，排除封面
        }?.size ?: 0
    }
}