package app.yawmi

import org.json.JSONObject
import java.time.LocalDate

/**
 * Item schedules, mirroring the web UI:
 *  - null            -> every day
 *  - {type:"days"}   -> only on the listed weekdays (0 = Sunday ... 6 = Saturday)
 *  - {type:"weekly"} -> N times per week, any day
 * An item's own schedule wins over its section's schedule.
 */
object Sched {
    fun weekStart(st: JSONObject): Int = st.optInt("weekStart", 6).let { if (it == 0 || it == 1 || it == 6) it else 6 }

    fun of(group: JSONObject, item: JSONObject): JSONObject? =
        item.optJSONObject("sched") ?: group.optJSONObject("sched")

    fun dow(d: LocalDate): Int = d.dayOfWeek.value % 7

    fun isWeekly(s: JSONObject?): Boolean = s?.optString("type") == "weekly"

    fun scheduledOn(s: JSONObject?, d: LocalDate): Boolean {
        if (s == null || s.optString("type") != "days") return true
        val arr = s.optJSONArray("days") ?: return true
        val today = dow(d)
        for (k in 0 until arr.length()) if (arr.optInt(k, -1) == today) return true
        return false
    }

    fun weekCount(data: JSONObject, id: String, d: LocalDate, ws: Int): Int {
        val start = d.minusDays(((dow(d) - ws + 7) % 7).toLong())
        val days = data.optJSONObject("days") ?: return 0
        var n = 0
        for (k in 0..6) {
            val key = start.plusDays(k.toLong()).toString()
            if (days.optJSONObject(key)?.optJSONObject("checks")?.optBoolean(id, false) == true) n++
        }
        return n
    }

    /** Should a reminder for this item fire on this (logical) day? */
    fun dueOn(data: JSONObject, group: JSONObject, item: JSONObject, d: LocalDate, ws: Int): Boolean {
        val s = of(group, item)
        if (isWeekly(s)) return weekCount(data, item.optString("id"), d, ws) < s!!.optInt("times", 7)
        return scheduledOn(s, d)
    }
}
