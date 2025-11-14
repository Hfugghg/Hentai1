package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.Tag
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析 [漫画详情页] 结构 (新逻辑)
 */
internal fun parseComicDetails(detailsObject: JSONObject, comics: MutableList<Comic>) {
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

        // --- 【修改】标准化变量名 ---
        val languages = mutableListOf<Tag>()
        val tags = mutableListOf<Tag>()
        val artists = mutableListOf<Tag>()
        val groups = mutableListOf<Tag>()
        val parodies = mutableListOf<Tag>()
        val categories = mutableListOf<Tag>()
        val characters = mutableListOf<Tag>()
        // --- 【修改结束】---

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

            // --- 【修改】添加多语言支持 (CN, EN)，扩展 when 语句 ---
            when (titleText) {
                "作者", "Artists" -> { // "作者" (JP, CN)
                    // 对应 'artists' -> 'artistsId'
                    // artists.addAll(extractTagLikeItems(groupChildren, ::getSingleElement)) // <-- 删除这一行

                    // --- 【修改】粘贴 "Characters" 或 "Tags" 的逻辑并修改变量名 ---
                    Log.d(TAG, "[7-Details] 正在解析 '作者' (Artists)...")
                    val artistsArray: JSONArray
                    val firstValue = groupChildren.optJSONArray(1)

                    // 检查是 "嵌套" 结构还是 "扁平" 结构
                    if (groupChildren.length() == 2 && firstValue != null) {
                        Log.d(TAG, "[7-Details] 检测到 '作者' 为嵌套结构。")
                        artistsArray = firstValue
                    } else {
                        Log.d(TAG, "[7-Details] 检测到 '作者' 为扁平结构。")
                        artistsArray = JSONArray()
                        // 从索引 1 开始循环，获取所有后续条目
                        for (j in 1 until groupChildren.length()) {
                            groupChildren.optJSONArray(j)?.let { artistsArray.put(it) }
                        }
                    }

                    Log.i(TAG, "[7-Details] 找到 ${artistsArray.length()} 个潜在的作者条目。")

                    // 遍历所有找到的作者
                    for (j in 0 until artistsArray.length()) {
                        val valueArray = artistsArray.optJSONArray(j) ?: continue
                        try {
                            val innerValueArray = getSingleElement(valueArray)
                            val elementType = innerValueArray.optString(1)

                            if (elementType == "\$L12") {
                                val elementContainer = innerValueArray.optJSONObject(3) ?: continue

                                // 解析 Artist ID
                                val href = elementContainer.optString("href")
                                val artistId = href.split("/").lastOrNull() ?: ""

                                // 解析 Artist Name
                                val childrenArray = elementContainer.optJSONArray("children") ?: continue
                                val element = getSingleElement(childrenArray)
                                val artistName = element.optJSONObject(3)
                                    ?.optString("children", "")
                                    ?.trim()
                                    ?.replace("\\r", "") ?: ""

                                if (artistId.isNotEmpty() && artistName.isNotEmpty() && artistName != "N/A") {
                                    artists.add(Tag(id = artistId, name = artistName))
                                    Log.d(TAG, "[7-Details]  - 添加作者: [ID: $artistId, Name: $artistName]")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[7-Details] 解析单个 '作者' 失败 at index $j: ${e.message}")
                            // 继续处理下一个作者，不要中断
                            continue
                        }
                    }
                    Log.i(TAG, "[7-Details] '作者' 解析完成，共 ${artists.size} 个。")
                    // --- 【修改结束】---
                }

                "作品言語", "作品语言", "Languages" -> { // "作品言語" (JP), "作品语言" (CN) [cite: 5]
                    // 对应 'languages' -> 'languagesId'
                    languages.addAll(extractTagLikeItems(groupChildren, ::getSingleElement))
                }

                "タグ", "标签", "Tags" -> { // "タグ" (JP), "标签" (CN) [cite: 3]
                    Log.d(TAG, "[7-Details] 正在解析 '标签' (Tags)...")
                    val tagsArray: JSONArray
                    val firstValue = groupChildren.optJSONArray(1)

                    // 检查是 "嵌套" 结构（所有标签都在一个数组里）
                    // 还是 "扁平" 结构（所有标签都是 groupChildren 的直接子元素）
                    if (groupChildren.length() == 2 && firstValue != null) {
                        Log.d(TAG, "[7-Details] 检测到 '标签' 为嵌套结构。")
                        tagsArray = firstValue
                    } else {
                        Log.d(TAG, "[7-Details] 检测到 '标签' 为扁平结构。")
                        tagsArray = JSONArray()
                        // 【关键】从索引 1 开始循环，获取所有后续条目
                        for (j in 1 until groupChildren.length()) {
                            groupChildren.optJSONArray(j)?.let { tagsArray.put(it) }
                        }
                    }

                    Log.i(TAG, "[7-Details] 找到 ${tagsArray.length()} 个潜在的标签条目。")

                    // 【关键】遍历所有找到的标签
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
                            Log.w(TAG, "[7-Details] 解析单个 '标签' 失败 at index $j: ${e.message}")
                            // 继续处理下一个标签，不要中断
                            continue
                        }
                    }
                    Log.i(TAG, "[7-Details] '标签' 解析完成，共 ${tags.size} 个。")
                }

                "サークル", "团队", "Groups" -> { // "サークル" (JP), "团队" (CN) [cite: 1]
                    // 对应 'groups' -> 'groupsId'
                    groups.addAll(extractTagLikeItems(groupChildren, ::getSingleElement))
                }

                "原作", "Parodies" -> { // "原作" (JP, CN)
                    // 对应 'parodies' -> 'parodiesId'
                    // parodies.addAll(extractTagLikeItems(groupChildren, ::getSingleElement)) // <-- 删除这一行

                    // --- 【修改】粘贴 "Characters" 或 "Tags" 的逻辑并修改变量名 ---
                    Log.d(TAG, "[7-Details] 正在解析 '原作' (Parodies)...")
                    val parodiesArray: JSONArray
                    val firstValue = groupChildren.optJSONArray(1)

                    // 检查是 "嵌套" 结构还是 "扁平" 结构
                    // (您提供的数据  显示为 "扁平" 结构，此逻辑将正确处理)
                    if (groupChildren.length() == 2 && firstValue != null) {
                        Log.d(TAG, "[7-Details] 检测到 '原作' 为嵌套结构。")
                        parodiesArray = firstValue
                    } else {
                        Log.d(TAG, "[7-Details] 检测到 '原作' 为扁平结构。")
                        parodiesArray = JSONArray()
                        // 从索引 1 开始循环，获取所有后续条目
                        for (j in 1 until groupChildren.length()) {
                            groupChildren.optJSONArray(j)?.let { parodiesArray.put(it) }
                        }
                    }

                    Log.i(TAG, "[7-Details] 找到 ${parodiesArray.length()} 个潜在的原作条目。")

                    // 遍历所有找到的原作
                    for (j in 0 until parodiesArray.length()) {
                        val valueArray = parodiesArray.optJSONArray(j) ?: continue
                        try {
                            val innerValueArray = getSingleElement(valueArray)
                            val elementType = innerValueArray.optString(1)

                            if (elementType == "\$L12") {
                                val elementContainer = innerValueArray.optJSONObject(3) ?: continue

                                // 解析 Parody ID
                                val href = elementContainer.optString("href")
                                val parodyId = href.split("/").lastOrNull() ?: ""

                                // 解析 Parody Name
                                val childrenArray = elementContainer.optJSONArray("children") ?: continue
                                val element = getSingleElement(childrenArray)
                                val parodyName = element.optJSONObject(3)
                                    ?.optString("children", "")
                                    ?.trim()
                                    ?.replace("\\r", "") ?: ""

                                if (parodyId.isNotEmpty() && parodyName.isNotEmpty() && parodyName != "N/A") {
                                    parodies.add(Tag(id = parodyId, name = parodyName))
                                    Log.d(TAG, "[7-Details]  - 添加原作: [ID: $parodyId, Name: $parodyName]")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[7-Details] 解析单个 '原作' 失败 at index $j: ${e.message}")
                            // 继续处理下一个原作，不要中断
                            continue
                        }
                    }
                    Log.i(TAG, "[7-Details] '原作' 解析完成，共 ${parodies.size} 个。")
                    // --- 【修改结束】---
                }

                "カテゴリー", "类别", "Categories" -> { // "カテゴリー" (JP), "类别" (CN) [cite: 6]
                    // 对应 'categories' -> 'categoriesId'
                    categories.addAll(extractTagLikeItems(groupChildren, ::getSingleElement))
                }

                "キャラクター", "角色", "Characters" -> { // "キャラクター" (JP), "角色" (CN) [cite: 2]
                    Log.d(TAG, "[7-Details] 正在解析 '角色' (Characters)...")
                    val charactersArray: JSONArray
                    val firstValue = groupChildren.optJSONArray(1)

                    // 检查是 "嵌套" 结构还是 "扁平" 结构
                    if (groupChildren.length() == 2 && firstValue != null) {
                        Log.d(TAG, "[7-Details] 检测到 '角色' 为嵌套结构。")
                        charactersArray = firstValue
                    } else {
                        Log.d(TAG, "[7-Details] 检测到 '角色' 为扁平结构。")
                        charactersArray = JSONArray()
                        // 从索引 1 开始循环，获取所有后续条目
                        for (j in 1 until groupChildren.length()) {
                            groupChildren.optJSONArray(j)?.let { charactersArray.put(it) }
                        }
                    }

                    Log.i(TAG, "[7-Details] 找到 ${charactersArray.length()} 个潜在的角色条目。")

                    // 遍历所有找到的角色
                    for (j in 0 until charactersArray.length()) {
                        val valueArray = charactersArray.optJSONArray(j) ?: continue
                        try {
                            val innerValueArray = getSingleElement(valueArray)
                            val elementType = innerValueArray.optString(1)

                            if (elementType == "\$L12") {
                                val elementContainer = innerValueArray.optJSONObject(3) ?: continue

                                // 解析 Character ID
                                val href = elementContainer.optString("href")
                                val characterId = href.split("/").lastOrNull() ?: ""

                                // 解析 Character Name
                                val childrenArray = elementContainer.optJSONArray("children") ?: continue
                                val element = getSingleElement(childrenArray)
                                val characterName = element.optJSONObject(3)
                                    ?.optString("children", "")
                                    ?.trim()
                                    ?.replace("\\r", "") ?: ""

                                if (characterId.isNotEmpty() && characterName.isNotEmpty() && characterName != "N/A") {
                                    characters.add(Tag(id = characterId, name = characterName))
                                    Log.d(TAG, "[7-Details]  - 添加角色: [ID: $characterId, Name: $characterName]")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[7-Details] 解析单个 '角色' 失败 at index $j: ${e.message}")
                            // 继续处理下一个角色，不要中断
                            continue
                        }
                    }
                    Log.i(TAG, "[7-Details] '角色' 解析完成，共 ${characters.size} 个。")
                }
            }
            // --- 【修改结束】---
        }


        // --- 【修改】更新日志以包含所有新字段 ---
        Log.i(TAG, "[7-Details] 解析详情页完成。")
        Log.i(TAG, "[7-Details] ID: $id, 标题: $title, 封面: $coverUrl")
        Log.i(TAG, "[7-Details] Languages (${languages.size} 个): ${languages.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Artists (${artists.size} 个): ${artists.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Groups (${groups.size} 个): ${groups.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Parodies (${parodies.size} 个): ${parodies.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Characters (${characters.size} 个): ${characters.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Categories (${categories.size} 个): ${categories.joinToString { it.name }}")
        Log.i(TAG, "[7-Details] Tags (${tags.size} 个): ${tags.joinToString { it.name }}")
        // --- 【修改结束】---

        // --- 【修改】更新 Comic 构造函数调用 ---
        // 假设 Comic.kt 数据类已更新，以匹配标准化的字段名 (artists, groups, etc.)
        // 并且这些字段的类型是 List<Tag>
        comics.add(
            Comic(
                id = id,
                title = title,
                coverUrl = coverUrl,
                languages = languages,
                tags = tags,
                // 以下字段基于您的标准化要求，假设 Comic.kt 已同步更新
                artists = artists,
                groups = groups,
                parodies = parodies,
                categories = categories,
                characters = characters
            )
        )
        // --- 【修改结束】---

    } catch (e: Exception) {
        Log.e(TAG, "[7-Details] 解析漫画详情页结构失败: ${e.message}", e)
    }
}

/**
 * 【新增】
 * 辅助函数，用于解析 "tag-like" 结构 (如 作者, 标签, 角色等)
 * 从 [h3_title, link1, link2, ...] 结构中提取 Link 列表
 *
 * @param groupChildren 包含 [h3_title, link1, link2, ...] 的 JSONArray
 * @param getSingleElement 对 `getSingleElement` 函数的引用
 * @return 解析出的 Tag 对象列表 (包含 id 和 name)
 */
private fun extractTagLikeItems(
    groupChildren: JSONArray,
    getSingleElement: (JSONArray) -> JSONArray
): List<Tag> {
    val items = mutableListOf<Tag>()

    // 示例数据显示所有都是扁平结构 [title, item1, item2, ...]
    // 我们从索引 1 开始迭代以跳过标题
    val itemsArray = JSONArray()
    for (j in 1 until groupChildren.length()) {
        groupChildren.optJSONArray(j)?.let { itemsArray.put(it) }
    }

    Log.d(TAG, "[7-Details-Helper] 找到 ${itemsArray.length()} 个条目。")

    for (j in 0 until itemsArray.length()) {
        val valueArray = itemsArray.optJSONArray(j) ?: continue
        try {
            val innerValueArray = getSingleElement(valueArray)
            val elementType = innerValueArray.optString(1)

            // "$L12" 是一种链接元素
            if (elementType == "\$L12") {
                val elementContainer = innerValueArray.optJSONObject(3) ?: continue

                // 解析 ID (e.g., "/artists/24889" -> "24889")
                val href = elementContainer.optString("href")
                val tagId = href.split("/").lastOrNull() ?: ""

                // 解析 Name (e.g., "春菊天うどん")
                val childrenArray = elementContainer.optJSONArray("children") ?: continue
                val element = getSingleElement(childrenArray)
                val tagName = element.optJSONObject(3)
                    ?.optString("children", "")
                    ?.trim()
                    ?.replace("\\r", "") ?: "" // 清理可能存在的 \r 字符

                if (tagId.isNotEmpty() && tagName.isNotEmpty() && tagName != "N/A") {
                    items.add(Tag(id = tagId, name = tagName))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[7-Details-Helper] 解析单个条目失败 at index $j: ${e.message}")
            continue // 继续处理下一个
        }
    }
    return items
}