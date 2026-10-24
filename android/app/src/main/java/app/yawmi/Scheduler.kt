package app.yawmi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the next occurrence of every reminder and the Fajr alarm.
 * Called on every save, on boot, on time changes, and after each reminder fires,
 * so tomorrow's occurrences are always queued.
 */
object Scheduler {
    const val ACT_REMIND = "app.yawmi.REMIND"
    const val ACT_MARK = "app.yawmi.MARK"
    const val ACT_FAJR = "app.yawmi.FAJR"

    const val CODE_TICK = 999
    const val CODE_ALARM = 1000
    const val CODE_TEST = 1001
    const val CODE_NIGHTLY = 4000
    const val CODE_FOLLOW = 6000

    private const val PREF = "sched"

    private fun alarmManager(ctx: Context): AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    fun location(st: JSONObject): Pair<Double, Double> {
        val l = st.optJSONObject("loc")
        val lat = l?.optDouble("lat", 30.0444)?.takeIf { !it.isNaN() } ?: 30.0444
        val lng = l?.optDouble("lng", 31.2357)?.takeIf { !it.isNaN() } ?: 31.2357
        return Pair(lat, lng)
    }

    fun nextAlarm(ctx: Context): Long =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("nextAlarm", 0L)

    private fun millis(date: LocalDate, minutes: Int, zone: ZoneId): Long =
        date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()

