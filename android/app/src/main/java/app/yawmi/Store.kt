package app.yawmi

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/** String value or null when missing, JSON null, or blank. */
fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** All app data lives in one JSON file shared by the web UI and the native side. */
object Store {
    private const val FILE = "data.json"
    private val lock = Any()

    /** Day boundary: anything before 3 AM counts toward the previous day. */
    const val CUTOFF_HOUR = 3

    fun read(ctx: Context): String = synchronized(lock) {
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) f.readText() else ""
    }

    fun write(ctx: Context, json: String) {
        synchronized(lock) {
            val f = File(ctx.filesDir, FILE)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }

    fun obj(ctx: Context): JSONObject =
        try {
            JSONObject(read(ctx).ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }

    fun settings(ctx: Context): JSONObject = obj(ctx).optJSONObject("settings") ?: JSONObject()

    private fun JSONObject.child(key: String): JSONObject =
        optJSONObject(key) ?: JSONObject().also { put(key, it) }

    fun markItem(ctx: Context, date: String, item: String) {
        synchronized(lock) {
            val o = obj(ctx)
            val day = o.child("days").child(date)
            day.put("date", date)
            day.child("checks").put(item, true)
            write(ctx, o.toString())
        }
    }

    fun markTask(ctx: Context, id: String) {
        synchronized(lock) {
            val o = obj(ctx)
            val t = o.optJSONObject("tasks")?.optJSONObject(id) ?: return
            t.put("done", true)
            t.put("doneAt", System.currentTimeMillis())
            write(ctx, o.toString())
        }
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
        val checks = o.optJSONObject("days")?.optJSONObject(date)?.optJSONObject("checks")
        var total = 0
        var left = 0
        val groups = o.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val items = groups.optJSONObject(i)?.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    val id = items.optJSONObject(j)?.str("id") ?: continue
                    total++
                    if (checks?.optBoolean(id, false) != true) left++
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
}
