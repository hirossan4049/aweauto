package com.hirossan.aweauto.adblock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val TAG = "AweAdBlock"

enum class FilterList(val id: String, val label: String, val description: String, val url: String, val defaultOn: Boolean) {
    ADGUARD_DNS(
        id = "adguard_dns",
        label = "AdGuard DNS フィルタ",
        description = "広告・トラッカーのドメインをまとめてブロック",
        url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        defaultOn = true,
    ),
    EASYLIST(
        id = "easylist",
        label = "EasyList",
        description = "定番の広告フィルタ (ドメイン単位のルールだけ使う)",
        url = "https://easylist.to/easylist/easylist.txt",
        defaultOn = false,
    ),
    ADGUARD_JAPANESE(
        id = "adguard_japanese",
        label = "AdGuard 日本語フィルタ",
        description = "日本のサイト向けの広告フィルタ (ドメイン単位のルールだけ使う)",
        url = "https://filters.adtidy.org/extension/ublock/filters/7.txt",
        defaultOn = false,
    ),
}

data class AdBlockStatus(
    val domainCount: Int = 0,
    val lastUpdated: Long = 0,
    val updating: Boolean = false,
    val error: String? = null,
)

/**
 * AdGuard / Adblock Plus 形式のフィルタリストからドメイン単位のブロックルールだけを取り出して、
 * WebView のリクエストを止める。パス指定や要素隠しのルールは扱わない
 * (ページ内の広告枠はサイトごとの CSS と YouTube 用スクリプトで消している)。
 */
object AdBlocker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private lateinit var dir: File
    private lateinit var builtin: Set<String>

    @Volatile
    private var blocked: Set<String> = emptySet()

    @Volatile
    private var allowed: Set<String> = emptySet()

    private val _status = MutableStateFlow(AdBlockStatus())
    val status: StateFlow<AdBlockStatus> = _status.asStateFlow()

    fun init(context: Context, enabledLists: () -> Set<FilterList>) {
        dir = File(context.filesDir, "filters").apply { mkdirs() }
        builtin = context.assets.open("adblock/builtin.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
        scope.launch {
            rebuild(enabledLists())
            val stale = System.currentTimeMillis() - _status.value.lastUpdated > TimeUnit.DAYS.toMillis(1)
            if (stale) update(enabledLists())
        }
    }

    /** host かその親ドメインがブロック対象なら true。WebView のネットワークスレッドから呼ばれる */
    fun isBlocked(host: String): Boolean {
        var h = host.lowercase()
        while (true) {
            if (h in allowed) return false
            if (h in blocked) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** 有効なリストが変わったときに呼ぶ。未取得のリストがあればダウンロードする */
    fun onListsChanged(lists: Set<FilterList>) {
        scope.launch {
            if (lists.any { !fileFor(it).exists() }) update(lists) else rebuild(lists)
        }
    }

    fun update(lists: Set<FilterList>) {
        scope.launch {
            _status.value = _status.value.copy(updating = true, error = null)
            var error: String? = null
            for (list in lists) {
                runCatching { download(list) }.onFailure {
                    Log.w(TAG, "download failed: ${list.id}", it)
                    error = "${list.label} の取得に失敗しました"
                }
            }
            rebuild(lists)
            _status.value = _status.value.copy(updating = false, error = error)
        }
    }

    private suspend fun rebuild(lists: Set<FilterList>) = mutex.withLock {
        val block = HashSet(builtin)
        val allow = HashSet<String>()
        var newest = 0L
        for (list in lists) {
            val file = fileFor(list)
            if (!file.exists()) continue
            newest = maxOf(newest, file.lastModified())
            file.bufferedReader().useLines { lines -> lines.forEach { parseLine(it, block, allow) } }
        }
        blocked = block
        allowed = allow
        _status.value = _status.value.copy(domainCount = block.size, lastUpdated = newest)
        Log.i(TAG, "rules: block=${block.size} allow=${allow.size}")
    }

    private fun download(list: FilterList) {
        val conn = URL(list.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val tmp = File(dir, "${list.id}.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(fileFor(list))
        } finally {
            conn.disconnect()
        }
    }

    private fun fileFor(list: FilterList) = File(dir, "${list.id}.txt")

    // ドメイン全体に効くルールだけを拾う。$third-party などリソース種別を問わないオプションは許容する
    private val allowedOptions = setOf("third-party", "3p", "important", "all", "popup", "document", "doc")
    private val domainPattern = Regex("""^[a-z0-9.-]+$""")

    internal fun parseLine(raw: String, block: MutableSet<String>, allow: MutableSet<String>) {
        val line = raw.trim()
        if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') return

        // hosts 形式: "0.0.0.0 example.com"
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
            val domain = line.substringAfter(' ').trim().substringBefore(' ').lowercase()
            if (domain != "localhost" && domainPattern.matches(domain)) block += domain
            return
        }

        val exception = line.startsWith("@@")
        val rule = if (exception) line.substring(2) else line
        if (!rule.startsWith("||")) return
        val caret = rule.indexOf('^')
        if (caret < 0) return
        val domain = rule.substring(2, caret).lowercase()
        if (!domainPattern.matches(domain) || '.' !in domain) return
        val rest = rule.substring(caret + 1)
        if (rest.isNotEmpty()) {
            if (rest[0] != '$') return
            val options = rest.substring(1).split(',')
            if (options.any { it !in allowedOptions }) return
        }
        if (exception) allow += domain else block += domain
    }
}
