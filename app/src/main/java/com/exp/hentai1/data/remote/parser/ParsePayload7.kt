package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.Tag
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import com.exp.hentai1.data.remote.parser.NextFParser.cleanPayloadString
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析数据块 7:
 * 此函数现在可以处理两种结构：
 * 1. 漫画列表（新着エロ漫画・エロ同人誌）
 * 2. 漫画详情页（article-details）
 *
 * @param payload 从 `extractPayloadsFromHtml` 获取的对应ID的payload字符串。
 * @return 包含解析出的漫画对象的 List。
 */
fun parsePayload7(payload: String): List<Comic> {
    // 【日志】确认函数被调用
    Log.i(TAG, "--- [7] 开始解析 (Payload 长度: ${payload.length}) ---")
    val comics = mutableListOf<Comic>()
    val jsonString = cleanPayloadString(payload)

    if (jsonString.isEmpty()) {
        Log.e(TAG, "[7] 清理后的 JSON 字符串为空。")
        return emptyList()
    }

    Log.d(TAG, "[7] 即将解析的 JSON: $jsonString")

    try {
        val topArray = JSONArray(jsonString)

        // 【日志】打印顶层数组的类型，用于调试
        Log.d(TAG, "[7] Top-level 元素类型: ${topArray.opt(0)?.javaClass?.name}, ${topArray.opt(1)?.javaClass?.name}")

        // 检查顶层数组的第二个元素，以确定是哪种数据结构
        when (val secondElement = topArray.opt(1)) {
            is JSONArray -> {
                // 结构 1: 漫画列表 (原有逻辑)
                Log.i(TAG, "[7] 检测到 [漫画列表] 结构 (JSONArray)。")
                parseComicList(secondElement, comics)
            }

            is String -> {
                // 结构 2: 漫画详情页 (新逻辑)
                Log.i(TAG, "[7] 检测到 [漫画详情页] 结构 (String: '$secondElement')。")
                // 详情页数据在顶层数组的第4个元素 (index 3) 中
                val detailsObject = topArray.optJSONObject(3)
                if (detailsObject != null) {
                    parseComicDetails(detailsObject, comics)
                } else {
                    Log.w(TAG, "[7] 详情页结构中未找到预期的 JSONObject (index 3)。")
                }
            }

            else -> {
                Log.e(TAG, "[7] 未知的数据块 7 结构类型: ${secondElement?.javaClass?.name}")
            }
        }

        return comics

    } catch (e: Exception) {
        Log.e(TAG, "[7] 解析数据块 7 失败: ${e.message}", e)
        return emptyList() // 发生异常时返回空列表
    } finally {
        Log.i(TAG, "--- [7] 解析结束 (共 ${comics.size} 项) ---")
    }
}

/**
 * 解析 [每日更新列表] 结构 (原 parsePayload7 逻辑)
 */
