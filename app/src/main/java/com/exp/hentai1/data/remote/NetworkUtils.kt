package com.exp.hentai1.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class HentaiOneSite(val baseUrl: String) {
    // 日语
    MAIN("https://hentai-one.com"),
    // 中文
    CHINESE("https://ch.hentai-one.com"),
    // 英文
    ENGLISH("https://en.hentai-one.com")
}

object NetworkUtils {

    const val GITHUB_RELEASE_URL = "https://api.github.com/repos/Hfugghg/Hentai1/releases/latest"

    private var currentSite = HentaiOneSite.MAIN

    fun setSite(site: HentaiOneSite) {
        currentSite = site
    }

    fun getCurrentSite(): HentaiOneSite {
        return currentSite
    }

    private val baseUrl: String
        get() = currentSite.baseUrl

    // 一个持久化的OkHttpClient，与WebView共享Cookie
    private val client: OkHttpClient by lazy {
        // 确保设置了默认的CookieHandler来桥接WebView和OkHttp的Cookie
        CookieHandler.setDefault(CookieManager())
        OkHttpClient.Builder()
            .build()
    }

    @Volatile private var isSolvingChallenge = false

    /**
     * 新增: 探测指定 comicId 存在于哪个站点。
     * 它会按顺序检查所有站点，并返回第一个成功获取到 HTML 的站点。
     * 此函数会调用 fetchHtml，因此它能处理 Cloudflare 质询。
     */
    suspend fun findComicSite(context: Context, comicId: String): HentaiOneSite? = withContext(Dispatchers.IO) {
        // 按 HentaiOneSite.entries (MAIN, CHINESE, ENGLISH) 顺序尝试
        for (site in HentaiOneSite.entries) {
            val url = "${site.baseUrl}/articles/$comicId"
            try {
                // 使用现有的 fetchHtml，因为它已经处理了 Cloudflare
                val html = fetchHtml(context, url)
                if (html != null && !html.startsWith("Error")) {
                    // 成功获取到 HTML，说明此站点存在该 ID
                    return@withContext site
                }
            } catch (e: Exception) {
                // 发生网络错误等，继续尝试下一个站点
                e.printStackTrace()
            }
        }
        // 尝试了所有站点均未找到
        return@withContext null
    }

    suspend fun fetchHtml(context: Context, url: String): String? = withContext(Dispatchers.IO) {
        if (isSolvingChallenge) return@withContext null // 防止重入调用

        var response = makeRequest(url)

        // 检查Cloudflare质询（503 Service Unavailable是一个常见的指标）
        if (response.code == 503) {
            isSolvingChallenge = true
            val challengeSolved = solveChallengeWithWebView(context, url)
            isSolvingChallenge = false

            if (challengeSolved) {
                // 使用新的Cookie重试请求
                response = makeRequest(url)
            }
        }

        if (response.isSuccessful) {
            response.body?.string()
        } else {
            "Error: ${response.code} - ${response.message}"
        }
    }

