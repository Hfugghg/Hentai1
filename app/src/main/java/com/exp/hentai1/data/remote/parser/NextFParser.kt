package com.exp.hentai1.data.remote.parser

import android.util.Log
import org.jsoup.Jsoup

/**
 * 这是一个包含解析 Next.js 页面数据逻辑的工具集合。
 * 它主要用于从 HTML 中提取由 `self.__next_f.push` 推送的 JSON payload，
 * 并提供了针对特定数据块的通用工具方法。
 */
object NextFParser {

    internal const val TAG = "NextFParser"

    /**
     * 从完整的HTML内容中提取Next.js的payload数据。
     * 它会查找类似 `self.__next_f.push([1,"7:[[...]]"])` 的脚本行，
     * 并将提取到的ID和payload存入Map中。
     *
     * @param html 完整的HTML页面源字符串。
     * @return 一个Map，键是payload的ID（例如 "7"），值是原始payload字符串（例如 "7:[[...]]"）。
     */
    fun extractPayloadsFromHtml(html: String): Map<String, String> {
        val payloads = mutableMapOf<String, String>()
        val doc = Jsoup.parse(html)
        val scripts = doc.select("script")
        // 正则表达式没有问题
        val pattern = """self\.__next_f\.push\(\[1,\s*"((?:.|\n)*?)"\s*\]\)""".toRegex()

        scripts.forEach { script ->
            val scriptContent = script.html()
            pattern.findAll(scriptContent).forEach { matchResult ->
                if (matchResult.groupValues.size == 2) {
                    val rawPayload = matchResult.groupValues[1]
                    // 你的转义处理逻辑是正确的
                    val payloadStep1 = rawPayload.replace("\\\\r", "[[PLACEHOLDER_R]]")
                    // --- BUG 修复 1: 增加对 \n 的反转义 ---
                    val payloadStep2 = payloadStep1.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n") // <-- 确保换行符被正确处理
                    val payloadBlock = payloadStep2.replace("[[PLACEHOLDER_R]]", "\\\\r")

                    // --- BUG 修复 2: 遍历 payload 块中的每一行 ---
                    payloadBlock.lines().forEach { line ->
                        if (line.isBlank()) return@forEach // 跳过空行

                        val parts = line.split(':', limit = 2)

                        if (parts.size == 2) {
                            val id = parts[0]
                            val content = parts[1].trim() // 获取冒号后的内容

                            // 1. 检查这个 key (id) 是否已经存在
                            if (!payloads.containsKey(id)) {
                                // 2. 检查它是否是一个有效的 JSON payload (它们都以 [ 开头)
                                //    (或者像 'a:{...}' 这样的 JSON 对象)
                                if (content.startsWith("[") || content.startsWith("{")) {
                                    payloads[id] = line // <-- 存储单行的 payload (例如 "6:[[...]]")
                                    Log.d(TAG, "从HTML中提取到有效的 Payload ID: $id")
                                } else {
                                    Log.d(TAG, "跳过无效的 Payload (ID: $id, Content: ${content.take(50)}...)")
                                }
                            } else {
                                Log.w(TAG, "已存在 Payload ID: $id。跳过新的（可能是无效的）payload。")
                            }
                            // --- 修复结束 ---
                        }
                    }
                }
            }
        }
        Log.i(TAG, "从HTML中总共提取到 ${payloads.size} 个有效 payloads。")
        return payloads
    }

    /**
     * 清理原始 payload 字符串，仅保留 JSON 数组部分。
     * @param rawPayload 原始的 Next.js payload 字符串，例如 "7:[[...]]"
     * @return 清理后的 JSON 字符串 "[...]"
     */
    internal fun cleanPayloadString(rawPayload: String): String {
        return rawPayload.substringAfter(':').trim()
    }
}