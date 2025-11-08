package com.exp.hentai1.data.remote.parser

import android.util.Log
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

/**
 * 这是一个包含解析 Next.js 页面数据逻辑的工具集合。
 * 它主要用于从 HTML 中提取由 `self.__next_f.push` 推送的 JSON payload，
 * 并提供了针对特定数据块的通用工具方法。
 */
object NextFParser {

    internal const val TAG = "NextFParser"

    // 【新增】用于暂存跨 <script> 标签的被截断的 payload
    private var fragmentBuffer: String? = null

    /**
     * 从完整的HTML内容中提取Next.js的payload数据。
     *
     * 【修复】
     * 1. 恢复 "逐个<script>处理" 的逻辑，这是保证 Payload 6 等正常数据能被解析的
     * 前提。
     * 2. 引入 `fragmentBuffer` 状态变量。
     * 3. 当 `payloadBlock.lines().forEach` 循环时：
     * - (A) 如果遇到一个 "ID:JSON" 结构，会尝试解析它。
     * - (B) 如果解析失败 (JSONException)，说明这是一个被截断的片段
     * (如 Payload 7 的第一部分)，将其存入 `fragmentBuffer`。
     * - (C) 如果遇到一个没有ID的行 (如 Payload 7 的第二部分)，
     * 并且 `fragmentBuffer` 不为空，就将此行拼接到缓冲区，
     * 并重新尝试解析 *拼接后* 的完整 JSON。
     *
     * @param html 完整的HTML页面源字符串。
     * @return 一个Map，键是payload的ID（例如 "7"），值是原始payload字符串（例如 "7:[[...]]"）。
     */
    fun extractPayloadsFromHtml(html: String): Map<String, String> {
        Log.d(TAG, "--- HTML 检查 ---")
        Log.d(TAG, "HTML 字符串是否包含 Payload 6 标记? ${html.contains("6:[[")}")
        Log.d(TAG, "-------------------")
        // --- 调试日志结束 ---

        val payloads = mutableMapOf<String, String>()
        val doc = Jsoup.parse(html)
        val scripts = doc.select("script")

        val startMarker = "self.__next_f.push([1,\""
        val endMarker = "\"])"

        // 【重置】在每次 *全新* 的 HTML 解析开始时，清空缓冲区
        fragmentBuffer = null

        scripts.forEach { script ->
            val scriptContent = script.html()
            val startIndex = scriptContent.indexOf(startMarker)

            if (startIndex != -1) {
                val payloadStartIndex = startIndex + startMarker.length
                // 【修复】使用 lastIndexOf 仍然是正确的，因为它在 *单个* <script> 内部是安全的
                val endIndex = scriptContent.lastIndexOf(endMarker)

                if (endIndex > payloadStartIndex) {
                    // 提取 *这个 script* 的 payload 块
                    val rawPayload = scriptContent.substring(payloadStartIndex, endIndex)

                    // --- 【核心】在这里立即处理，而不是全部拼接 ---

                    // 转义
                    val payloadStep1 = rawPayload.replace("\\\\r", "[[PLACEHOLDER_R]]")
                    val payloadStep2 = payloadStep1.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n") // <-- 确保换行符被正确处理
                    val payloadBlock = payloadStep2.replace("[[PLACEHOLDER_R]]", "\\\\r")

                    // 逐行解析这个 *小块*
                    payloadBlock.lines().forEach { line ->
                        if (line.isBlank()) return@forEach // 跳过空行

                        // 【修改】优先检查缓冲区
                        if (fragmentBuffer != null) {
                            // --- 情况 C: 缓冲区有内容，此行 *必须* 是片段 (Payload 7 - part 2) ---
                            Log.d(TAG, "检测到 Payload 片段(上一行被截断)，尝试拼接...")

                            // 无论此行是否包含 ':'，都将其拼接到缓冲区
                            val combinedLine = fragmentBuffer + line
                            fragmentBuffer = null // 消耗并清空缓冲区

                            // --- 现在，像处理一个普通行一样处理 *拼接后* 的 line ---
                            val combinedParts = combinedLine.split(':', limit = 2)
                            if (combinedParts.size != 2) {
                                Log.e(TAG, "拼接失败，格式仍然无效。")
                                return@forEach
                            }

                            val id = combinedParts[0]
                            val content = combinedParts[1].trim()

                            try {
                                if (content.startsWith("[")) JSONArray(content) else JSONObject(content)

                                // 【成功】拼接成功!
                                payloads[id] = combinedLine
                                Log.d(TAG, "从缓冲区成功拼接并提取 Payload ID: $id")

                            } catch (e: org.json.JSONException) {
                                Log.e(TAG, "拼接 Payload $id 失败，JSON 仍然无效。", e)
                            }

                        } else {
                            // --- 缓冲区为空，正常处理此行 ---
                            val parts = line.split(':', limit = 2)

                            if (parts.size == 2) {
                                // --- 情况 A: 找到 "ID:Content" 结构 ---
                                val id = parts[0]
                                val content = parts[1].trim()

                                if (payloads.containsKey(id)) {
                                    Log.d(TAG, "已存在 Payload ID: $id。跳过...")
                                    return@forEach
                                }

                                if (content.startsWith("[") || content.startsWith("{")) {
                                    try {
                                        if (content.startsWith("[")) JSONArray(content) else JSONObject(content)

                                        // 【成功】没有异常，是完整 JSON (例如 Payload 6)
                                        payloads[id] = line
                                        Log.d(TAG, "从HTML中提取到有效的 Payload ID: $id")

                                    } catch (e: org.json.JSONException) {
                                        // --- 情况 B: 截断的 JSON (Payload 7 - part 1) ---
                                        Log.w(TAG, "检测到 Payload $id 片段 (JSON无效)，存入缓冲区...")
                                        fragmentBuffer = line
                                    }
                                } else {
                                    // 内容不是 JSON (例如 "1:HL..." 或 "19:I...")
                                    Log.d(TAG, "跳过无效的 Payload (ID: $id, Content: ${content.take(50)}...)")
                                }
                            } else {
                                // 缓冲区是空的，而这行也没有ID，是真的无效数据
                                Log.d(TAG, "跳过格式不正确的 payload 行: ${line.take(50)}...")
                            }
                        }
                    } // 结束 lines().forEach
                }
            }
        } // 结束 scripts.forEach

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