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
import kotlin.math.abs

/**
 * Schedules the next occurrence of every reminder and alarm.
 * Called on every save, on boot, on time changes, and after each reminder fires,
 * so the next occurrences are always queued.
 *
 * Request code ranges:
 *   999 tick · 1000 Fajr alarm · 1001 test alarm · 2000+ adhan · 3000+ items · 4000 nightly
 *   5000+ task reminders · 6000 Fajr follow-up · 8000+ task rings · 9000+ snoozed rings · 30000+ repeat reminders
 */
object Scheduler {
    const val ACT_REMIND = "app.yawmi.REMIND"
    const val ACT_MARK = "app.yawmi.MARK"
    const val ACT_FAJR = "app.yawmi.FAJR"
    const val ACT_TASK_RING = "app.yawmi.TASK_RING"

    const val CODE_TICK = 999
    const val CODE_ALARM = 1000
    const val CODE_TEST = 1001
    const val CODE_NIGHTLY = 4000
    const val CODE_FOLLOW = 6000
    private const val RING_FIRST = 8000
    private const val RING_LAST = 8899
    private const val NAG_BASE = 30000

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

    private fun logicalDateAt(t: Long, zone: ZoneId): LocalDate =
        LocalDate.parse(Store.logicalKey(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone)))

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
        val ws = Sched.weekStart(st)
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

        // A reminder at each adhan, only for prayers the user keeps in a prayers section
        val pn = st.optJSONObject("prayerNotify")
        val tracked = mutableSetOf<String>()
        val groups = data.optJSONArray("groups")
        if (groups != null) {
            for (gi in 0 until groups.length()) {
                val g = groups.optJSONObject(gi) ?: continue
                if (g.optString("kind") != "prayers" && g.optString("id") != "prayers") continue
                val items = g.optJSONArray("items") ?: continue
                for (ii in 0 until items.length()) items.optJSONObject(ii)?.str("id")?.let { tracked.add(it) }
            }
        }
        PrayerTimes.PRAYERS.forEachIndexed { i, p ->
            if (p !in tracked) return@forEachIndexed
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

        // Items with their own reminder time, only on the days they are due
        var ic = 3000
        if (groups != null) {
            outer@ for (gi in 0 until groups.length()) {
                val g = groups.optJSONObject(gi) ?: continue
                if (g.optString("kind") == "prayers" || g.optString("id") == "prayers") continue
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
                    var next = 0L
                    for (off in 0..14) {
                        val t = millis(today.plusDays(off.toLong()), lt, zone)
                        if (t <= soon) continue
                        if (Sched.dueOn(data, g, item, logicalDateAt(t, zone), ws)) {
                            next = t
                            break
                        }
                    }
                    if (next == 0L) continue
                    val label = item.str("label") ?: "بند"
                    remind(ctx, am, ic, next, "item", id, logicalDateAt(next, zone).toString(), label, "متنساش: $label")
                    codes.add(ic)
                    ic++
                }
            }
        }

        // Bedtime: sleep enough hours before the next Fajr (or the alarm), reminded 30 minutes earlier
        val fajrSt = st.optJSONObject("fajr")
        if (tracked.contains("fajr") && (fajrSt == null || fajrSt.optBoolean("bedtime", true))) {
            val hours = (fajrSt?.optInt("sleepHours", 7) ?: 7).coerceIn(5, 9)
            val alarmOn = alarm != null && alarm.optBoolean("enabled", false)
            val lead = if (alarmOn) alarm!!.optInt("offset", 15) else 10
            for (d in listOf(tomorrow, tomorrow.plusDays(1))) {
                val pt = PrayerTimes.compute(d, lat, lng, zone)
                val wake = millis(d, pt.getValue("fajr"), zone) - lead * 60_000L
                val bed = wake - hours * 3_600_000L
                val t = bed - 30 * 60_000L
                if (t <= soon) continue
                val z = Instant.ofEpochMilli(bed).atZone(zone)
                val h12 = if (z.hour % 12 == 0) 12 else z.hour % 12
                val time = Notifs.ar(h12) + ":" + (if (z.minute < 10) "٠" else "") + Notifs.ar(z.minute) + (if (z.hour < 12) " ص" else " م")
                remind(ctx, am, 4100, t, "bedtime", "", "", "جهّز نفسك للنوم", "عشان تصحى للفجر مرتاح، نام الساعة $time (${Notifs.ar(hours)} ساعات نوم)")
                codes.add(4100)
                break
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
            remind(ctx, am, CODE_NIGHTLY, t, "nightly", "", logicalDateAt(t, zone).toString(), "", "")
            codes.add(CODE_NIGHTLY)
        }

        // Tasks: a time (remind or ring), or a deadline day (the evening before and 9 AM on the day)
        val tasks = data.optJSONObject("tasks")
        var tc = 5000
        var rc = RING_FIRST
        if (tasks != null) {
            val keys = tasks.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val task = tasks.optJSONObject(id) ?: continue
                if (task.optBoolean("done", false)) continue
                val due = task.optLong("due", 0L)
                if (due <= 0L) continue
                val text = task.optString("text", "")
                val ring = task.optBoolean("ring", false)
                if (task.optBoolean("dateOnly", false)) {
                    val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    val eve = millis(day.minusDays(1), LocalTime.of(20, 0), zone)
                    val morning = millis(day, LocalTime.of(9, 0), zone)
                    if (eve > soon && tc < 5900) {
                        remind(ctx, am, tc, eve, "task", id, "", "بكرة آخر يوم", text)
                        codes.add(tc); tc++
                    }
                    if (morning > soon) {
                        if (ring && rc <= RING_LAST) {
                            ringTask(ctx, am, rc, morning, id, "النهارده آخر يوم: $text")
                            codes.add(rc); rc++
                        } else if (tc < 5900) {
                            remind(ctx, am, tc, morning, "task", id, "", "النهارده آخر يوم", text)
                            codes.add(tc); tc++
                        }
                    }
                } else if (due > soon) {
                    if (ring && rc <= RING_LAST) {
                        ringTask(ctx, am, rc, due, id, text)
                        codes.add(rc); rc++
                    } else if (tc < 5900) {
                        remind(ctx, am, tc, due, "task", id, "", "تذكير", text)
                        codes.add(tc); tc++
                    }
                }
            }
        }

        // Safety net: re-plan shortly after midnight even if nothing else fires
        val tick = millis(tomorrow, LocalTime.of(0, 5), zone)
        remind(ctx, am, CODE_TICK, tick, "tick", "", "", "", "", exact = false)
        codes.add(CODE_TICK)

        prefs.edit().putString("codes", codes.joinToString(",")).apply()
        Widget.updateAll(ctx)
    }

    private fun setExact(ctx: Context, am: AlarmManager, t: Long, pi: PendingIntent, exact: Boolean = true) {
        try {
            if (exact && canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun remind(
        ctx: Context, am: AlarmManager, code: Int, t: Long,
        kind: String, item: String, date: String, title: String, text: String,
        exact: Boolean = true, attempt: Int = 0
    ) {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACT_REMIND)
            .putExtra("kind", kind).putExtra("item", item).putExtra("date", date)
            .putExtra("title", title).putExtra("text", text).putExtra("code", code)
            .putExtra("attempt", attempt)
        val pi = PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(ctx, am, t, pi, exact)
    }

    private fun ringTask(ctx: Context, am: AlarmManager, code: Int, t: Long, id: String, text: String) {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACT_TASK_RING)
            .putExtra("task", id).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(ctx, am, t, pi)
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
        val i = when {
            code == CODE_ALARM -> Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FAJR)
            code in RING_FIRST..RING_LAST -> Intent(ctx, AlarmReceiver::class.java).setAction(ACT_TASK_RING)
            else -> Intent(ctx, ReminderReceiver::class.java).setAction(ACT_REMIND)
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

    /** Ring this task again in a few minutes (from the ringing screen). */
    fun snoozeTask(context: Context, id: String, text: String, minutes: Int) {
        val ctx = context.applicationContext
        val code = 9000 + abs(id.hashCode() % 900)
        ringTask(ctx, alarmManager(ctx), code, System.currentTimeMillis() + minutes * 60_000L, id, text)
    }

    /**
     * Repeat a reminder later if the user has not marked it done yet.
     * The receiver checks again when it fires, so a finished item is never repeated.
     */
    fun scheduleNag(context: Context, kind: String, item: String, date: String, title: String, text: String, baseCode: Int, attempt: Int) {
        val ctx = context.applicationContext
        val nag = Store.settings(ctx).optJSONObject("nag")
        if (nag != null && !nag.optBoolean("enabled", true)) return
        val every = (nag?.optInt("every", 30) ?: 30).coerceIn(5, 240)
        val max = (nag?.optInt("max", 3) ?: 3).coerceIn(1, 10)
        if (attempt >= max) return
        if (kind == "task" && Store.task(ctx, item)?.optBoolean("nag", true) == false) return
        remind(
            ctx, alarmManager(ctx), NAG_BASE + (baseCode % 10000), System.currentTimeMillis() + every * 60_000L,
            kind, item, date, title, text, attempt = attempt + 1
        )
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