    private fun millis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun logicalKeyAt(t: Long, zone: ZoneId): String =
        Store.logicalKey(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone))

    @Synchronized
    fun rescheduleAll(context: Context) {
        val ctx = context.applicationContext
        val am = alarmManager(ctx)
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        prefs.getString("codes", "").orEmpty().split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .forEach { cancel(ctx, am, it) }

        val codes = mutableListOf<Int>()
        val data = Store.obj(ctx)
        val st = data.optJSONObject("settings") ?: JSONObject()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val soon = now + 30_000L
        val (lat, lng) = location(st)
        val today = LocalDate.now(zone)
        val tomorrow = today.plusDays(1)
        val pt0 = PrayerTimes.compute(today, lat, lng, zone)
        val pt1 = PrayerTimes.compute(tomorrow, lat, lng, zone)

        // Fajr alarm
        var nextAlarm = 0L
        val alarm = st.optJSONObject("alarm")
        if (alarm != null && alarm.optBoolean("enabled", false)) {
            val off = alarm.optInt("offset", 15) * 60_000L
            var t = millis(today, pt0.getValue("fajr"), zone) - off
            if (t <= soon) t = millis(tomorrow, pt1.getValue("fajr"), zone) - off
            setAlarmClock(ctx, am, t, CODE_ALARM, false)
            codes.add(CODE_ALARM)
            nextAlarm = t
        }
        prefs.edit().putLong("nextAlarm", nextAlarm).apply()

        // A reminder at each adhan
        val pn = st.optJSONObject("prayerNotify")
        PrayerTimes.PRAYERS.forEachIndexed { i, p ->
            if (pn?.optBoolean(p, true) == false) return@forEachIndexed
            var day = today
            var t = millis(today, pt0.getValue(p), zone)
            if (t <= soon) {
                day = tomorrow
                t = millis(tomorrow, pt1.getValue(p), zone)
            }
            val code = 2000 + i
            remind(
                ctx, am, code, t, "prayer", p, day.toString(),
                "حان وقت صلاة ${PrayerTimes.NAMES[p]}", "لو صليت دوس «صليت»"
            )
            codes.add(code)
        }

        // Items with their own reminder time
        var ic = 3000
        val groups = data.optJSONArray("groups")
        if (groups != null) {
            outer@ for (gi in 0 until groups.length()) {
                val g = groups.optJSONObject(gi) ?: continue
                if (g.optString("id") == "prayers") continue
                val items = g.optJSONArray("items") ?: continue
                for (ii in 0 until items.length()) {
                    if (ic >= 3900) break@outer
                    val item = items.optJSONObject(ii) ?: continue
                    val id = item.str("id") ?: continue
                    val time = item.str("time") ?: continue
                    val lt = try {
                        LocalTime.parse(time)
                    } catch (e: Exception) {
                        continue
                    }
                    var t = millis(today, lt, zone)
                    if (t <= soon) t = millis(tomorrow, lt, zone)
                    val label = item.str("label") ?: "بند"
                    remind(ctx, am, ic, t, "item", id, logicalKeyAt(t, zone), label, "متنساش: $label")
                    codes.add(ic)
                    ic++
                }
            }
        }

        // End-of-day check-in
        val nightly = st.optJSONObject("nightly")
        if (nightly == null || nightly.optBoolean("enabled", true)) {
            val lt = try {
                LocalTime.parse(nightly?.str("time") ?: "22:50")
            } catch (e: Exception) {
                LocalTime.of(22, 50)
            }
            var t = millis(today, lt, zone)
            if (t <= soon) t = millis(tomorrow, lt, zone)
            remind(ctx, am, CODE_NIGHTLY, t, "nightly", "", logicalKeyAt(t, zone), "", "")
            codes.add(CODE_NIGHTLY)
        }

        // Tasks with a reminder
        val tasks = data.optJSONObject("tasks")
        var tc = 5000
        if (tasks != null) {
            val keys = tasks.keys()
            while (keys.hasNext() && tc < 5900) {
                val id = keys.next()
                val task = tasks.optJSONObject(id) ?: continue
                if (task.optBoolean("done", false)) continue
                val due = task.optLong("due", 0L)
                if (due <= soon) continue
                remind(ctx, am, tc, due, "task", id, "", "تذكير", task.optString("text", ""))
                codes.add(tc)
                tc++
            }
        }

        // Safety net: re-plan shortly after midnight even if nothing else fires
        val tick = millis(tomorrow, LocalTime.of(0, 5), zone)
        remind(ctx, am, CODE_TICK, tick, "tick", "", "", "", "", exact = false)
        codes.add(CODE_TICK)

        prefs.edit().putString("codes", codes.joinToString(",")).apply()
    }

    private fun remind(
        ctx: Context, am: AlarmManager, code: Int, t: Long,
        kind: String, item: String, date: String, title: String, text: String,
        exact: Boolean = true
    ) {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACT_REMIND)
            .putExtra("kind", kind).putExtra("item", item).putExtra("date", date)
            .putExtra("title", title).putExtra("text", text).putExtra("code", code)
        val pi = PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (exact && canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun setAlarmClock(ctx: Context, am: AlarmManager, t: Long, code: Int, test: Boolean) {
        val op = PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FAJR).putExtra("test", test),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), op)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op)
        }
    }

    private fun cancel(ctx: Context, am: AlarmManager, code: Int) {
        val i = if (code == CODE_ALARM) {
            Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FAJR)
        } else {
            Intent(ctx, ReminderReceiver::class.java).setAction(ACT_REMIND)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun testAlarm(context: Context, seconds: Int) {
        val ctx = context.applicationContext
        setAlarmClock(ctx, alarmManager(ctx), System.currentTimeMillis() + seconds * 1000L, CODE_TEST, true)
    }

    /** After the Fajr alarm is stopped: ask "did you pray?" a little later. */
    fun followUp(context: Context) {
        val ctx = context.applicationContext
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (lat, lng) = location(Store.settings(ctx))
        val pt = PrayerTimes.compute(today, lat, lng, zone)
        val t = maxOf(
            System.currentTimeMillis() + 15 * 60_000L,
            millis(today, pt.getValue("fajr"), zone) + 10 * 60_000L
        )
        remind(
            ctx, alarmManager(ctx), CODE_FOLLOW, t, "prayer", "fajr", today.toString(),
            "صليت الفجر؟", "علّم عليها من هنا"
        )
    }
}
