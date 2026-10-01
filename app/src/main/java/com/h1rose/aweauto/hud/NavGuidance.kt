package com.h1rose.aweauto.hud

import androidx.car.app.navigation.model.Maneuver
import java.time.LocalTime

/** 地図アプリのナビ通知から読み取った、次の案内と目的地までの見込み */
data class NavGuidance(
    /** 案内文 (例: 「〇〇交差点を右折」) */
    val cue: String,
    /** 次の案内地点までの距離 (m)。読み取れなければ null */
    val stepMeters: Double?,
    /** [Maneuver] の TYPE_* */
    val maneuverType: Int,
    /** ロータリーの何番目の出口か */
    val roundaboutExit: Int?,
    /** 目的地までの残り時間 (秒) */
    val remainingSeconds: Long?,
    /** 目的地までの残り距離 (m) */
    val remainingMeters: Double?,
    /** 到着予定時刻 */
    val arrival: LocalTime?,
)

/**
 * ナビ通知の文字列を読む。Google マップの日本語表示を基本に、英語表示とよく似た書き方のアプリにも対応する。
 *
 * Google マップのナビ通知は
 * - タイトル: 次の案内地点までの距離 (「300 m」「1.2 km」)
 * - 本文: 案内文 (「北に進む」「〇〇を右折」)
 * - サブテキスト: 「36 分 · 13 km · 17:54到着予定」
 */
object NavGuidanceParser {
    fun parse(title: String?, text: String?, subText: String?): NavGuidance? =
        parse(listOfNotNull(title, text, subText))

    /**
     * 通知に出ている文字列を表示順に渡す。アプリごとに距離・案内文・到着予定の置き場所が違うので、中身で見分ける。
     * - 到着予定: 時刻 (17:54) や「到着」「残り」「ETA」を含むもの
     * - 距離: 「300 m」だけのもの。無ければ案内文の中の「300m先」
     * - 案内文: それ以外
     */
    fun parse(texts: List<String>): NavGuidance? {
        val lines = texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val summaryLine = lines.firstOrNull { isSummary(it) }
        val rest = lines - setOfNotNull(summaryLine)
        val distanceLine = rest.firstOrNull { isDistanceOnly(it) }
        val cue = (rest - setOfNotNull(distanceLine)).joinToString(" ")
        val distance = distanceLine?.let { parseDistance(it) } ?: parseDistance(cue)
        if (cue.isEmpty() && distance == null) return null
        val summary = summaryLine?.let { parseSummary(it) } ?: Summary(null, null, null)
        return NavGuidance(
            cue = cue,
            stepMeters = distance,
            maneuverType = maneuverType(cue),
            roundaboutExit = roundaboutExit(cue),
            remainingSeconds = summary.seconds,
            remainingMeters = summary.meters,
            arrival = summary.arrival,
        )
    }

    private val DISTANCE = Regex("""([0-9][0-9,]*(?:\.[0-9]+)?)\s*(km|ｋｍ|キロ|m|ｍ|メートル|mi|マイル|ft|フィート)""", RegexOption.IGNORE_CASE)

