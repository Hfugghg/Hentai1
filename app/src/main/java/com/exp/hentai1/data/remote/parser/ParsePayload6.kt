package com.exp.hentai1.data.remote.parser

import android.util.Log
import org.json.JSONArray
import com.exp.hentai1.data.remote.parser.NextFParser.cleanPayloadString
import com.exp.hentai1.data.remote.parser.NextFParser.TAG

/**
 * 解析数据块 6: 漫画原图链接列表
 * @param payloadString 从 `extractPayloadsFromHtml` 获取的对应ID的payload字符串。
 * @return 包含解析出的图片URL的 List<String>。
 */
fun parsePayload6(payloadString: String): List<String> {
    Log.i(TAG, "--- 开始解析数据块 6: (漫画原图链接列表) ---")
    val imageUrls = mutableListOf<String>()
    // cleanPayloadString 假设会移除 "6:" 这样的前缀，并返回一个有效的 JSON 数组字符串
    val jsonString = cleanPayloadString(payloadString)

    if (jsonString.isEmpty()) {
        Log.e(TAG, "[6] 清理后的 JSON 字符串为空。")
        return emptyList()
    }

    try {
        // 根据 parsePayload6 的结构 ，数据深深地嵌套在结构中。
        // 路径大致为: root[1][3].children[3].children[3].slides

        // 1. 根数组
        val rootArray = JSONArray(jsonString)

        // 2. "body" 数组: rootArray[1]
        // ["$", "body", null, {...}]
        val bodyArray = rootArray.getJSONArray(1)

        // 3. "body" 对象: bodyArray[3]
        // { "className": "dark", "children": [...] }
        val bodyObject = bodyArray.getJSONObject(3)

        // 4. "children" 数组 (level 1): bodyObject.children
        // ["$", "$13", null, {...}]
        val children1 = bodyObject.getJSONArray("children")

        // 5. children1 的对象: children1[3]
        // { "children": [...] }
        val children1Object = children1.getJSONObject(3)

        // 6. "children" 数组 (level 2): children1Object.children
        // ["$", "$L16", null, {...}]
        val children2 = children1Object.getJSONArray("children")

        // 7. 包含 "slides" 的数据对象: children2[3]
        // { "articleId": "2298726", "slides": [...] }
        val dataObject = children2.getJSONObject(3)

        // 8. 获取 "slides" 数组
        val slidesArray = dataObject.getJSONArray("slides")

        // 9. 遍历 "slides" 数组，提取 "src"
        // 这里的 "slidesArray.length()" 是可变的，满足您的需求
        for (i in 0 until slidesArray.length()) {
            val slideObject = slidesArray.getJSONObject(i)
            if (slideObject.has("src")) {
                val imageUrl = slideObject.getString("src")
                imageUrls.add(imageUrl)
            }
        }

        Log.d(TAG, "[6] 成功解析到 ${imageUrls.size} 个图片链接。")

    } catch (e: Exception) {
        Log.e(TAG, "解析 Payload 6 失败: ${e.message}", e)
        // 在失败时打印出清理后的 JSON 字符串，方便调试
        Log.d(TAG, "[6] 失败时待解析的 JSON: $jsonString")
        return emptyList()
    } finally {
        Log.i(TAG, "--- 数据块 6: 解析尝试结束 (共找到 ${imageUrls.size} 个链接) ---")
    }

    return imageUrls
}