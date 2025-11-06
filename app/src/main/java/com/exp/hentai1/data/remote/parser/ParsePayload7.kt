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
                // 列表结构 (新着 或 排行)
                // 我们需要通过 topArray[0] 的标题来区分
                var listTitle = ""
                val firstElementArray = topArray.optJSONArray(0)
                if (firstElementArray == null) {
                    Log.e(TAG, "[7] 列表结构检测到，但 topArray[0] 不是 JSONArray。")
                    // 回退到原有逻辑
                    Log.i(TAG, "[7] 回退到 [新着漫画列表] 逻辑。")
                    parseComicList(secondElement, comics)
                    return comics
                }

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


                if (listTitle.contains("人気")) {
                    // 结构 3: 漫画排行列表 (Popular)
                    Log.i(TAG, "[7] 检测到 [漫画排行列表] 结构 (Title: $listTitle)。")
                    parseComicRankList(firstElementArray, comics)
                } else {
                    // 结构 1: 漫画列表 (原有逻辑 - "New")
                    Log.i(TAG, "[7] 检测到 [新着漫画列表] 结构 (JSONArray)。")
                    parseComicList(secondElement, comics)
                }
            }

            is String -> {
                // 【修改】结构 2 (详情页) 或 结构 4 (搜索页)
                // 两者的 secondElement 都可能是 "div"

                var pageTitle = ""
                var isSearchPage = false

                try {
                    // 尝试按 [搜索页] 结构解析H1标题
                    pageTitle = topArray.getJSONObject(3) // {"className":"space-y-10", "children": [...]}
                        .getJSONArray("children") // [ [HEADER_DIV], [GRID_DIV] ]
                        .getJSONArray(0) // [HEADER_DIV] = ["$", "div", {"className":"container..."}]
                        .getJSONObject(3) // {"className":"container...", "children": [...]}
                        .getJSONArray("children") // [ [H1], [BUTTON_DIV] ]
                        .getJSONArray(0) // [H1] = ["$", "h1", {"className":"..."}]
                        .getJSONObject(3) // {"className":"...", "children": "..."}
                        .getString("children") // "「...」のエロ漫画・エロ同人誌"

                    // 通过标题特征词判断是否为搜索页
                    if (pageTitle.contains("のエロ漫画・エロ同人誌")) {
                        isSearchPage = true
                    }
                } catch (e: Exception) {
                    // 解析标题失败，说明不是搜索页，假定为详情页
                    Log.d(TAG, "[7] 标题解析失败，假定为详情页 (非Search): ${e.message}")
                }

                if (isSearchPage) {
                    // 结构 4: 漫画搜索页
                    Log.i(TAG, "[7] 检测到 [漫画搜索列表] 结构 (Title: $pageTitle)。")

                    val childrenArray = topArray.optJSONObject(3)?.optJSONArray("children")
                    var gridContainer: JSONArray? = null

                    if (childrenArray != null) {
                        // 【修复】正确的 grid container 路径
                        // 从 New 1.txt 可以看到结构：childrenArray[0] -> container -> children[2] -> grid
                        try {
                            val containerDiv = childrenArray.getJSONArray(0) // 第一个 div (container)
                            val containerChildren = containerDiv.getJSONObject(3).getJSONArray("children")

                            // 遍历 container 的 children 寻找 grid
                            for (i in 0 until containerChildren.length()) {
                                val child = containerChildren.optJSONArray(i)
                                if (child != null) {
                                    val className = child.optJSONObject(3)?.optString("className", "") ?: ""
                                    if (className.contains("grid")) {
                                        gridContainer = child
                                        Log.d(TAG, "[7-Search] 在 container 索引 $i 处找到 grid container。")
                                        break
                                    }
                                }
                            }

                            // 如果上面没找到，尝试直接取索引 2（根据 New 1.txt 结构）
                            if (gridContainer == null && containerChildren.length() > 2) {
                                gridContainer = containerChildren.optJSONArray(2)
                                if (gridContainer != null) {
                                    Log.d(TAG, "[7-Search] 通过固定索引 2 找到 grid container。")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "[7-Search] 解析 container 结构失败: ${e.message}")
                        }
                    }

                    if (gridContainer != null) {
                        parseComicSearchList(gridContainer, comics)
                    } else {
                        Log.e(TAG, "[7-Search] grid container (className=grid) 未找到！")
                        // 【调试】打印结构信息
                        Log.d(TAG, "[7-Search] childrenArray 长度: ${childrenArray?.length()}")
                        if (childrenArray != null && childrenArray.length() > 0) {
                            val firstChild = childrenArray.optJSONArray(0)
                            if (firstChild != null) {
                                val firstChildClassName = firstChild.optJSONObject(3)?.optString("className", "")
                                Log.d(TAG, "[7-Search] 第一个 child 的 className: $firstChildClassName")
                            }
                        }
                    }
                }else {
                    // 结构 2: 漫画详情页
                    Log.i(TAG, "[7] 检测到 [漫画详情页] 结构。")

                    // parseComicDetails 期望传入 {"className":"space-y-10", "children": [...]}
                    val detailsObject = topArray.optJSONObject(3)

                    if (detailsObject != null) {
                        // 调用您已经写好的详情页解析器
                        parseComicDetails(detailsObject, comics)
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
