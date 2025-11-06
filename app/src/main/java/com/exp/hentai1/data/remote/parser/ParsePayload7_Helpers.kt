package com.exp.hentai1.data.remote.parser

import android.util.Log
import com.exp.hentai1.data.remote.parser.NextFParser.TAG
import org.json.JSONArray
import org.json.JSONObject

/**
 * 【新】辅助函数，用于从 "值" 元素中提取文本。
 * 它可以处理 [["$", "$L12", ...]] (链接) 和 ["$", "p", ...] (文本) 两种情况。
 */
internal fun extractValue(valueArray: JSONArray?): String {
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
internal fun getSingleElement(array: JSONArray): JSONArray {
    if (array.length() == 0) return JSONArray() // 处理空数组
    val firstElement = array.opt(0)

    return if (firstElement is JSONArray) {
        firstElement
    } else {
        array
    }
}