    private fun makeRequest(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        return client.newCall(request).execute()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun solveChallengeWithWebView(context: Context, url: String): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                if (Looper.myLooper() == null) {
                    Looper.prepare()
                }

                val webView = WebView(context).apply {
                    // 警告: 启用JavaScript可能引入XSS漏洞，请仔细审查
                    settings.javaScriptEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            super.onPageFinished(view, loadedUrl)
                            // 页面加载完成后，检查cf_clearance cookie。
                            val cookies = android.webkit.CookieManager.getInstance().getCookie(loadedUrl)
                            val hasClearanceCookie = cookies?.contains("cf_clearance") == true

                            if (hasClearanceCookie) {
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                    destroy()
                                }
                            }
                            // 如果仍然是质询页面，WebView会自动继续尝试。
                            // 我们可以在这里添加一个超时，以防止卡住。
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (continuation.isActive) {
                                val errorMessage = error?.description ?: "Unknown error"
                                val failingUrl = request?.url?.toString() ?: "Unknown URL"
                                continuation.resumeWithException(Exception("WebView error ($failingUrl): $errorMessage"))
                                view?.destroy()
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    webView.destroy()
                }

                webView.loadUrl(url)
            }
        }

    private fun buildEntityUrl(entity: String, id: Any, popular: Boolean = false, page: Int = 1): String {
        val url = "$baseUrl/$entity/$id"
        val queryParams = mutableListOf<String>()
        if (popular) {
            queryParams.add("type=popular")
        }
        if (page > 1) {
            queryParams.add("page=$page")
        }
        return if (queryParams.isEmpty()) {
            url
        } else {
            "$url?${queryParams.joinToString("&")}"
        }
    }

    fun searchUrl(keyword: String, popular: Boolean = false, page: Int = 1): String {
        val popularParam = if (popular) "&type=popular" else ""
        return "$baseUrl/articles/search?keyword=${URLEncoder.encode(keyword, "UTF-8")}$popularParam&page=$page"
    }

    fun detailUrl(comicId: String): String {
        return "$baseUrl/articles/$comicId"
    }

    fun viewerUrl(comicId: String, page: Int = 1): String {
        return "$baseUrl/viewer?articleId=$comicId&page=$page"
    }

    fun daylyRankUrl(page: Int = 1): String {
        return "$baseUrl/articles/rank?t=daily&page=$page"
    }

    fun weeklyRankUrl(page: Int = 1): String {
        return "$baseUrl/articles/rank?t=weekly&page=$page"
    }

    fun monthlyRankUrl(page: Int = 1): String {
        return "$baseUrl/articles/rank?t=monthly&page=$page"
    }

    fun allTimeRankUrl(page: Int = 1): String {
        return "$baseUrl/articles/rank?t=all_time&page=$page"
    }

    fun latestUrl(page: Int = 1): String {
        return if (page == 1) baseUrl else "$baseUrl/?page=$page"
    }

    private fun buildListUrl(entity: String, popular: Boolean = false, page: Int = 1): String {
        val path = if (popular) "/${entity}/popular" else "/${entity}"
        val queryParams = mutableListOf<String>()
        if (page > 1) {
            queryParams.add("page=$page")
        }
        return if (queryParams.isEmpty()) {
            "$baseUrl$path"
        } else {
            "$baseUrl$path?${queryParams.joinToString("&")}"
        }
    }

    fun tagsUrl(popular: Boolean = false, page: Int = 1): String {
        return buildListUrl("tags", popular, page)
    }

    fun parodiesUrl(popular: Boolean = false, page: Int = 1): String {
        return buildListUrl("parodies", popular, page)
    }

    fun charactersUrl(popular: Boolean = false, page: Int = 1): String {
        return buildListUrl("characters", popular, page)
    }

    fun artistsUrl(popular: Boolean = false, page: Int = 1): String {
        return buildListUrl("artists", popular, page)
    }

    fun groupsUrl(popular: Boolean = false, page: Int = 1): String {
        return buildListUrl("groups", popular, page)
    }

    fun thumbnailsUrl(comicId: String): String {
        return "https://cdn.imagedeliveries.com/$comicId/thumbnails/cover.webp"
    }

    fun getCoverUrl(path: String): String {
        return if (path.startsWith("https://")) {
            path
        } else {
            "https://cdn.imagedeliveries.com/$path"
        }
    }

    fun artistUrl(artistId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("artists", artistId, popular, page)
    }

    fun groupUrl(groupsId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("groups", groupsId, popular, page)
    }

    fun parodyUrl(parodiesId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("parodies", parodiesId, popular, page)
    }

    fun characterUrl(charactersId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("characters", charactersId, popular, page)
    }

    fun tagUrl(tagsId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("tags", tagsId, popular, page)
    }

    fun languageUrl(languagesId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("languages", languagesId, popular, page)
    }

    fun categoryUrl(categoriesId: Int, popular: Boolean = false, page: Int = 1): String {
        return buildEntityUrl("categories", categoriesId, popular, page)
    }

}