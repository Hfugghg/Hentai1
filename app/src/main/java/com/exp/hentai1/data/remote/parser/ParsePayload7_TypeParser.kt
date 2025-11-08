package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.TagInfo
import org.json.JSONArray

// (您可以保留 TAG, Comic, NetworkUtils, NextFParser.TAG, getSingleElement 等其他代码)

/**
 * 解析标签列表页面 (e.g., /tags 页面, 对应 New 1.txt)
 *
 * @param containerArray 顶层 JSON 数组 (来自 New 1.txt)
 * @return 包含 TagInfo 对象的列表
 */
fun parseTagsList(containerArray: JSONArray): List<TagInfo> {
    val tagsList = mutableListOf<TagInfo>()
    val TAG = "ParsePayload7_TypeParser" // 为日志定义一个TAG

    try {
        Log.d(TAG, "[TagsList] 开始解析标签列表。传入的 containerArray: ${containerArray}...")

        var divMt10: JSONArray? = null
        for (i in 0 until containerArray.length()) {
            try {
                val element = containerArray.optJSONArray(i) ?: continue
                val props = element.optJSONObject(3)
                if (props?.optString("className") == "mt-10") {
                    divMt10 = element
                    break // 找到 'div.mt-10' [cite: 1]
                }
            } catch (e: Exception) {
                // 忽略非数组元素或格式不正确的元素
            }
        }

        if (divMt10 == null) {
            Log.e(TAG, "[TagsList] 步骤 1 失败: 未能在 containerArray 中找到 'div.mt-10' 元素。")
            return tagsList
        }

        Log.d(TAG, "[TagsList] 步骤 1: 获取 div.mt-10 -> ${divMt10}...")

        // 2. -> children[0] (div.container)
        // 'div.mt-10' 的子元素 [0] 是 'div.container'
        val children1 = divMt10.getJSONObject(3).getJSONArray("children")
        val divContainer = children1.optJSONArray(0)
        if (divContainer == null) {
            Log.e(TAG, "[TagsList] 步骤 2 失败: div.mt-10 的 children[0] 不是 JSONArray。")
            return tagsList
        }
        Log.d(TAG, "[TagsList] 步骤 2: 获取 div.container -> ${divContainer}...")

        // 3. -> children (div.bg-[#1f1f1f])
// 修复：根据日志，div.container 的 "children" 属性 *直接* 就是 div.bg 元素，
// 而不是一个包含 div.bg 的数组。
        val divBg = divContainer.getJSONObject(3).optJSONArray("children")
        if (divBg == null) {
            Log.e(TAG, "[TagsList] 步骤 3 失败: div.container 的 children 不是 JSONArray。") // 更新日志消息
            return tagsList
        }
        Log.d(TAG, "[TagsList] 步骤 3: 获取 div.bg-[#1f1f1f] -> ${divBg}...")

        // 4. -> children (div.tag-container)
// 修复：根据日志，div.bg 的 "children" 属性 *直接* 就是 div.tag-container 元素。
        val tagContainer = divBg.getJSONObject(3).optJSONArray("children")
        if (tagContainer == null) {
            Log.e(TAG, "[TagsList] 步骤 4 失败: div.bg 的 children 不是 JSONArray。") // 更新日志消息
            return tagsList
        }
        Log.d(TAG, "[TagsList] 步骤 4: 获取 div.tag-container -> ${tagContainer}...")

        // 5. -> children (这就是标签<a>的数组)
        val tagsArray = tagContainer.getJSONObject(3)
        .getJSONArray("children") // [ ["$", "a", "101", ...], ["$", "a", "104", ...], ... ] [cite: 1]
        Log.d(TAG, "[TagsList] 步骤 5: 获取 tagsArray (所有 <a> 标签) -> ${tagsArray}...")

        Log.i(TAG, "[TagsList] 找到 ${tagsArray.length()} 个标签条目。")

        for (i in 0 until tagsArray.length()) {
            try {
                val tagElement = tagsArray.getJSONArray(i) // e.g., ["$", "a", "bandages", {...}] [cite: 16]
                val tagObject = tagElement.getJSONObject(3)
                Log.d(TAG, "[TagsList] 正在处理第 $i 个标签: tagElement -> ${tagElement}...")

                // a. 解析 href (e.g., "/tags/155")
                val href = tagObject.getString("href") // e.g., "/tags/13045" [cite: 1]
                Log.d(TAG, "[TagsList] 标签 $i 的 href: $href")

                // b. 解析名称
                // b.1. 英文名 (来自 tagElement[2])
                val englishName = tagElement.optString(2) // e.g., "sole female"
                Log.d(TAG, "[TagsList] 标签 $i 的英文名称 (englishName): $englishName")

                // b.2. 真实名称 (来自 tagObject.title)
                val name = tagObject.optString("title") // e.g., "女性一人\r"
                Log.d(TAG, "[TagsList] 标签 $i 的原始名称 (title): $name")

                // c. 分割 href 来获取种类和ID
                val parts = href.split("/").filter { it.isNotEmpty() }

                if (parts.size == 2) {
                    val category = parts[0] // "tags"
                    val id = parts[1]       // "155"

                    // 清理名称中可能存在的 \r 或 \n 字符
                    val cleanName = name.replace("\\r", "").replace("\\n", " ").trim() // 修复：同时移除 \n [cite: 6]

                    // 添加 englishName 到 TagInfo 构造函数
                    tagsList.add(TagInfo(name = cleanName, englishName = englishName, category = category, id = id))
                    Log.d(TAG, "[TagsList] 解析成功: Name=$cleanName, EnglishName=$englishName, Category=$category, ID=$id")
                } else {
                    Log.w(TAG, "[TagsList] 无法解析的 href 格式: $href")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[TagsList] 解析单个标签条目失败 (索引 $i): ${e.message}", e)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "[TagsList] 解析标签列表结构失败: ${e.message}", e)
    }
    return tagsList
}