    /** 「300 m」「1.2 km」「1,500 m」を m にする */
    fun parseDistance(s: String): Double? {
        val m = DISTANCE.find(s) ?: return null
        val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "km", "ｋｍ", "キロ" -> value * 1000
            "mi", "マイル" -> value * 1609.344
            "ft", "フィート" -> value * 0.3048
            else -> value
        }
    }

    private fun isSummary(s: String) =
        CLOCK.containsMatchIn(s) || listOf("到着", "残り", "ETA", "arrival").any { s.contains(it, ignoreCase = true) }

    private fun isDistanceOnly(s: String): Boolean {
        val m = DISTANCE.find(s) ?: return false
        return s.removeRange(m.range).trim().trim('先').isEmpty()
    }

    data class Summary(val seconds: Long?, val meters: Double?, val arrival: LocalTime?)

    private val HOURS = Regex("""(\d+)\s*(?:時間|hr|h)""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s*(?:分|min)""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM|午前|午後)?""", RegexOption.IGNORE_CASE)

    /** 「1 時間 5 分 · 85 km · 19:00到着予定」を残り時間・残り距離・到着時刻にする */
    fun parseSummary(s: String): Summary {
        val parts = s.split('·', '•', '・', '|')
        var seconds: Long? = null
        var meters: Double? = null
        var arrival: LocalTime? = null
        for (part in parts) {
            val clock = CLOCK.find(part)
            when {
                clock != null -> arrival = toTime(clock)
                HOURS.containsMatchIn(part) || MINUTES.containsMatchIn(part) -> {
                    val h = HOURS.find(part)?.groupValues?.get(1)?.toLong() ?: 0
                    val m = MINUTES.find(part)?.groupValues?.get(1)?.toLong() ?: 0
                    seconds = (h * 60 + m) * 60
                }
                parseDistance(part) != null -> meters = parseDistance(part)
            }
        }
        return Summary(seconds, meters, arrival)
    }

    private fun toTime(m: MatchResult): LocalTime? {
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        when (m.groupValues[3].lowercase()) {
            "pm", "午後" -> if (hour < 12) hour += 12
            "am", "午前" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private val ROUNDABOUT_EXIT = Regex("""(\d+)\s*(?:番目|つ目|st|nd|rd|th)""", RegexOption.IGNORE_CASE)

    fun roundaboutExit(cue: String): Int? =
        if (isRoundabout(cue)) ROUNDABOUT_EXIT.find(cue)?.groupValues?.get(1)?.toIntOrNull() else null

    private fun isRoundabout(cue: String) =
        cue.containsAny("ロータリー", "環状交差点", "ラウンドアバウト", "roundabout", "rotary")

    /**
     * 案内文から曲がり方を決める。車側は矢印の画像ではなくこの種類で矢印を出すことがあるので、なるべく当てる。
     * 日本は左側通行なので、Uターンは右回り、ロータリーは時計回り。
     */
    fun maneuverType(cue: String): Int {
        val right = cue.containsAny("右", "right")
        val left = cue.containsAny("左", "left")
        fun side(r: Int, l: Int, none: Int) = when {
            right && !left -> r
            left && !right -> l
            // 「左車線を使って右折」のように両方含むときは、最後に出てきた方を曲がる向きとみなす
            right && left -> if (lastIndexOfAny(cue, "右", "right") > lastIndexOfAny(cue, "左", "left")) r else l
            else -> none
        }
        return when {
            cue.containsAny("目的地", "到着", "destination", "arrive") ->
                side(Maneuver.TYPE_DESTINATION_RIGHT, Maneuver.TYPE_DESTINATION_LEFT, Maneuver.TYPE_DESTINATION)
            isRoundabout(cue) ->
                if (roundaboutExit(cue) != null) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
                else Maneuver.TYPE_ROUNDABOUT_ENTER_CW
            cue.containsAny("Uターン", "U ターン", "u-turn", "make a u") ->
                side(Maneuver.TYPE_U_TURN_RIGHT, Maneuver.TYPE_U_TURN_LEFT, Maneuver.TYPE_U_TURN_RIGHT)
            cue.containsAny("フェリー", "ferry") -> Maneuver.TYPE_FERRY_BOAT
            cue.containsAny("出口", "exit", "降り") ->
                side(Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT, Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT, Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT)
            cue.containsAny("入口", "ランプ", "ramp", "インターチェンジ") ->
                side(Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT, Maneuver.TYPE_ON_RAMP_NORMAL_LEFT, Maneuver.TYPE_ON_RAMP_NORMAL_LEFT)
            cue.containsAny("合流", "merge") ->
                side(Maneuver.TYPE_MERGE_RIGHT, Maneuver.TYPE_MERGE_LEFT, Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED)
            cue.containsAny("分岐", "fork") ->
                side(Maneuver.TYPE_FORK_RIGHT, Maneuver.TYPE_FORK_LEFT, Maneuver.TYPE_STRAIGHT)
            cue.containsAny("斜め", "やや", "slight") ->
                side(Maneuver.TYPE_TURN_SLIGHT_RIGHT, Maneuver.TYPE_TURN_SLIGHT_LEFT, Maneuver.TYPE_STRAIGHT)
            cue.containsAny("大きく", "鋭角", "sharp") ->
                side(Maneuver.TYPE_TURN_SHARP_RIGHT, Maneuver.TYPE_TURN_SHARP_LEFT, Maneuver.TYPE_STRAIGHT)
            cue.containsAny("右折", "左折", "turn", "方向", "曲が") ->
                side(Maneuver.TYPE_TURN_NORMAL_RIGHT, Maneuver.TYPE_TURN_NORMAL_LEFT, Maneuver.TYPE_STRAIGHT)
            cue.containsAny("維持", "車線", "keep") ->
                side(Maneuver.TYPE_KEEP_RIGHT, Maneuver.TYPE_KEEP_LEFT, Maneuver.TYPE_STRAIGHT)
            DEPART.containsMatchIn(cue) -> Maneuver.TYPE_DEPART
            cue.containsAny("直進", "道なり", "まっすぐ", "continue", "straight") -> Maneuver.TYPE_STRAIGHT
            right || left -> side(Maneuver.TYPE_TURN_NORMAL_RIGHT, Maneuver.TYPE_TURN_NORMAL_LEFT, Maneuver.TYPE_STRAIGHT)
            else -> Maneuver.TYPE_STRAIGHT
        }
    }

    /** 出発直後の「北に進む」「Head north」 */
    private val DEPART = Regex("""^(北|南|東|西|北東|北西|南東|南西)(に|へ)進|^head\s+(north|south|east|west)""", RegexOption.IGNORE_CASE)

    private fun String.containsAny(vararg words: String) = words.any { contains(it, ignoreCase = true) }

    private fun lastIndexOfAny(s: String, vararg words: String) =
        words.maxOf { s.lastIndexOf(it, ignoreCase = true) }
}
