package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import org.json.JSONArray
import com.exp.hentai1.data.remote.parser.NextFParser.cleanPayloadString
import com.exp.hentai1.data.remote.parser.NextFParser.TAG // 引入 TAG

/**
 * 解析数据块 18: 中的漫画列表（日间排行榜）
 * @param payloadString 从 `extractPayloadsFromHtml` 获取的对应ID的payload字符串。
 * @return 包含解析出的漫画对象的 List<Comic>。
 */
fun parsePayload18(payloadString: String): List<Comic> {
    Log.i(TAG, "--- 开始解析数据块 18: (日间排行榜) ---")
    val comics = mutableListOf<Comic>()
    val jsonString = cleanPayloadString(payloadString)

    try {
        val outerArray = JSONArray(jsonString)

        // 1. 定位到 Grid Props
        // 路径: outerArray[3].children[0][3]
        val gridProps = outerArray.optJSONObject(3)
            ?.optJSONArray("children")?.optJSONArray(0)
            ?.optJSONObject(3)
            ?: run { Log.e(TAG, "无法定位到 Grid Props"); return emptyList() }

        // 2. 获取漫画列表 (Grid 的 children)
        val comicElements = gridProps.optJSONArray("children")
            ?: run { Log.e(TAG, "无法定位到漫画列表 (Grid children)"); return emptyList() }

        Log.d(TAG, "成功定位到漫画列表，包含 ${comicElements.length()} 个元素。")

        // 3. 遍历每一个漫画元素
        for (i in 0 until comicElements.length()) {
            val comicElement = comicElements.optJSONArray(i)
                ?: continue

            // 漫画元素的结构: [0:'$', 1:'$L17', 2:'ID', 3:{comicProps}]
            val id = comicElement.optString(2) // ID 在索引 2
            val comicProps = comicElement.optJSONObject(3) // 漫画元素的 props 在索引 3
                ?: continue

            Log.d(TAG, "正在解析漫画元素 $i，ID: $id")

            // innerChildren: [封面元素, 标题/作者元素]
            val innerChildren = comicProps.optJSONArray("children")
                ?: continue

            // --- 4. 提取封面 URL ---
            // 路径: innerChildren[0][3].children[3].src
            val coverDiv = innerChildren.optJSONArray(0) // 索引 0 是封面 div 元素
            val coverDivProps = coverDiv?.optJSONObject(3)
            // coverDivProps.children 结构是 ["$", "img", null, {props}]
            val coverUrl = coverDivProps?.optJSONArray("children")?.optJSONObject(3)?.optString("src") ?: ""

            // --- 5. 提取标题 ---
            // 路径: innerChildren[1][3].children[1][3].children[0][3].children
            val titleAuthorContainer = innerChildren.optJSONArray(1) // 索引 1 是标题/作者容器 div
            val titleAuthorContainerProps = titleAuthorContainer?.optJSONObject(3)
            val titleAuthorInfoDivs = titleAuthorContainerProps?.optJSONArray("children") // [语言标签 div, 标题/作者信息 container div]

            val titleAuthorInfoContainer = titleAuthorInfoDivs?.optJSONArray(1) // 索引 1 是 标题/作者信息 container div
            val titleAuthorInfoContainerProps = titleAuthorInfoContainer?.optJSONObject(3)
            val titleAuthorDivs = titleAuthorInfoContainerProps?.optJSONArray("children") // [标题 div, 作者 div]

            val titleElementProps = titleAuthorDivs?.optJSONArray(0)?.optJSONObject(3) // 索引 0 是标题 div 元素, 索引 3 是它的 props

            // 标题文本直接位于 titleProps 的 "children" 字段
            var title = titleElementProps?.optString("children") ?: ""

            // 备用：从 title 属性中获取 (它们通常相同)
            if (title.isBlank()) {
                title = titleElementProps?.optString("title") ?: ""
            }

            Log.d(TAG, "  -> 提取 CoverUrl: $coverUrl")
            Log.d(TAG, "  -> 提取 Title: $title")

            // --- 6. 创建 Comic 对象 (标准化输出) ---
            if (id.isNotBlank() && title.isNotBlank() && coverUrl.isNotBlank()) {
                comics.add(
                    Comic(
                        id = id,
                        title = title.trim(),
                        coverUrl = coverUrl, // 这里已经是完整的 URL
                        languages = emptyList(),
                    )
                )
            } else {
                Log.w(TAG, "跳过漫画 $id，提取信息不完整: Title=${title.isNotBlank()}, Cover=${coverUrl.isNotBlank()}")
            }
        }

        Log.i(TAG, "成功从 Payload 18 中提取到 ${comics.size} 个漫画条目。")
        return comics

    } catch (e: Exception) {
        Log.e(TAG, "解析 Payload 18 失败: ${e.message}", e)
        return emptyList()
    } finally {
        Log.i(TAG, "--- 数据块 18: 解析尝试结束 ---")
    }
}