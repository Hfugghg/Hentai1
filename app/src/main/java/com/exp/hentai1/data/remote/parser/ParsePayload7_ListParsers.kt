package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import org.json.JSONArray

/**
 * 解析 [每日更新列表] 结构 (原 parsePayload7 逻辑)
 *
 * @param secondElementArray 传入的 JSON 数组。
 * (注意：为了解析 lastPage，此参数【必须】是【根数组】)
 * @param comics (out) 这是一个输出参数，函数将把解析到的漫画对象添加到这个 MutableList 中
 * @return 最后一页的页码 (Int?)，如果解析失败则返回 null
 */
internal fun parseComicList(secondElementArray: JSONArray, comics: MutableList<Comic>): Int? {
//    // 添加一个打印完整secondElementArray的log,需要换行处理过大log
//    val jsonString = secondElementArray.toString(2) // 使用缩进格式化JSON
//    val tag = "ComicListDebug"
//
//    // 处理过长的日志，分段打印
//    val maxLogLength = 4000
//    if (jsonString.length <= maxLogLength) {
//        Log.d(tag, "完整 secondElementArray: \n$jsonString")
//    } else {
//        Log.d(tag, "完整 secondElementArray (分段输出):")
//        var i = 0
//        while (i < jsonString.length) {
//            val end = minOf(i + maxLogLength, jsonString.length)
//            Log.d(tag, "分段 ${i / maxLogLength + 1}: ${jsonString.substring(i, end)}")
//            i += maxLogLength
//        }
//    }

    // --- 1. 解析漫画列表 (!!! 按照你的要求，原封不动地保留 !!!) ---
    try {
        // 既然 secondElementArray 是 根数组 (topArray),
        // 那么漫画列表就在 根数组[1] (即 $L19 块)
        val comicListBlock = secondElementArray.getJSONArray(1)

        // 漫画列表数据位于 $L19 块的第四个位置 (index 3)
        val articlesContainer = comicListBlock.getJSONObject(3)
        val articlesArray = articlesContainer.getJSONArray("articles")
        // #################################

        Log.i(TAG, "[7-List] 找到 ${articlesArray.length()} 个新着漫画条目。")

        // 遍历并提取关键信息
        for (i in 0 until articlesArray.length()) {
            val comicObject = articlesArray.getJSONObject(i)
            val id = comicObject.getInt("id").toString() // 确保 ID 是 String
            val attributes = comicObject.getJSONObject("attributes")
            val title = attributes.getString("title")
            // 缩略图路径的开头需要补全，补全为完整的 URL
            val thumbnailRelativePath = attributes.getString("thumbnail")
            val fullCoverUrl = NetworkUtils.getCoverUrl(thumbnailRelativePath)

            Log.d(TAG, "[7-List] ID: $id, 标题: $title")

            // 创建 Comic 对象并添加到列表中
            comics.add(
                Comic(
                    id = id,
                    title = title,
                    coverUrl = fullCoverUrl,
                    artists = emptyList(),
                    groups = emptyList(),
                    parodies = emptyList(),
                    characters = emptyList(),
                    tags = emptyList(),
                    languages = emptyList(),
                    categories = emptyList(),
                )
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "[7-List] 解析漫画列表结构失败: ${e.message}", e)
        // 警告：如果 'secondElementArray' 是根数组，这里会失败，这是预期的。
        // ^^^ 这个警告现在是一个真正的错误了
    }

    // --- 2. 解析 "Last Page" (添加了针对 null 错误的健壮性) ---
    // (这部分逻辑现在是正确的，因为 secondElementArray 是 根数组)
    var lastPage: Int? = null
    try {
        // nav 元素在 根数组[2]
        val navElement = secondElementArray.getJSONArray(2) // [ "$", "nav", ... ]

        // 沿着 JSON 树向下查找
        val navChildrenObj = navElement.getJSONObject(3)

        // navChildrenObj.getJSONArray("children") 返回的就是 ul 元素数组 [ "$", "ul", null, {...} ]
        val ulElement = navChildrenObj.getJSONArray("children") // [ "$", "ul", ... ]

        val ulChildrenObj = ulElement.getJSONObject(3) //
        val ulChildrenArray = ulChildrenObj.getJSONArray("children") // [null, [...pages...], [...controls...]]

        // !!! 关键修复: 检查索引 2 (controls) 是否为 null !!!
        if (ulChildrenArray.isNull(2)) {
            Log.i(TAG, "[7-List] 未找到 'next/last' 按钮组 (ulChildrenArray[2] is null)，尝试从页码列表获取。")

            // 如果 controls 不存在 (可能只有1页)，则"最后一页"就是页码列表的最后一项
            val pagesArray = ulChildrenArray.getJSONArray(1) // [...pages...]
            if (pagesArray.length() > 0) {
                // 获取页码列表的最后一个 <li>
                val lastPageLi = pagesArray.getJSONArray(pagesArray.length() - 1)
                val lastPageLiChildrenObj = lastPageLi.getJSONObject(3)

                // "children" 数组 [ "$", "a", ... ] 本身就是 <a> 元素
                val lastPageA = lastPageLiChildrenObj.getJSONArray("children")
                val lastPageA_Obj = lastPageA.getJSONObject(3)

                // 提取 href, 例如: "/?page=1"
                val href = lastPageA_Obj.getString("href")
                val pageNumStr = href.split("=").lastOrNull()
                if (pageNumStr != null) {
                    lastPage = pageNumStr.toIntOrNull()
                    Log.i(TAG, "[7-List] 从页码列表解析到最后一页 (Last Page): $lastPage")
                }
            } else {
                Log.i(TAG, "[7-List] 页码列表也为空。")
                lastPage = 1 // 假设至少有1页
            }
        } else {
            // --- 正常路径: "controls" 数组存在 ---
            val nextLastLinksArray = ulChildrenArray.getJSONArray(2) // [ [...next...], [...last...] ]

            // 同样检查 "last page" 按钮 (index 1) 是否存在
            if (nextLastLinksArray.isNull(1)) {
                Log.i(TAG, "[7-List] 未找到 'last page' 按钮 (nextLastLinksArray[1] is null)，可能页数较少。")

                // 再次回退，使用页码列表的最后一项
                val pagesArray = ulChildrenArray.getJSONArray(1)
                if (pagesArray.length() > 0) {
                    val lastPageLi = pagesArray.getJSONArray(pagesArray.length() - 1)
                    val lastPageLiChildrenObj = lastPageLi.getJSONObject(3)

                    val lastPageA = lastPageLiChildrenObj.getJSONArray("children")
                    val lastPageA_Obj = lastPageA.getJSONObject(3)
                    val href = lastPageA_Obj.getString("href")
                    val pageNumStr = href.split("=").lastOrNull()
                    lastPage = pageNumStr?.toIntOrNull()
                    Log.i(TAG, "[7E-List] 从页码列表解析到最后一页 (Last Page): $lastPage")
                }
            } else {
                // --- 完美路径: 找到了 "last page" 按钮 ---
                val lastPageLi = nextLastLinksArray.getJSONArray(1) // [ "$", "li", ... ]
                val lastPageLiChildrenObj = lastPageLi.getJSONObject(3)

                val lastPageA = lastPageLiChildrenObj.getJSONArray("children") // [ "$", "a", ... ]
                val lastPageA_Obj = lastPageA.getJSONObject(3)

                // "aria-label" 为 "last page"
                // Log.d(TAG, "[7-List] Last Page Aria Label: ${lastPageA_Obj.getString("aria-label")}")

                val href = lastPageA_Obj.getString("href") // "/?page=16824"

                val pageNumStr = href.split("=").lastOrNull()
                if (pageNumStr != null) {
                    lastPage = pageNumStr.toIntOrNull()
                    Log.i(TAG, "[7-List] 从 'last page' 按钮解析到最后一页: $lastPage")
                } else {
                    Log.w(TAG, "[7-List] 无法从 'last page' 的 href '$href' 中提取页码")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "[7-List] 解析 'last page' 失败 (常规流程): ${e.message}", e)
    }

    // 返回解析到的页码（可能为 null）
    return lastPage
}

/**
 * 【新】解析 [漫画排行列表] 结构 (e.g., 人気エロ漫画)
 * @param containerArray 顶层数组的第一个元素 (["$", "div", ...])
 */
internal fun parseComicRankList(containerArray: JSONArray, comics: MutableList<Comic>) {
    try {
        // 路径参考 New 1.txt
        val gridElement = containerArray.getJSONObject(3)
            .getJSONArray("children")
            .getJSONArray(2) // ["$", "div", ... {className: "grid..."}]

        val articlesArray = gridElement.getJSONObject(3)
            .getJSONArray("children") // [COMIC_1, COMIC_2, ...]

        Log.i(TAG, "[7-Rank] 找到 ${articlesArray.length()} 个排行漫画条目。")

        for (i in 0 until articlesArray.length()) {
            //
            val comicElement = getSingleElement(articlesArray.getJSONArray(i)) // ["$", "$L11", "ID", {...}]

            val id = comicElement.getString(2) //
            val comicObject = comicElement.getJSONObject(3)
            val comicChildren = comicObject.getJSONArray("children")

            // 1. 解析封面
            val imageDiv = getSingleElement(comicChildren.getJSONArray(0))
            val imgTag = getSingleElement(
                imageDiv.getJSONObject(3)
                    .getJSONArray("children")
            ) // ["$", "img", ...]
            val coverUrl = NetworkUtils.getCoverUrl(imgTag.getJSONObject(3).getString("src")) // 已经是完整 URL

            // 2. 解析标题
            val detailsDiv = getSingleElement(comicChildren.getJSONArray(1))
            val titleHolder = getSingleElement( // This is ["$", "div", {title:"..."}]
                detailsDiv.getJSONObject(3)
                    .getJSONArray("children") // [ [FLAG], [CONTENT] ]
                    .getJSONArray(1) // [CONTENT] = ["$", "div", {children: [ [TITLE], [AUTHOR] ]}]
                    .getJSONObject(3)
                    .getJSONArray("children") // [ [TITLE], [AUTHOR] ]
            )

            val title = titleHolder.getJSONObject(3).getString("children")

            Log.d(TAG, "[7-Rank] ID: $id, 标题: $title")

            comics.add(
                Comic(
                    id = id,
                    title = title,
                    coverUrl = coverUrl,
                    artists = emptyList(),
                    groups = emptyList(),
                    parodies = emptyList(),
                    characters = emptyList(),
                    tags = emptyList(),
                    languages = emptyList(), // 列表页默认为日语
                    categories = emptyList(),
                )
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "[7-Rank] 解析漫画排行列表结构失败: ${e.message}", e)
    }
}

/**
 * 【新】解析 [漫画搜索列表] 结构 (v4 - 修复结构不一致崩溃)
 *
 * 此版本通过检查第一个元素来处理 "嵌套" 和 "扁平" 两种数据结构。
 * - 嵌套: [ [COMIC_1], [COMIC_2], ... ] (如 New 1.txt 所示)
 * - 扁平: [ "$", "$L11", "ID1", {...}, "$", "$L11", "ID2", {...}, ... ] (如崩溃日志所示)
 *
 * @param gridElement 包含漫画条目的 grid 元素 (["$", "div", ... {className: "grid..."}])
 */
internal fun parseComicSearchList(gridElement: JSONArray, comics: MutableList<Comic>) {
    try {
        // gridElement 就是 ["$", "div", ... {className: "grid..."}]
        val articlesArray = gridElement.getJSONObject(3)
            .getJSONArray("children") // [COMIC_1, COMIC_2, ..., PAGINATION_UL]

        if (articlesArray.length() == 0) {
            Log.i(TAG, "[7-Search] 找到 0 个搜索结果条目。")
            return
        }

        Log.d(TAG, "[7-Search] Grid children 数组长度: ${articlesArray.length()}")

        // 检查第一个元素以确定结构
        when (articlesArray.opt(0)) {
            // 案例 1：嵌套结构 (如 New 1.txt 所示)
            is JSONArray -> {
                Log.i(TAG, "[7-Search] 检测到 [嵌套] 结构。找到 ${articlesArray.length()} 个条目。")
                for (i in 0 until articlesArray.length()) {
                    val comicElement = articlesArray.optJSONArray(i) ?: continue

                    // 【!! 修正 !!】
                    // 在解析之前，检查这是否是一个漫画条目 (e.g., ["$", "$L11", ...])
                    // 而不是一个分页元素 (e.g., ["$", "nav", ...])
                    val elementType = comicElement.optString(1)

                    if (elementType == "\$L11") {
                        // 确认是漫画条目，才进行解析
                        parseSearchComicElement(comicElement)?.let { comics.add(it) }
                    } else {
                        // 找到了一个非 $L11 的元素 (很可能是分页 <nav>)
                        Log.d(TAG, "[7-Search] 跳过嵌套结构中的非漫画元素 (Type: $elementType)")
                    }
                }
            }

            // 2：扁平结构 (如崩溃日志所示)
            is String -> {
                Log.i(TAG, "[7-Search] 检测到 [扁平] 结构。(Total elements: ${articlesArray.length()})")

                var i = 0
                while (i < articlesArray.length()) {
                    val startTag = articlesArray.optString(i)
                    val typeTag = articlesArray.optString(i + 1)

                    // 检查是否为漫画链接元素的开始: ["$", "$L11", "ID", {DATA}]
                    // **【修复点 1】确保 i+3 在界内，并且 i+3 确实是 JSONObject**
                    if (startTag == "$" && typeTag == "\$L11" && i + 3 < articlesArray.length()) {
                        val dataObject = articlesArray.optJSONObject(i + 3)

                        if (dataObject != null) {
                            try {
                                // 从扁平数组手动重建 comicElement: ["$", "$L11", "ID", {...}]
                                val comicElement = JSONArray().apply {
                                    put(startTag)
                                    put(typeTag)
                                    put(articlesArray.optString(i + 2)) // "ID"
                                    put(dataObject) // 确保是 JSONObject
                                }
                                parseSearchComicElement(comicElement)?.let { comics.add(it) }

                                // 成功处理了一个 4 元素的漫画条目，跳过这 4 个元素
                                i += 4

                            } catch (e: Exception) {
                                Log.e(TAG, "[7-Search] 处理扁平结构条目失败 at index $i: ${e.message}", e)
                                // 发生错误时，保守地跳过 4 个元素，尝试下一个
                                i += 4
                            }
                        } else {
                            // 发现是 $L11，但第 4 个元素不是 JSONObject，说明结构异常或遇到非漫画元素 (如分页)
                            Log.w(TAG, "[7-Search] 检测到 \"\$L11\", 但第 4 个元素不是 JSONObject. 跳过 4 元素. Index: $i")
                            i += 4 // 跳过 4 个元素，避免无限循环
                        }
                    } else {
                        // 不是漫画条目的开始，或者长度不足 4，跳到下一个元素
                        i++
                    }
                }
            }

            // 未知结构
            else -> {
                Log.e(TAG, "[7-Search] 未知的 articlesArray 结构类型: ${articlesArray.opt(0)?.javaClass?.name}")
            }
        }

    } catch (e: Exception) {
        Log.e(TAG, "[7-Search] 解析漫画搜索列表结构失败: ${e.message}", e)
    }
}

/**
 * 辅助函数：从搜索列表的单个 comicElement JSON 数组中解析 Comic 对象
 */
private fun parseSearchComicElement(comicElement: JSONArray): Comic? {
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

        Log.d(TAG, "[7-Search-Helper] ID: $id, 标题: $title")

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
        Log.e(TAG, "[7-Search-Helper] 解析单个 comic element 失败: ${e.message}", e)
        Log.d(TAG, "[7-Search-Helper] 失败的 Element: $comicElement")
        return null
    }
}
