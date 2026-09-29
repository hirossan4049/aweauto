package com.h1rose.aweauto.cast

import org.json.JSONArray
import org.json.JSONObject

/** bind チャンネルで届く 1 件のメッセージ。例: [12, ["setPlaylist", {...}]] */
data class LoungeMessage(val index: Int, val name: String, val args: JSONArray) {
    /** コマンドの引数オブジェクト (無ければ空) */
    val payload: JSONObject get() = args.optJSONObject(0) ?: JSONObject()
}

/**
 * bind の応答は「長さ\n[JSON 配列]」の繰り返しで、ストリームの途中で切れて届く。
 * 長さの単位 (バイト/文字) は当てにせず、括弧の対応で JSON 配列の終わりを見つける。
 */
class LoungeChunkParser {
    private val buf = StringBuilder()

    fun feed(text: String): List<LoungeMessage> {
        buf.append(text)
        val out = mutableListOf<LoungeMessage>()
        while (true) {
            val start = buf.indexOf("[")
            if (start < 0) break
            val end = findArrayEnd(buf, start)
            if (end < 0) break
            val chunk = buf.substring(start, end + 1)
            buf.delete(0, end + 1)
            out += parseChunk(chunk)
        }
        return out
    }

    private fun parseChunk(json: String): List<LoungeMessage> {
        val outer = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until outer.length()).mapNotNull { i ->
            val entry = outer.optJSONArray(i) ?: return@mapNotNull null
            val body = entry.optJSONArray(1) ?: return@mapNotNull null
            val name = body.optString(0).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val args = JSONArray()
            for (j in 1 until body.length()) args.put(body.get(j))
            LoungeMessage(entry.optInt(0), name, args)
        }
    }

    private fun findArrayEnd(s: CharSequence, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> if (--depth == 0) return i
            }
        }
        return -1
    }
}

/** 送信する 1 件 (onStateChange など) */
data class OutgoingMessage(val name: String, val params: Map<String, String> = emptyMap())

/** count=2&ofs=5&req0__sc=onStateChange&req0_state=1&... の形にする */
fun encodeOutgoing(messages: List<OutgoingMessage>, ofs: Int): Map<String, String> {
    val form = linkedMapOf("count" to messages.size.toString(), "ofs" to ofs.toString())
    messages.forEachIndexed { i, m ->
        form["req${i}__sc"] = m.name
        m.params.forEach { (k, v) -> form["req${i}_$k"] = v }
    }
    return form
}
