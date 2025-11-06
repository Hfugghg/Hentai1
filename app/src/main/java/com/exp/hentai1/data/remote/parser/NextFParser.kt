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
     *
     * 优化逻辑 (根据用户反馈)：
     * 1. 使用 Jsoup 遍历所有 <script> 标签。
     * 2. 不再使用正则表达式，而是直接查找 "self.__next_f.push([1,\"" 标记。
     * 3. 手动截取开始标记和结束标记 ""])" 之间的原始 payload 字符串。
     * 4. 这种方法可以避免正则表达式处理超长字符串 (如 New 1.txt 所示) 时可能发生的故障。
     *
     * @param html 完整的HTML页面源字符串。
     * @return 一个Map，键是payload的ID（例如 "7"），值是原始payload字符串（例如 "7:[[...]]"）。
     */
    fun extractPayloadsFromHtml(html: String): Map<String, String> {
        val payloads = mutableMapOf<String, String>()
        val doc = Jsoup.parse(html)
        val scripts = doc.select("script")

        // 定义 payload 的开始和结束标记
        val startMarker = "self.__next_f.push([1,\""
        val endMarker = "\"])"

        scripts.forEach { script ->
            // 获取 <script> 标签的内部 HTML 内容
            val scriptContent = script.html()

            // 查找 payload 的开始位置
            val startIndex = scriptContent.indexOf(startMarker)
            if (startIndex != -1) {
                // 查找到开始标记，现在从开始标记之后查找结束标记
                val payloadStartIndex = startIndex + startMarker.length
                val endIndex = scriptContent.indexOf(endMarker, payloadStartIndex)

                if (endIndex != -1) {
                    // 成功找到开始和结束标记，提取它们之间的原始 payload
                    val rawPayload = scriptContent.substring(payloadStartIndex, endIndex)

                    // --- 保留你原有的转义处理逻辑 ---
                    val payloadStep1 = rawPayload.replace("\\\\r", "[[PLACEHOLDER_R]]")
                    val payloadStep2 = payloadStep1.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n") // <-- 确保换行符被正确处理
                    val payloadBlock = payloadStep2.replace("[[PLACEHOLDER_R]]", "\\\\r")

                    // --- 保留你原有的 payload 块处理逻辑 ---
                    // 这个 .lines() 逻辑可以同时处理 "多行payload块" 和 "单行巨大payload" (如 New 1.txt)
                    payloadBlock.lines().forEach { line ->
                        if (line.isBlank()) return@forEach // 跳过空行

                        val parts = line.split(':', limit = 2)

                        if (parts.size == 2) {
                            val id = parts[0]
                            val content = parts[1].trim() // 获取冒号后的内容

                            if (!payloads.containsKey(id)) {
                                if (content.startsWith("[") || content.startsWith("{")) {
                                    payloads[id] = line // <-- 存储单行的 payload (例如 "7:[...]")
                                    Log.d(TAG, "从HTML中提取到有效的 Payload ID: $id")
                                } else {
                                    Log.d(TAG, "跳过无效的 Payload (ID: $id, Content: ${content.take(50)}...)")
                                }
                            } else {
                                Log.w(TAG, "已存在 Payload ID: $id。跳过新的（可能是无效的）payload。")
                            }
                        } else {
                            Log.d(TAG, "跳过格式不正确的 payload 行: ${line.take(50)}...")
                        }
                    }
                    // --- 逻辑结束 ---

                } else {
                    Log.w(TAG, "找到 'self.__next_f.push' 脚本，但无法定位 payload 结束标记。")
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