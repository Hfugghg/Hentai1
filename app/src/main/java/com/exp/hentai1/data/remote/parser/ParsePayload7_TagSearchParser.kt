package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import com.exp.hentai1.data.remote.parser.NextFParser.cleanPayloadString
import org.json.JSONArray
import org.json.JSONObject


/**
 * 【新增】专门用于解析 [标签搜索结果] 的顶层函数。
 * 它会找到 grid 元素或 $L19 元素并调用 `parseComicTagSearchList`。
 */
fun parsePayload7ForTagSearch(payload: String): List<Comic> {
    Log.i(TAG, "--- [7-TagSearch] 开始解析 (Payload 长度: ${payload.length}) ---")
    val comics = mutableListOf<Comic>()
    val jsonString = cleanPayloadString(payload)

    if (jsonString.isEmpty()) {
        Log.e(TAG, "[7-TagSearch] 清理后的 JSON 字符串为空。")
        return emptyList()
    }

    try {
        val topArray = JSONArray(jsonString)
        var containerElement: JSONArray? = null // 【修改】通用容器，可以是 "grid" 或 "$L19"

        // --- 【修复开始】---

        // 【新】尝试解析 "日本語" 结构 (e.g., 日本語标签数据.txt)
        // 结构: topArray[0] 就是 $L19 元素
        val firstElement = topArray.optJSONArray(0)
        if (firstElement != null && "\$L19" == firstElement.optString(1)) {
            val dataObject = firstElement.optJSONObject(3)
            // 确保它确实包含了 'articles' 键
            if (dataObject != null && dataObject.has("articles")) {
                containerElement = firstElement
                Log.d(TAG, "[7-TagSearch] 在 topArray[0] 找到 '\$L19' (含articles) container (日本語结构)。")
            }
        }

        // 【旧】如果 topArray[0] 不是，则尝试 "巨乳" 结构 (e.g., New 1.txt)
        // 结构: topArray[3].children[0] 是 $L19 元素
        if (containerElement == null) {
            // 标签搜索页的结构与普通搜索页非常相似，都是从 topArray[3] 开始
            val childrenArray = topArray.optJSONObject(3)?.optJSONArray("children")

            if (childrenArray != null) {
                try {
                    // 【新】优先尝试在索引 0 查找 "$L19" (基于 New 1.txt)
                    val firstChild = childrenArray.optJSONArray(0)
                    if (firstChild != null && "\$L19" == firstChild.optString(1)) {
                        val dataObject = firstChild.optJSONObject(3)
                        // 确保它确实包含了 'articles' 键
                        if (dataObject != null && dataObject.has("articles")) {
                            containerElement = firstChild
                            Log.d(TAG, "[7-TagSearch] 在 children[0] 找到 '\$L19' (含articles) container (巨乳结构)。")
                        }
                    }

                    // 【旧】如果 children[0] 不是，则尝试查找 "grid" (原始逻辑)
                    if (containerElement == null) {
                        for (i in 0 until childrenArray.length()) {
                            val child = childrenArray.optJSONArray(i)
                            if (child != null) {
                                val className = child.optJSONObject(3)?.optString("className", "") ?: ""
                                if (className.contains("grid")) {
                                    containerElement = child
                                    Log.d(TAG, "[7-TagSearch] 在 childrenArray 索引 $i 处找到 'grid' container (原始逻辑)。")
                                    break
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[7-TagSearch] 解析 container 结构失败: ${e.message}")
                }
            } else {
                // 只有在两种主要结构都失败时才记录这个错误
                Log.e(TAG, "[7-TagSearch] topArray[3].children 未找到 (结构)，且 topArray[0] 也不是有效 container。")
            }
        }

        // --- 【修复结束】---

        if (containerElement != null) {
            // parseComicTagSearchList 已支持 "articles" 键，无需修改
            parseComicTagSearchList(containerElement, comics)
        } else {
            // 【修改】更新日志
            Log.e(TAG, "[7-TagSearch] 'grid' 或 '\$L19' (含articles) container 均未找到！")
        }

        return comics

    } catch (e: Exception) {
        Log.e(TAG, "[7-TagSearch] 解析失败: ${e.message}", e)
        return emptyList()
    } finally {
        Log.i(TAG, "--- [7-TagSearch] 解析结束 (共 ${comics.size} 项) ---")
    }
}

/**
 * 【修改】解析 [同步标签漫画列表] 结构
 *
 * @param containerElement 包含漫画条目的 grid 元素或 $L19 元素
 */
internal fun parseComicTagSearchList(containerElement: JSONArray, comics: MutableList<Comic>) {
    try {
        // containerElement 是 ["$", "div", ... {className: "grid..."}] 或 ["$", "$L19", ... {"articles": [...]}]
        val dataObject = containerElement.getJSONObject(3)
        var articlesArray: JSONArray? = null

        // 【新】优先尝试 "articles" 键 (用于 $L19 结构, e.g., New 1.txt) 
        if (dataObject.has("articles")) {
            articlesArray = dataObject.optJSONArray("articles")
            if (articlesArray != null) {
                Log.d(TAG, "[7-TagSearch] 在 container[3] 中找到 'articles' 数组。")
            }
        }

        // 【旧】如果没有 "articles"，则尝试 "children" 键 (用于 'grid' 结构)
        if (articlesArray == null && dataObject.has("children")) {
            articlesArray = dataObject.optJSONArray("children")
            if (articlesArray != null) {
                Log.d(TAG, "[7-TagSearch] 在 container[3] 中找到 'children' 数组 (原始逻辑)。")
            }
        }

        if (articlesArray == null) {
            Log.e(TAG, "[7-TagSearch] 在 container[3] 中未找到 'articles' 或 'children' 数组。")
            return
        }

        if (articlesArray.length() == 0) {
            Log.i(TAG, "[7-TagSearch] 找到 0 个搜索结果条目。")
            return
        }

        Log.d(TAG, "[7-TagSearch] List 数组长度: ${articlesArray.length()}, 首个元素类型: ${articlesArray.opt(0)?.javaClass?.name}")

        // 检查第一个元素以确定结构
        when (articlesArray.opt(0)) {
            // 【新】案例 3：JSON 对象结构 (如 New 1.txt 所示) 
            is JSONObject -> {
                Log.i(TAG, "[7-TagSearch] 检测到 [JSONObject] 结构。")
                for (i in 0 until articlesArray.length()) {
                    val comicObject = articlesArray.optJSONObject(i) ?: continue
                    // 使用新的辅助函数
                    parseTagSearchComicObject(comicObject)?.let { comics.add(it) }
                }
            }

            // 案例 1：嵌套结构 (原始逻辑)
            is JSONArray -> {
                Log.i(TAG, "[7-TagSearch] 检测到 [嵌套 JSONArray] 结构。")
                for (i in 0 until articlesArray.length()) {
                    val comicElement = articlesArray.optJSONArray(i) ?: continue

                    val elementType = comicElement.optString(1)

                    if (elementType == "\$L19") {
                        parseTagSearchComicElement(comicElement)?.let { comics.add(it) }
                    } else {
                        Log.d(TAG, "[7-TagSearch] 跳过嵌套结构中的非漫画元素 (Type: $elementType)")
                    }
                }
            }

            // 2：扁平结构 (原始逻辑)
            is String -> {
                Log.i(TAG, "[7-TagSearch] 检测到 [扁平 String] 结构。")

                var i = 0
                while (i < articlesArray.length()) {
                    val startTag = articlesArray.optString(i)
                    val typeTag = articlesArray.optString(i + 1)

                    if (startTag == "$" && typeTag == "\$L19" && i + 3 < articlesArray.length()) {
                        val dataObjectFlat = articlesArray.optJSONObject(i + 3) // 避免变量重名

                        if (dataObjectFlat != null) {
                            try {
                                val comicElement = JSONArray().apply {
                                    put(startTag)
                                    put(typeTag)
                                    put(articlesArray.optString(i + 2)) // "ID"
                                    put(dataObjectFlat) // 确保是 JSONObject
                                }
                                parseTagSearchComicElement(comicElement)?.let { comics.add(it) }

                                i += 4

                            } catch (e: Exception) {
                                Log.e(TAG, "[7-TagSearch] 处理扁平结构条目失败 at index $i: ${e.message}", e)
                                i += 4
                            }
                        } else {
                            Log.w(TAG, "[7-TagSearch] 检测到 \"\$L19\", 但第 4 个元素不是 JSONObject. 跳过 4 元素. Index: $i")
                            i += 4
                        }
                    } else {
                        i++
                    }
                }
            }

            // 未知结构
            else -> {
                Log.e(TAG, "[7-TagSearch] 未知的 articlesArray 结构类型: ${articlesArray.opt(0)?.javaClass?.name}")
            }
        }

    } catch (e: Exception) {
        Log.e(TAG, "[7-TagSearch] 解析漫画标签搜索列表结构失败: ${e.message}", e)
    }
}

/**
 * 【新】辅助函数：从标签搜索列表的单个 "articles" JSON 对象中解析 Comic 对象
 * (用于 New 1.txt 结构)
 */
private fun parseTagSearchComicObject(comicObject: JSONObject): Comic? {
    try {
        val id = comicObject.optString("id") // "id" 在JSON中是数字, 但 optString 会转换 
        if (id.isEmpty()) {
            Log.w(TAG, "[7-TagSearch-HelperObj] 无法解析 ID: $comicObject")
            return null
        }
        val attributes = comicObject.optJSONObject("attributes") 
        if (attributes == null) {
            Log.w(TAG, "[7-TagSearch-HelperObj] 'attributes' 未找到: $comicObject")
            return null
        }

        val title = attributes.optString("title", "N/A") 
        val thumbnailUrl = attributes.optString("thumbnail")
        val coverUrl = NetworkUtils.getCoverUrl(thumbnailUrl)

        Log.d(TAG, "[7-TagSearch-HelperObj] ID: $id, 标题: $title")

        return Comic(
            id = id,
            title = title,
            coverUrl = coverUrl, // 存储完整的URL
            artists = emptyList(),
            groups = emptyList(),
            parodies = emptyList(),
            characters = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            categories = emptyList(),
        )
    } catch (e: Exception) {
        Log.e(TAG, "[7-TagSearch-HelperObj] 解析单个 comic object 失败: ${e.message}", e)
        Log.d(TAG, "[7-TagSearch-HelperObj] 失败的 Object: $comicObject")
        return null
    }
}


/**
 * 辅助函数：从标签搜索列表的单个 comicElement JSON 数组中解析 Comic 对象
 * (用于原始的 嵌套/扁平 结构)
 */
private fun parseTagSearchComicElement(comicElement: JSONArray): Comic? {
    try {
        val id = comicElement.getString(2) // e.g., "851469"
        val comicObject = comicElement.getJSONObject(3)
        val comicChildren = comicObject.getJSONArray("children") // [ [COVER_DIV], [DETAILS_DIV] ]

        // 1. 解析封面
        val imageDiv = comicChildren.getJSONArray(0) // [COVER_DIV]
        val imgTag = imageDiv.getJSONObject(3)
            .getJSONArray("children") // ["$", "img", null, {...}]
        val coverUrl = NetworkUtils.getCoverUrl(imgTag.getJSONObject(3).getString("src"))

        // 2. 解析标题
        val detailsDiv = comicChildren.getJSONArray(1) // [DETAILS_DIV]
        val contentDiv = detailsDiv.getJSONObject(3)
            .getJSONArray("children") // [ [FLAG_DIV], [CONTENT_DIV] ]
            .getJSONArray(1) // [CONTENT_DIV]
        val titleDiv = contentDiv.getJSONObject(3)
            .getJSONArray("children") // [ [TITLE_DIV], [AUTHOR_DIV] ]
            .getJSONArray(0) // [TITLE_DIV]
        val title = titleDiv.getJSONObject(3).getString("children")

        Log.d(TAG, "[7-TagSearch-Helper] ID: $id, 标题: $title")

        return Comic(
            id = id,
            title = title,
            coverUrl = coverUrl,
            artists = emptyList(),
            groups = emptyList(),
            parodies = emptyList(),
            characters = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            categories = emptyList(),
        )
    } catch (e: Exception) {
        Log.e(TAG, "[7-TagSearch-Helper] 解析单个 comic element 失败: ${e.message}", e)
        Log.d(TAG, "[7-TagSearch-Helper] 失败的 Element: $comicElement")
        return null
    }
}
