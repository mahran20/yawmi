package app.yawmi

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** String value or null when missing, JSON null, or blank. */
fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/**
 * All app data lives in one JSON document shared by the web UI and the native side.
 *
 * The web UI owns the document and saves it whole. Native code (widget, notification buttons,
 * the alarm) only makes small additive changes: check an item, raise a count, record a wake
 * time, finish a task. Each of those is written to disk and also sent to the open app through
 * [listener], so the app's next save includes it instead of overwriting it.
 */
object Store {
    private val lock = Any()

    /** Day boundary: anything before 3 AM counts toward the previous day. */
    const val CUTOFF_HOUR = 3

    /** Set by the open app to receive native changes; null when the app isn't running. */
    @Volatile
    var listener: ((String) -> Unit)? = null

    private fun file(ctx: Context) = DataFile(ctx.filesDir)

    /** The document text, from the backup if the main file is damaged; "" on first run or if unreadable. */
    fun read(ctx: Context): String = synchronized(lock) {
        when (val r = file(ctx).read()) {
            is DataFile.Read.Ok -> r.text
            else -> ""
        }
    }

    /** Saves the whole document from the web UI. Returns false if it was rejected. */
    fun write(ctx: Context, json: String): Boolean = synchronized(lock) {
        file(ctx).write(json)
    }

    fun obj(ctx: Context): JSONObject = synchronized(lock) {
        (file(ctx).read() as? DataFile.Read.Ok)?.obj ?: JSONObject()
    }

    fun settings(ctx: Context): JSONObject = obj(ctx).optJSONObject("settings") ?: JSONObject()

    fun snapshots(ctx: Context): List<String> = synchronized(lock) { file(ctx).snapshots() }

    /** Puts a daily snapshot back as the current document. */
    fun restore(ctx: Context, date: String): Boolean = synchronized(lock) {
        val f = file(ctx)
        val text = f.snapshot(date) ?: return false
        f.write(text)
    }

    private fun JSONObject.child(key: String): JSONObject =
        optJSONObject(key) ?: JSONObject().also { put(key, it) }

    /**
     * Applies a native change. Skipped entirely if the stored data can't be read, so a damaged
     * file is never replaced by a near-empty one. The change is then sent to the open app.
     */
    private fun mutate(ctx: Context, change: (JSONObject) -> JSONObject?) {
        val event = synchronized(lock) {
            val f = file(ctx)
            val o = when (val r = f.read()) {
                is DataFile.Read.Ok -> r.obj
                DataFile.Read.Empty -> JSONObject()
                DataFile.Read.Unreadable -> return
            }
            val ev = change(o) ?: return
            if (!f.write(o.toString())) return
            ev
        }
        try {
            listener?.invoke(event.toString())
        } catch (e: Exception) {
            // the app may be closing; it reloads from disk when it comes back
        }
    }

    private fun day(o: JSONObject, date: String): JSONObject =
        o.child("days").child(date).also { it.put("date", date) }

    fun markItem(ctx: Context, date: String, item: String) = mutate(ctx) { o ->
        day(o, date).child("checks").put(item, true)
        JSONObject().put("kind", "check").put("date", date).put("item", item)
    }

    /** Time the Fajr alarm was stopped, kept on that calendar day. */
    fun setWake(ctx: Context, date: String, hm: String) = mutate(ctx) { o ->
        day(o, date).child("fajr").put("wake", hm)
        JSONObject().put("kind", "wake").put("date", date).put("hm", hm)
    }

    /** +1 on a count habit (from the widget); marks it done when the target is reached. */
    fun increment(ctx: Context, date: String, item: String, target: Int) = mutate(ctx) { o ->
        val d = day(o, date)
        val checks = d.child("checks")
        if (checks.optBoolean(item, false)) return@mutate null
        val counts = d.child("counts")
        val n = (counts.optInt(item, 0) + 1).coerceAtMost(target)
        counts.put(item, n)
        if (n >= target) checks.put(item, true)
        JSONObject().put("kind", "count").put("date", date).put("item", item).put("n", n).put("done", n >= target)
    }

    fun markTask(ctx: Context, id: String) = mutate(ctx) { o ->
        val t = o.optJSONObject("tasks")?.optJSONObject(id) ?: return@mutate null
        val at = System.currentTimeMillis()
        t.put("done", true)
        t.put("doneAt", at)
        JSONObject().put("kind", "task").put("id", id).put("at", at)
    }

    fun isChecked(ctx: Context, date: String, item: String): Boolean =
        obj(ctx).optJSONObject("days")?.optJSONObject(date)?.optJSONObject("checks")
            ?.optBoolean(item, false) ?: false

    /** A task that no longer exists counts as done, so its reminder is skipped. */
    fun isTaskDone(ctx: Context, id: String): Boolean =
        obj(ctx).optJSONObject("tasks")?.optJSONObject(id)?.optBoolean("done", false) ?: true

    fun logicalKey(now: LocalDateTime = LocalDateTime.now()): String =
        (if (now.hour < CUTOFF_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()).toString()

    data class Left(val left: Int, val total: Int, val tasks: Int)

    fun remaining(ctx: Context, date: String): Left {
        val o = obj(ctx)
        val day = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val checks = o.optJSONObject("days")?.optJSONObject(date)?.optJSONObject("checks")
        var total = 0
        var left = 0
        val groups = o.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val g = groups.optJSONObject(i) ?: continue
                val items = g.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val id = item.str("id") ?: continue
                    val s = Sched.of(g, item)
                    val checked = checks?.optBoolean(id, false) == true
                    if (Sched.isWeekly(s)) {
                        if (checked) total++
                        continue
                    }
                    if (!Sched.scheduledOn(s, day)) continue
                    total++
                    if (!checked) left++
                }
            }
        }
        var openTasks = 0
        val tasks = o.optJSONObject("tasks")
        if (tasks != null) {
            val keys = tasks.keys()
            while (keys.hasNext()) {
                val t = tasks.optJSONObject(keys.next())
                if (t != null && !t.optBoolean("done", false)) openTasks++
            }
        }
        return Left(left, total, openTasks)
    }

    /** The section and item for an item id, or null if it was deleted. */
    fun findItem(data: JSONObject, id: String): Pair<JSONObject, JSONObject>? {
        val groups = data.optJSONArray("groups") ?: return null
        for (i in 0 until groups.length()) {
            val g = groups.optJSONObject(i) ?: continue
            val items = g.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                if (item.optString("id") == id) return Pair(g, item)
            }
        }
        return null
    }

    fun task(ctx: Context, id: String): JSONObject? = obj(ctx).optJSONObject("tasks")?.optJSONObject(id)
}
