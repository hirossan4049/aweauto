package com.h1rose.aweauto.data

import android.content.Context
import android.content.SharedPreferences
import com.h1rose.aweauto.adblock.AdBlocker
import com.h1rose.aweauto.adblock.FilterList
import com.h1rose.aweauto.cast.CastBridge
import com.h1rose.aweauto.cast.DialServer
import com.h1rose.aweauto.shizuku.ShizukuState
import com.h1rose.aweauto.cast.LoungeReceiver
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
    /** 画質の上限 (縦の画素数)。0 なら自動 */
    private val _maxHeight = MutableStateFlow(480)
    val maxHeight: StateFlow<Int> = _maxHeight.asStateFlow()
    /** 車の画面を「左: 地図 / 右: いつもの画面」に分ける */
    private val _splitMap = MutableStateFlow(false)
    val splitMap: StateFlow<Boolean> = _splitMap.asStateFlow()
    private val _mapApp = MutableStateFlow("google")
    val mapApp: StateFlow<String> = _mapApp.asStateFlow()
    /** 開発用: 動画を常にミュートする (debug ビルドだけ有効) */
    private val _devMute = MutableStateFlow(false)
    val devMute: StateFlow<Boolean> = _devMute.asStateFlow()
    private val _shizukuAutoRestart = MutableStateFlow(true)
    val shizukuAutoRestart: StateFlow<Boolean> = _shizukuAutoRestart.asStateFlow()
    private val _cast = MutableStateFlow(true)
    val cast: StateFlow<Boolean> = _cast.asStateFlow()
    private val _tverOffline = MutableStateFlow(true)
    val tverOffline: StateFlow<Boolean> = _tverOffline.asStateFlow()
    private val _prefetch = MutableStateFlow(false)
    val prefetch: StateFlow<Boolean> = _prefetch.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("aweauto", Context.MODE_PRIVATE)
        optimize.value = StreamService.entries.associate { it.id to sp.getBoolean(optimizeKey(it), true) }
        _history.value = decodeHistory(sp.getString("history", null))
        _adblock.value = sp.getBoolean("adblock", true)
        _maxHeight.value = sp.getInt("max_height", 480)
        _prefetch.value = sp.getBoolean("prefetch", false)
        _tverOffline.value = sp.getBoolean("tver_offline", true)
        _cast.value = sp.getBoolean("cast", true)
        _shizukuAutoRestart.value = sp.getBoolean("shizuku_auto_restart", true)
        _devMute.value = com.h1rose.aweauto.BuildConfig.DEBUG && sp.getBoolean("dev_mute", false)
        ShizukuState.autoRestart = _shizukuAutoRestart.value
        _splitMap.value = sp.getBoolean("split_map", false)
        _mapApp.value = sp.getString("map_app", "google") ?: "google"
        _filterLists.value = FilterList.entries.filter { sp.getBoolean(filterKey(it), it.defaultOn) }.toSet()
    }

    fun setMaxHeight(height: Int) {
        sp.edit().putInt("max_height", height).apply()
        _maxHeight.value = height
    }

    fun setSplitMap(enabled: Boolean) {
        sp.edit().putBoolean("split_map", enabled).apply()
        _splitMap.value = enabled
    }

    fun toggleSplitMap() = setSplitMap(!_splitMap.value)

    fun setMapApp(id: String) {
        sp.edit().putString("map_app", id).apply()
        _mapApp.value = id
    }

    fun setDevMute(enabled: Boolean) {
        sp.edit().putBoolean("dev_mute", enabled).apply()
        _devMute.value = enabled
    }

    fun setShizukuAutoRestart(enabled: Boolean) {
        sp.edit().putBoolean("shizuku_auto_restart", enabled).apply()
        _shizukuAutoRestart.value = enabled
        ShizukuState.autoRestart = enabled
    }

    fun setCast(context: Context, enabled: Boolean) {
        sp.edit().putBoolean("cast", enabled).apply()
        _cast.value = enabled
        if (enabled) {
            LoungeReceiver.start(CastBridge)
            DialServer.start(context)
        } else {
            LoungeReceiver.stop()
            DialServer.stop()
        }
    }

    fun setTverOffline(enabled: Boolean) {
        sp.edit().putBoolean("tver_offline", enabled).apply()
        _tverOffline.value = enabled
    }

    fun setPrefetch(enabled: Boolean) {
        sp.edit().putBoolean("prefetch", enabled).apply()
        _prefetch.value = enabled
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
            // 遷移直後はタイトルが「YouTube」だけのことがあるので、既知のタイトルを残す
            title = title?.takeIf { it.isNotBlank() && it != "YouTube" } ?: prev?.title ?: "",
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
