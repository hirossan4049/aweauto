package com.hirossan.aweauto.data

import android.content.Context
import android.content.SharedPreferences
import com.hirossan.aweauto.adblock.AdBlocker
import com.hirossan.aweauto.adblock.FilterList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val videoId: String, val title: String, val watchedAt: Long) {
    val thumbnailUrl get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val heroImageUrl get() = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
    val watchUrl get() = "https://m.youtube.com/watch?v=$videoId"
}

/** スマホ側の設定画面と車載画面で共有する設定。変更は StateFlow で即時に両方へ反映される。 */
object Prefs {
    private const val MAX_HISTORY = 30

    private lateinit var sp: SharedPreferences

    private val optimize = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _adblock = MutableStateFlow(true)
    val adblock: StateFlow<Boolean> = _adblock.asStateFlow()
    private val _filterLists = MutableStateFlow<Set<FilterList>>(emptySet())
    val filterLists: StateFlow<Set<FilterList>> = _filterLists.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("aweauto", Context.MODE_PRIVATE)
        optimize.value = StreamService.entries.associate { it.id to sp.getBoolean(optimizeKey(it), true) }
        _history.value = decodeHistory(sp.getString("history", null))
        _adblock.value = sp.getBoolean("adblock", true)
        _filterLists.value = FilterList.entries.filter { sp.getBoolean(filterKey(it), it.defaultOn) }.toSet()
    }

    fun setAdblock(enabled: Boolean) {
        sp.edit().putBoolean("adblock", enabled).apply()
        _adblock.value = enabled
    }

    fun setFilterList(list: FilterList, enabled: Boolean) {
        sp.edit().putBoolean(filterKey(list), enabled).apply()
        _filterLists.value = if (enabled) _filterLists.value + list else _filterLists.value - list
        AdBlocker.onListsChanged(_filterLists.value)
    }

    val optimizeFlags: StateFlow<Map<String, Boolean>> get() = optimize.asStateFlow()

    fun isOptimized(service: StreamService) = optimize.value[service.id] ?: true

    fun setOptimized(service: StreamService, enabled: Boolean) {
        sp.edit().putBoolean(optimizeKey(service), enabled).apply()
        optimize.value = optimize.value + (service.id to enabled)
    }

    fun recordWatch(videoId: String, title: String?) {
        val prev = _history.value.firstOrNull { it.videoId == videoId }
        val item = HistoryItem(
            videoId = videoId,
            // 遷移直後はタイトルが取れていないことがあるので、既知のタイトルを残す
            title = title?.takeIf { it.isNotBlank() } ?: prev?.title ?: "",
            watchedAt = System.currentTimeMillis(),
        )
        val next = (listOf(item) + _history.value.filter { it.videoId != videoId }).take(MAX_HISTORY)
        _history.value = next
        sp.edit().putString("history", encodeHistory(next)).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        sp.edit().remove("history").apply()
    }

    private fun optimizeKey(service: StreamService) = "optimize_${service.id}"
    private fun filterKey(list: FilterList) = "filter_${list.id}"

    private fun encodeHistory(items: List<HistoryItem>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.videoId).put("title", it.title).put("at", it.watchedAt))
        }
    }.toString()

    private fun decodeHistory(json: String?): List<HistoryItem> {
        if (json == null) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryItem(o.getString("id"), o.optString("title"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}