private fun parseComicList(secondElementArray: JSONArray, comics: MutableList<Comic>) {
    try {
        // 漫画列表数据位于第二个顶层元素（第二个 React 元素）的第四个位置
        val articlesContainer = secondElementArray.getJSONObject(3)
        val articlesArray = articlesContainer.getJSONArray("articles")

        Log.i(TAG, "[7-List] 找到 ${articlesArray.length()} 个新着漫画条目。")

        // 遍历并提取关键信息
        for (i in 0 until articlesArray.length()) {
            val comicObject = articlesArray.getJSONObject(i)
            val id = comicObject.getInt("id").toString() // 确保 ID 是 String
            val attributes = comicObject.getJSONObject("attributes")
            val title = attributes.getString("title")
            // 缩略图路径的开头需要补全，补全为完整的 URL
            val thumbnailRelativePath = attributes.getString("thumbnail")
            // 完整的基准 URL 是 https://cdn.imagedeliveries.com/
            val fullCoverUrl = "https://cdn.imagedeliveries.com/$thumbnailRelativePath"

            Log.d(TAG, "[7-List] ID: $id, 标题: $title")

            // 创建 Comic 对象并添加到列表中
            comics.add(
                Comic(
                    id = id,
                    title = title,
                    coverUrl = fullCoverUrl,
                    language = "日语" // 列表页默认为日语
                )
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "[7-List] 解析漫画列表结构失败: ${e.message}", e)
    }
}

/**
 * 解析 [漫画详情页] 结构 (新逻辑)
 */
private fun parseComicDetails(detailsObject: JSONObject, comics: MutableList<Comic>) {
    try {
        Log.d(TAG, "[7-Details] 开始解析详情页结构...")

        val flexChildren = detailsObject.getJSONArray("children")
            .getJSONArray(0) // id: "article-details"
            .getJSONObject(3).getJSONArray("children")
            .getJSONObject(3).getJSONArray("children")
            .getJSONObject(3).getJSONArray("children")

        Log.d(TAG, "[7-Details] 正在解析封面和ID...")
        val imageDiv = flexChildren.getJSONArray(0)
        val imageLink = getSingleElement(imageDiv.getJSONObject(3).getJSONArray("children"))
        val href = imageLink.getJSONObject(3).getString("href") // "/viewer?articleId=3615783&page=1"
        val imgTag = getSingleElement(imageLink.getJSONObject(3).getJSONArray("children"))
        val coverUrl = imgTag.getJSONObject(3).getString("src")
        val id = href.split("articleId=").getOrNull(1)?.split("&")?.getOrNull(0) ?: "N/A"
        Log.d(TAG, "[7-Details] ID: $id, Cover: $coverUrl")

        Log.d(TAG, "[7-Details] 正在解析标题...")
        val detailsDiv = flexChildren.getJSONArray(1)
        val titleArray = getSingleElement(detailsDiv.getJSONObject(3).getJSONArray("children"))
        val title = titleArray.getJSONObject(3).getString("children")
        Log.d(TAG, "[7-Details] 标题: $title")

        var language = "N/A" // 默认值
        var author: String? = null
        var circle: String? = null
        var parody: String? = null
        var category: String? = null
        var character: String? = null
        val tags = mutableListOf<Tag>()

        val tagInfoDiv = detailsDiv.getJSONObject(3).getJSONArray("children")
            .getJSONArray(1)

        val tagGroupsContainer = tagInfoDiv.optJSONObject(3)
            ?.optJSONArray("children")
            ?.optJSONObject(3)

        if (tagGroupsContainer == null) {
            Log.e(TAG, "[7-Details] 严重错误: tagGroupsContainer (div.flex.flex-col) 未找到!")
            return
        }

        val tagGroups = tagGroupsContainer.optJSONArray("children")
        if (tagGroups == null) {
            Log.e(TAG, "[7-Details] 严重错误: tagGroupsContainer.children 不是 JSONArray!")
            return
        }

        Log.i(TAG, "[7-Details] 找到 ${tagGroups.length()} 个信息组 (作者, 标签, 语言等)...")
        for (i in 0 until tagGroups.length()) {
            val group = tagGroups.optJSONArray(i) ?: continue
            val groupContent =
                group.optJSONObject(3) ?: continue
            val groupChildren = groupContent.optJSONArray("children") ?: continue

            val titleElement = groupChildren.optJSONArray(0) ?: continue
            val titleText = try {
                val childrenArray = titleElement.getJSONObject(3).getJSONArray("children")
                childrenArray.getString(0)
            } catch (e: Exception) {
                Log.w(TAG, "[7-Details] 解析信息组标题失败 at index $i: ${e.message}")
                Log.d(TAG, "[7-Details] 失败的 titleElement: $titleElement")
                continue
            }

            Log.d(TAG, "[7-Details] 正在处理信息组: '$titleText'")

            when (titleText) {
                "作者" -> {
                    author = extractValue(groupChildren.optJSONArray(1))
                    Log.i(TAG, "[7-Details]  - 作者: $author")
                }

                "作品言語" -> {
                    language = extractValue(groupChildren.optJSONArray(1))
                    if (language.isEmpty() || language == "N/A") {
                        Log.w(TAG, "[7-Details]  - '作品言語' 未找到或为 N/A, 保持: $language")
                    } else {
                        Log.i(TAG, "[7-Details]  - 作品言語: $language")
                    }
                }

                "タグ" -> {
                    Log.d(TAG, "[7-Details] 正在解析 'タグ'...")
                    val tagsArray: JSONArray
                    val firstValue = groupChildren.optJSONArray(1)
                    if (groupChildren.length() == 2 && firstValue != null) {
                        Log.d(TAG, "[7-Details] 检测到 'タグ' 为嵌套结构。")
                        tagsArray = firstValue
                    } else {
                        Log.d(TAG, "[7-Details] 检测到 'タグ' 为扁平结构。")
                        tagsArray = JSONArray()
                        for (j in 1 until groupChildren.length()) {
                            groupChildren.optJSONArray(j)?.let { tagsArray.put(it) }
                        }
                    }

                    Log.i(TAG, "[7-Details] 找到 ${tagsArray.length()} 个潜在的标签条目。")

                    for (j in 0 until tagsArray.length()) {
                        val valueArray = tagsArray.optJSONArray(j) ?: continue
                        try {
                            val innerValueArray = getSingleElement(valueArray)
                            val elementType = innerValueArray.optString(1)

                            if (elementType == "\$L12") {
                                val elementContainer = innerValueArray.optJSONObject(3) ?: continue

                                // 解析 Tag ID
                                val href = elementContainer.optString("href")
                                val tagId = href.split("/").lastOrNull() ?: ""

                                // 解析 Tag Name
                                val childrenArray = elementContainer.optJSONArray("children") ?: continue
                                val element = getSingleElement(childrenArray)
                                val tagName = element.optJSONObject(3)
                                    ?.optString("children", "")
                                    ?.trim()
                                    ?.replace("\\r", "") ?: ""

                                if (tagId.isNotEmpty() && tagName.isNotEmpty() && tagName != "N/A") {
                                    tags.add(Tag(id = tagId, name = tagName))
                                    Log.d(TAG, "[7-Details]  - 添加标签: [ID: $tagId, Name: $tagName]")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[7-Details] 解析单个 'タグ' 失败 at index $j: ${e.message}")
                            // 继续处理下一个标签，不要中断
                            continue
                        }
                    }
                    Log.i(TAG, "[7-Details] 'タグ' 解析完成，共 ${tags.size} 个。")
                }
                
                "サークル" -> {
                    circle = extractValue(groupChildren.optJSONArray(1))
                    Log.i(TAG, "[7-Details]  - サークル: $circle")
                }

                "原作" -> {
                    parody = extractValue(groupChildren.optJSONArray(1))
                    Log.i(TAG, "[7-Details]  - 原作: $parody")
                }

                "カテゴリー" -> {
                    category = extractValue(groupChildren.optJSONArray(1))
                    Log.i(TAG, "[7-Details]  - カテゴリー: $category")
                }

                "キャラクター" -> {
                    character = extractValue(groupChildren.optJSONArray(1))
                    Log.i(TAG, "[7-Details]  - キャラクター: $character")
                }
            }
        }


        Log.i(TAG, "[7-Details] 解析详情页完成。")
        Log.i(TAG, "[7-Details] ID: $id, 标题: $title, 语言: $language, 作者: $author, 封面: $coverUrl")
        // 【修改】更新日志，使其打印 tag 的 name
        Log.i(TAG, "[7-Details] 标签 (${tags.size} 个): ${tags.joinToString { it.name }}")

        comics.add(
            Comic(
                id = id,
                title = title,
                coverUrl = coverUrl,
                language = language,
                tags = tags,
                author = author,
                circle = circle,
                parody = parody,
                category = category,
                character = character
            )
        )

    } catch (e: Exception) {
        Log.e(TAG, "[7-Details] 解析漫画详情页结构失败: ${e.message}", e)
    }
}

/**
 * 【新】辅助函数，用于从 "值" 元素中提取文本。
 * 它可以处理 [["$", "$L12", ...]] (链接) 和 ["$", "p", ...] (文本) 两种情况。
 */
private fun extractValue(valueArray: JSONArray?): String {
    if (valueArray == null) return ""
    return try {
        val valueElement = getSingleElement(valueArray)
        val elementType = valueElement.optString(1)

        when (elementType) {
            "\$L12" -> {
                val elementContainer = valueElement.optJSONObject(3)
                val element =
                    getSingleElement(elementContainer.getJSONArray("children"))
                element.getJSONObject(3).getString("children").trim().replace("\\r", "")
            }

            "p" -> {
                when (val valueNode = valueElement.opt(3)) {
                    is JSONObject -> valueNode.optString("children", "").trim()
                    is String -> valueNode.trim()
                    else -> ""
                }
            }

            else -> ""
        }
    } catch (e: Exception) {
        Log.w(TAG, "[7-Details] extractValue 失败: ${e.message}")
        ""
    }
}


/**
 * 智能获取 JSONArray 的第一个元素。
 * 专门处理数据源中 "children" 结构不一致的问题。
 *
 * case 1: "children": [ ["$", "div", ...] ] (嵌套)
 * case 2: "children": [ "$", "div", ...] (非嵌套)
 *
 * @param array "children" 对应的 JSONArray
 * @return 总是返回 [Element] 数组 (e.g., ["$", "div", ...])
 */
private fun getSingleElement(array: JSONArray): JSONArray {
    if (array.length() == 0) return JSONArray() // 处理空数组
    val firstElement = array.opt(0)

    return if (firstElement is JSONArray) {
        firstElement
    } else {
        array
    }
}