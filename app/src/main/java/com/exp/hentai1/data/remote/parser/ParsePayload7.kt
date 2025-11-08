package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import com.exp.hentai1.data.remote.parser.NextFParser.cleanPayloadString
import org.json.JSONArray

/**
 * 解析数据块 7:
 * 此函数现在可以处理多种结构,并将解析任务分派给相应的辅助函数。
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
                // 列表结构 (新着、排行 或 类型列表)
                var listTitle = ""
                val firstElementArray = topArray.optJSONArray(0)
                if (firstElementArray == null) {
                    Log.e(TAG, "[7] 列表结构检测到，但 topArray[0] 不是 JSONArray。")
                    // 回退到原有逻辑
                    Log.i(TAG, "[7] 回退到 [新着漫画列表] 逻辑。")
                    parseComicList(secondElement, comics)
                    return comics
                }

                // --- 【新增】检查是否为 "类型数据列表" (Tags, Artists, etc.) ---
                var isTypeList = false
                try {
                    // 结构: ["$", "div", null, {"className":"mx-auto mt-10 w-fit", ...}]
                    val tag = firstElementArray.optString(1)
                    val props = firstElementArray.optJSONObject(3)
                    val className = props?.optString("className", "") ?: ""

                    // 使用 className 作为主要判断依据
                    if (tag == "div" && className == "mx-auto mt-10 w-fit") {
                        // 进一步确认: 检查它是否包含两个 "a" 子元素
                        val children = props?.optJSONArray("children")
                        if (children != null && children.length() == 2) {
                            val child1 = children.optJSONArray(0)
                            val child2 = children.optJSONArray(1)
                            // 确保子元素是 <a> 标签
                            if (child1 != null && child1.optString(1) == "a" &&
                                child2 != null && child2.optString(1) == "a"
                            ) {
                                isTypeList = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[7] 检查 '类型数据列表' 头部时出错: ${e.message}")
                }

                if (isTypeList) {
                    Log.i(TAG, "[7] 检测到 [类型数据列表] 结构 (如 Tags, Artists)。正在调用 parseTagsList...")
                    val tags = parseTagsList(topArray)
                    // 由于此页面不包含漫画，我们只记录日志并返回空列表
                    Log.i(TAG, "[7] 类型列表解析完成，共找到 ${tags.size} 个类型。返回空漫画列表。")
                    return emptyList() // 返回空的漫画列表
                }

                // --- 如果不是 "类型数据列表", 则继续执行 "新着/排行" 的逻辑 ---

                try {
                    // 【修正】使用 getSingleElement 来正确解析标题

                    // 1. 获取 h1 元素 (["$", "h1", ...])
                    val h1Element = firstElementArray
                        .getJSONObject(3) // {"className":"container...", "children": [...]}
                        .getJSONArray("children") // [ ["$","h1",...], ["$","div",...], ... ]
                        .getJSONArray(0) // ["$","h1",null,{"className":"...","children":[...]}]

                    // 2. 获取 h1 的 children (span 元素)
                    //    数据源为: "children":["$","span",null,{"className":"name",...}]
                    //    必须使用 getSingleElement 来处理这种非嵌套结构
                    val spanElement = getSingleElement(
                        h1Element.getJSONObject(3) // {"className":"...","children":[...]}
                            .getJSONArray("children") // [ "$", "span", ... ]
                    ) // spanElement is now ["$","span",null,{...}]

                    // 3. 提取标题
                    listTitle = spanElement
                        .getJSONObject(3) // {"className":"name", "children": "..."}
                        .getString("children") // "人気エロ漫画・エロ同人誌"

                } catch (e: Exception) {
                    Log.w(TAG, "[7] 无法解析列表标题 (可能为新着列表): ${e.message}")
                }

                // 【修改】使用更通用的关键词来判断是否为排行榜
                // 将标题转为小写以进行不区分大小写的比较
                val normalizedTitle = listTitle.lowercase()
                val isRankingList = normalizedTitle.contains("人気") || // 日语
                        normalizedTitle.contains("受欢迎") || // 中文
                        normalizedTitle.contains("popular")   // 英语

                if (isRankingList) {
                    // 结构 3: 漫画排行列表 (Popular)
                    Log.i(TAG, "[7] 检测到 [漫画排行列表] 结构 (Title: $listTitle)。")
                    parseComicRankList(firstElementArray, comics)
                } else {
                    // 结构 1: 漫画列表 (原有逻辑 - "New")
                    Log.i(TAG, "[7] 检测到 [新着漫画列表] 结构 (Title: '$listTitle', 未匹配到排行关键词)。")
                    parseComicList(secondElement, comics)
                }
            }

            is String -> {
                // 【修改】结构 2 (详情页) 或 结构 4 (搜索/列表页)
                // 优先通过结构 (寻找 grid container) 来判断，而不是 H1 标题内容

                var isListPage = false
                var gridContainer: JSONArray? = null
                val rootObject = topArray.optJSONObject(3) // {"className":"space-y-10", ...}
                val childrenArray = rootObject?.optJSONArray("children")

                if (childrenArray != null) {

                    // --- 【新增】检查 "无结果" 页面 ---
                    // "无结果" 页面没有 grid, 只有一个 h1 作为第一个子元素
                    if (childrenArray.length() > 0) {
                        try {
                            val firstChild = childrenArray.optJSONArray(0)
                            // 检查第一个子元素是否为 H1
                            if (firstChild != null && "h1" == firstChild.optString(1)) {
                                val h1Children = firstChild.optJSONObject(3)?.optJSONArray("children")
                                // 检查 H1 的子元素(文本)
                                if (h1Children != null && h1Children.length() > 0) {
                                    val h1Text = h1Children.optString(0, "").lowercase()
                                    // 检查是否包含 "无结果" 关键词
                                    if (h1Text.contains("見つかりませんでした") || // JP
                                        h1Text.contains("没有找到搜索结果") ||     // CN
                                        h1Text.contains("no results found")) {   // EN

                                        Log.i(TAG, "[7] 检测到 [漫画搜索/列表页 - 无结果] 结构。")
                                        isListPage = true
                                        // gridContainer 保持为 null, 因为没有结果
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[7] 结构检测: 检查 '无结果' H1 时出错: ${e.message}")
                        }
                    }

                    // --- 尝试寻找 Grid Container ---
                    // 只有在尚未确定为 "无结果" 页面时才需要寻找 grid
                    if (!isListPage) {
                        // 尝试 1: 检查 children[1] (新结构, 如中文/英文列表页)
                        if (childrenArray.length() > 1) {
                            val potentialGrid = childrenArray.optJSONArray(1)
                            if (potentialGrid != null) {
                                val gridClassName = potentialGrid.optJSONObject(3)?.optString("className", "") ?: ""
                                if (gridClassName.contains("grid")) {
                                    gridContainer = potentialGrid
                                    isListPage = true
                                    Log.d(TAG, "[7] 结构检测: 在 children[1] 找到 grid container。")
                                }
                            }
                        }

                        // 尝试 2: 检查 children[0] 内部 (旧结构, 如日文搜索页)
                        if (!isListPage && childrenArray.length() > 0) {
                            try {
                                Log.d(TAG, "[7] 结构检测: children[1] 未命中, 尝试解析 children[0] 内部...")
                                val containerDiv = childrenArray.optJSONArray(0) // 第一个 div (container)
                                val containerChildren = containerDiv?.getJSONObject(3)?.getJSONArray("children")

                                if (containerChildren != null) {
                                    // 遍历 container 的 children 寻找 grid
                                    for (i in 0 until containerChildren.length()) {
                                        val child = containerChildren.optJSONArray(i)
                                        if (child != null) {
                                            val className = child.optJSONObject(3)?.optString("className", "") ?: ""
                                            if (className.contains("grid")) {
                                                gridContainer = child
                                                isListPage = true
                                                Log.d(TAG, "[7] 结构检测: 在 children[0] 内部索引 $i 处找到 grid。")
                                                break
                                            }
                                        }
                                    }

                                    // 回退: 尝试固定索引 2 (旧结构)
                                    if (!isListPage && containerChildren.length() > 2) {
                                        val fallbackGrid = containerChildren.optJSONArray(2)
                                        val fallbackClassName = fallbackGrid?.optJSONObject(3)?.optString("className", "") ?: ""
                                        if (fallbackGrid != null && fallbackClassName.contains("grid")) {
                                            gridContainer = fallbackGrid
                                            isListPage = true
                                            Log.d(TAG, "[7] 结构检测: 通过 children[0] 内部固定索引 2 找到 grid。")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "[7] 结构检测: 尝试解析 children[0] 内部 grid 失败: ${e.message}")
                            }
                        }
                    }
                }

                // --- 根据检测结果分派 ---

                // 【修改】如果 isListPage 为 true, 则认为是列表页 (可能有 grid, 也可能无结果)
                if (isListPage) {

                    if (gridContainer != null) {
                        // 结构 4: 漫画搜索/列表页 (有结果)
                        Log.i(TAG, "[7] 检测到 [漫画搜索/列表页] 结构 (基于 grid)。")

                        // (可选) 尝试解析 H1 标题用于日志记录
                        try {
                            val pageTitle = topArray.getJSONObject(3)
                                .getJSONArray("children")
                                .getJSONArray(0) // [HEADER_DIV]
                                .getJSONObject(3)
                                .getJSONArray("children")
                                .getJSONArray(0) // [H1]
                                .getJSONObject(3)
                                .getString("children")
                            Log.d(TAG, "[7-List] 页面标题: $pageTitle")
                        } catch (_: Exception) {
                            // 标题解析失败不影响列表解析
                        }

                        parseComicSearchList(gridContainer, comics)
                    } else {
                        // 结构 4: 漫画搜索/列表页 (无结果)
                        // H1 检查已将 isListPage 设为 true, 但 gridContainer 为 null
                        Log.i(TAG, "[7] 检测到 [漫画搜索/列表页], 但无 grid (无结果)。")
                        // 不需要解析, comics 列表将为空, 这是正确的
                    }

                } else {
                    // 结构 2: 漫画详情页
                    Log.i(TAG, "[7] 未找到 grid/无结果H1，检测为 [漫画详情页] 结构。")

                    if (rootObject != null) {
                        // 调用您已经写好的详情页解析器
                        parseComicDetails(rootObject, comics)
                    } else {
                        Log.e(TAG, "[7-Details] 无法提取详情页的 'detailsObject' (topArray[3])。")
                    }
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