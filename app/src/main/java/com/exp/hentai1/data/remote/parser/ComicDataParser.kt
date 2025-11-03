package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.Tag
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element


class ComicDataParser {

    companion object {
        private const val TAG = "ComicDataParser"

        /**
         * 解析包含漫画数据的HTML。
         * 此方法会协调两种解析策略：
         * 1. 优先调用 [NextFParser] 来解析 `<script>` 标签中由 Next.js 推送的 JSON 数据。
         * 2. 使用 Jsoup 直接从HTML的DOM结构中提取漫画信息作为备用。
         *
         * @param html 完整的HTML页面源字符串。
         * @return 从HTML DOM结构中解析出的 [com.exp.hentai1.data.Comic] 列表。
         */
        fun parseLatestComics(html: String): List<Comic> {
            Log.d(TAG, "[parseLatestComics] 开始解析HTML，长度: ${html.length}")
            val parsedComics = mutableListOf<Comic>()

            // 1. **优先**使用 NextFParser 提取并解析 script 中的数据
            try {
                val payloads = NextFParser.extractPayloadsFromHtml(html)
                Log.d(TAG, "[parseLatestComics] 提取到的 Script Payloads ID: ${payloads.keys}")

                // 尝试解析数据块 7: 新着漫画列表
                payloads["7"]?.let { payload ->
                    // 现在直接调用导入的顶层函数 parsePayload7
                    val comics7 = parsePayload7(payload)
                    Log.i(TAG, "[parseLatestComics] 从 Payload 7 解析到 ${comics7.size} 个漫画")
                    parsedComics.addAll(comics7)
                }

                if (parsedComics.isNotEmpty()) {
                    Log.i(TAG, "[parseLatestComics] 成功从 Next.js payloads 中解析到 ${parsedComics.size} 个漫画，优先返回此列表。")
                    // 如果从 JSON 中解析到了数据，则优先返回 JSON 解析结果
                    return parsedComics.distinctBy { it.id }
                }

            } catch (e: Exception) {
                // 如果 NextFParser 发生致命错误，将记录错误并回退到 Jsoup 解析
                Log.e(TAG, "[parseLatestComics] 使用 NextFParser 解析 script 数据时出错，回退到 Jsoup: ${e.message}", e)
            }

            // 2. 使用 Jsoup 从HTML结构中提取（作为备用）
            Log.w(TAG, "[parseLatestComics] Next.js payloads 未能提供数据或解析失败，尝试使用 Jsoup 解析HTML结构。")
            return try {
                val comicsFromHtml = extractFromHtmlStructure(html)
                Log.i(TAG, "[parseLatestComics] 从HTML结构中解析到 ${comicsFromHtml.size} 个漫画")
                comicsFromHtml
            } catch (e: Exception) {
                Log.e(TAG, "[parseLatestComics] 使用 Jsoup 解析HTML结构时出错: ${e.message}", e)
                emptyList()
            }
        }

        fun parseRankingComics(html: String): List<Comic> {
            try {
                val payloads = NextFParser.extractPayloadsFromHtml(html)
                payloads["18"]?.let { payload ->
                    return parsePayload18(payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[parseRankingComics] 使用 NextFParser 解析 script 数据时出错: ${e.message}", e)
            }
            return emptyList()
        }

        fun parseFavoritesComics(html: String): List<Comic> {
            return extractFromHtmlStructure(html)
        }

        // --- 这是修复的关键 ---
        /**
         * 解析漫画详情页。
         * 根据日志，详情页数据现在也包含在 Payload 7 中。
         * @param html 详情页的完整 HTML
         * @return 解析出的 Comic 对象，如果失败则为 null
         */
        fun parseComicDetail(html: String): Comic? {
            Log.d(TAG, "[parseComicDetail] 开始解析详情页...")
            try {
                val payloads = NextFParser.extractPayloadsFromHtml(html)
                Log.i(TAG, "[parseComicDetail] 提取到的 Payloads ID: ${payloads.keys}")

                // --- 修正点 ---
                // 根据你的日志 "D  从HTML中提取到Payload ID: 7---->注意这个才是要解析的内容"
                // 我们应该使用 Payload 7 的解析器 (parsePayload7)，而不是旧的 parsePayloadDetail

                payloads["7"]?.let { payload ->
                    Log.i(TAG, "[parseComicDetail] 找到 Payload 7，调用 parsePayload7 进行解析...")
                    // parsePayload7 内部会处理详情页结构 (在 ParsePayload7.kt 文件中定义)
                    // 它返回一个 List<Comic>，对于详情页，这个列表应该只包含一个元素。
                    val comicList = parsePayload7(payload)
                    if (comicList.isNotEmpty()) {
                        Log.i(TAG, "[parseComicDetail] parsePayload7 成功返回 ${comicList.size} 个项目。")
                        return comicList.first() // 返回第一个（也是唯一一个）漫画对象
                    } else {
                        Log.w(TAG, "[parseComicDetail] parsePayload7 被调用 (Payload 7)，但未返回任何 Comic 对象。")
                    }
                }

                // *** 备用逻辑 (原崩溃逻辑) ***
                // 如果 Payload 7 不存在, 尝试使用旧的 parsePayloadDetail (它导致了 'HL' 崩溃)
                Log.w(TAG, "[parseComicDetail] 未找到 Payload 7。回退到旧的 'firstOrNull' 逻辑 (有崩溃风险)...")
                payloads.values.firstOrNull()?.let { payload ->
                    Log.w(TAG, "[parseComicDetail] 正在使用 'firstOrNull' 逻辑调用旧的 parsePayloadDetail...")
                    return parsePayloadDetail(payload) // <-- 这是导致 'HL' 崩溃的旧函数
                }

                Log.e(TAG, "[parseComicDetail] 未找到 Payload 7 或任何其他 payload。")
                return null

            } catch (e: Exception) {
                Log.e(TAG, "[parseComicDetail] 解析详情页时出现顶层异常: ${e.message}", e)
                return null
            }
        }

        /**
         * [已废弃/备用] 旧的详情页解析器。
         * 这个解析器期望一个 'props.pageProps.article' 结构，但似乎
         * 1. 它被传递了错误的 payload (比如一个只包含 "HL" 的)
         * 2. 当前的详情页结构 (Payload 7) 更加复杂
         *
         * 它被保留作为备用，但 `parseComicDetail` 现在会优先调用 `parsePayload7`。
         */
        private fun parsePayloadDetail(payload: String): Comic? {
            Log.w(TAG, "[parsePayloadDetail_Old] 正在运行旧的详情页解析器...")
            val cleanPayload = NextFParser.cleanPayloadString(payload)

            // --- 增加保护，防止 "HL" 字符串导致崩溃 ---
            if (!cleanPayload.startsWith("{")) {
                Log.e(TAG, "[parsePayloadDetail_Old] 失败: Payload 不是一个 JSONObject (payload 可能是 'HL' 或其他非JSON值)。")
                Log.e(TAG, "[parsePayloadDetail_Old] 清理后的 Payload 内容: $cleanPayload")
                return null // 避免 'Value HL of type java.lang.String' 崩溃
            }

            return try {
                val json = JSONObject(cleanPayload)
                val props = json.getJSONObject("props")
                val pageProps = props.getJSONObject("pageProps")
                val article = pageProps.getJSONObject("article")

                val id = article.getInt("id").toString()
                val title = article.getString("title")
                val cover = article.getString("cover")
                val coverUrl = "https://cdn.imagedeliveries.com/$cover"

                val tagsArray = article.getJSONArray("tags")
                val tags: List<Tag> = (0 until tagsArray.length()).map { i ->
                    val tagJson = tagsArray.getJSONObject(i)
                    Tag(id = tagJson.getInt("id").toString(), name = tagJson.getString("name"))
                }

                val authorObject = article.optJSONObject("author")
                val author = authorObject?.getString("name") ?: "N/A"

                val pagesArray = article.getJSONArray("pages")
                val imageList = (0 until pagesArray.length()).map { i ->
                    val page = pagesArray.getJSONObject(i)
                    val image = page.getString("image")
                    "https://cdn.imagedeliveries.com/$image"
                }

                Log.i(TAG, "[parsePayloadDetail_Old] 成功解析 (ID: $id)")
                Comic(
                    id = id,
                    title = title,
                    coverUrl = coverUrl,
                    tags = tags,
                    author = author,
                    imageList = imageList,
                    language = "" // 旧结构没有语言
                )
            } catch (e: Exception) {
                Log.e(TAG, "[parsePayloadDetail_Old] 解析详情页 payload 失败: ${e.message}", e)
                null
            }
        }

        /**
         * 使用 Jsoup 从HTML的DOM结构中提取漫画列表。
         * ... (此方法保持不变)
         */
        private fun extractFromHtmlStructure(html: String): List<Comic> {
            val comics = mutableListOf<Comic>()
            try {
                val doc = Jsoup.parse(html)
                val comicLinks = doc.select("a[href*=/articles/]")
                Log.d(TAG, "[extractFromHtmlStructure] 通过 Jsoup 找到 ${comicLinks.size} 个潜在的漫画链接")

                comicLinks.forEachIndexed { index, element ->
                    // 确保链接内包含图片，这通常是漫画条目的标志
                    if (element.selectFirst("img") == null) {
                        return@forEachIndexed // 跳过不含图片的链接
                    }

                    try {
                        parseComicElement(element)?.let { comics.add(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "[extractFromHtmlStructure] 解析单个HTML元素 $index 失败: ${e.message}")
                    }
                }

                // 去重，防止重复添加
                return comics.distinctBy { it.id }
            } catch (e: Exception) {
                Log.e(TAG, "[extractFromHtmlStructure] Jsoup HTML解析失败: ${e.message}", e)
                return emptyList()
            }
        }

        /**
         * 从单个 a 标签元素中解析出 [Comic] 对象。
         * ... (此方法保持不变)
         */
        private fun parseComicElement(element: Element): Comic? {
            return try {
                val href = element.attr("href")
                val id = href.substringAfterLast("/").substringBefore("?").trim('"', '\'')

                val titleElement = element.selectFirst(".line-clamp-2")
                val imgElement = element.selectFirst("img")

                if (id.isNotBlank() && titleElement != null && imgElement != null) {
                    Comic(
                        id = id,
                        title = titleElement.text().trim(),
                        coverUrl = imgElement.attr("src"),
                        language = "日语" // 语言信息可能需要更复杂的逻辑来确定
                    )
                } else {
                    Log.w(TAG, "[parseComicElement] 无法从元素中完整解析漫画: id=$id, title=${titleElement?.text()}, img=${imgElement?.attr("src")}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "[parseComicElement] 解析单个漫画元素时出错: ${e.message}")
                null
            }
        }
    }
}
