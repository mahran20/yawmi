package app.yawmi

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The morning brief and the evening review, built from the user's data.
 * Plain logic (no Android types) so it can be unit tested.
 */
object Brief {
    data class Note(val title: String, val text: String)

    /** Same icons as TASK_KINDS in the page. */
    private val KIND_ICON = mapOf(
        "task" to "✅", "meeting" to "📅", "work" to "💼", "errand" to "🚗", "call" to "📞", "study" to "📚"
    )

    fun icon(task: JSONObject): String = KIND_ICON[task.optString("kind")] ?: "✅"

    fun time12(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return Notifs.ar(h12) + ":" + (if (m < 10) "٠" else "") + Notifs.ar(m) + (if (h < 12) " ص" else " م")
    }

    /** Joins up to [max] names, then "و٣ كمان". */
    private fun list(names: List<String>, max: Int = 4): String {
        val shown = names.take(max).joinToString("، ")
        return if (names.size > max) "$shown، و${Notifs.ar(names.size - max)} كمان" else shown
    }

    private class OpenTask(val text: String, val icon: String, val due: Long, val dateOnly: Boolean, val day: LocalDate?, val minutes: Int)

    private fun openTasks(data: JSONObject, zone: ZoneId): List<OpenTask> {
        val out = mutableListOf<OpenTask>()
        val tasks = data.optJSONObject("tasks") ?: return out
        for (id in tasks.keys()) {
            val t = tasks.optJSONObject(id) ?: continue
            if (t.optBoolean("done", false)) continue
            val text = t.optString("text").trim()
            if (text.isEmpty()) continue
            val due = t.optLong("due", 0L)
            val at = if (due > 0) Instant.ofEpochMilli(due).atZone(zone) else null
            out.add(OpenTask(text, icon(t), due, t.optBoolean("dateOnly", false), at?.toLocalDate(), at?.let { it.hour * 60 + it.minute } ?: 0))
        }
        return out.sortedBy { if (it.due > 0) it.due else Long.MAX_VALUE }
    }

    /** Items scheduled for [day] (weekly items left out), with whether each is checked. */
    private fun habits(data: JSONObject, day: LocalDate): List<Pair<String, Boolean>> {
        val checks = data.optJSONObject("days")?.optJSONObject(day.toString())?.optJSONObject("checks")
        val out = mutableListOf<Pair<String, Boolean>>()
        val groups = data.optJSONArray("groups") ?: return out
        for (gi in 0 until groups.length()) {
            val g = groups.optJSONObject(gi) ?: continue
            val items = g.optJSONArray("items") ?: continue
            for (ii in 0 until items.length()) {
                val item = items.optJSONObject(ii) ?: continue
                val id = item.str("id") ?: continue
                val s = Sched.of(g, item)
                if (Sched.isWeekly(s) || !Sched.scheduledOn(s, day)) continue
                out.add(item.optString("label") to (checks?.optBoolean(id, false) == true))
            }
        }
        return out
    }

    private fun appointments(tasks: List<OpenTask>, day: LocalDate): List<String> =
        tasks.filter { it.day == day }.map { if (it.dateOnly) "${it.icon} ${it.text} (آخر يوم)" else "${it.icon} ${it.text} ${time12(it.minutes)}" }

    fun morning(data: JSONObject, day: LocalDate, zone: ZoneId): Note {
        val name = data.optJSONObject("settings")?.str("name")
        val tasks = openTasks(data, zone)
        val today = appointments(tasks, day)
        val late = tasks.filter { it.day != null && it.day.isBefore(day) }.map { it.text }
        val loose = tasks.count { it.day == null }
        val habitCount = habits(data, day).size
        val lines = mutableListOf<String>()
        if (today.isNotEmpty()) lines.add("النهارده: " + list(today))
        if (late.isNotEmpty()) lines.add("⏳ متأخر: " + list(late, 3))
        if (loose > 0) lines.add(if (loose == 1) "📝 تاسك واحد من غير معاد" else "📝 ${Notifs.ar(loose)} تاسكات من غير معاد")
        if (habitCount > 0) lines.add("🌱 ${Notifs.ar(habitCount)} ${if (habitCount == 1) "عادة" else "عادات"} النهارده")
        if (lines.isEmpty()) lines.add("يومك فاضي من المواعيد. ضيف أي حاجة جدّت من التطبيق.")
        val title = (if (name != null) "صباح الخير يا $name" else "صباح الخير") +
            when (today.size) {
                0 -> ""
                1 -> " · عندك ميعاد النهارده"
                2 -> " · عندك ميعادين النهارده"
                else -> " · عندك ${Notifs.ar(today.size)} مواعيد النهارده"
            }
        return Note(title, lines.joinToString("\n"))
    }

    fun evening(data: JSONObject, day: LocalDate, zone: ZoneId): Note {
        val name = data.optJSONObject("settings")?.str("name")
        val h = habits(data, day)
        val left = h.filter { !it.second }.map { it.first }
        val tasks = openTasks(data, zone)
        val pending = tasks.filter { it.day != null && !it.day.isAfter(day) }.map { if (it.day!!.isBefore(day)) "${it.text} (متأخر)" else it.text }
        val tomorrow = appointments(tasks, day.plusDays(1))
        val lines = mutableListOf<String>()
        if (left.isNotEmpty()) lines.add("لسه: " + list(left))
        if (pending.isNotEmpty()) lines.add("تاسكات: " + list(pending, 3))
        if (tomorrow.isNotEmpty()) lines.add("بكرة: " + list(tomorrow, 3))
        val title = when {
            h.isEmpty() && pending.isEmpty() -> if (name != null) "يومك خلص يا $name" else "يومك خلص"
            left.isEmpty() -> if (name != null) "خلصت عاداتك كلها يا $name 👏" else "خلصت عاداتك كلها 👏"
            else -> "فاضلك ${Notifs.ar(left.size)} من ${Notifs.ar(h.size)}"
        }
        if (left.isNotEmpty() || pending.isNotEmpty()) lines.add("علّم على اللي عملته قبل ما تنام")
        if (lines.isEmpty()) lines.add("تصبح على خير.")
        return Note(title, lines.joinToString("\n"))
    }
}
