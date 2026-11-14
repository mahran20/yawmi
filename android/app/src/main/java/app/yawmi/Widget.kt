package app.yawmi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Home screen widget: today's progress, Fajr time and streak, and up to 4 open items to check off. */
class YawmiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widget.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Widget.ACT_TAP) return
        val ctx = context.applicationContext
        val id = intent.getStringExtra("item") ?: return
        val date = intent.getStringExtra("date") ?: return
        val target = intent.getIntExtra("target", 1)
        if (target > 1) Store.increment(ctx, date, id, target) else Store.markItem(ctx, date, id)
        Scheduler.rescheduleAll(ctx) // also refreshes the widget
    }
}

object Widget {
    const val ACT_TAP = "app.yawmi.WIDGET_TAP"
    private val ROWS = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3)

    private fun time12(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return Notifs.ar(h12) + ":" + (if (m < 10) "٠" else "") + Notifs.ar(m) + (if (h < 12) " ص" else " م")
    }

    fun updateAll(context: Context) {
        val ctx = context.applicationContext
        try {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, YawmiWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        } catch (e: Exception) {
            // a widget refresh must never break saving or reminders
        }
    }

    private fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_yawmi)
        val data = Store.obj(ctx)
        val st = data.optJSONObject("settings") ?: org.json.JSONObject()
        val ws = Sched.weekStart(st)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val key = Store.logicalKey(now)
        val day = LocalDate.parse(key)
        val days = data.optJSONObject("days")
        val checks = days?.optJSONObject(key)?.optJSONObject("checks")
        val counts = days?.optJSONObject(key)?.optJSONObject("counts")
        val (lat, lng) = Scheduler.location(st)
        val ptToday = PrayerTimes.compute(LocalDate.now(zone), lat, lng, zone)
        val nowMin = LocalTime.now(zone).let { it.hour * 60 + it.minute }

        var total = 0
        var done = 0
        var hasFajr = false
        data class Row(val id: String, val label: String, val target: Int, val count: Int)
        val rows = mutableListOf<Row>()
        val groups = data.optJSONArray("groups")
        if (groups != null) {
            for (gi in 0 until groups.length()) {
                val g = groups.optJSONObject(gi) ?: continue
                val isPrayers = g.optString("kind") == "prayers" || g.optString("id") == "prayers"
                val items = g.optJSONArray("items") ?: continue
                for (ii in 0 until items.length()) {
                    val item = items.optJSONObject(ii) ?: continue
                    val id = item.str("id") ?: continue
                    if (isPrayers && id == "fajr") hasFajr = true
                    val s = Sched.of(g, item)
                    val checked = checks?.optBoolean(id, false) == true
                    val target = item.optInt("target", 1).coerceAtLeast(1)
                    if (Sched.isWeekly(s)) {
                        if (checked) { total++; done++ }
                        else if (Sched.weekCount(data, id, day, ws) < s!!.optInt("times", 7)) {
                            rows.add(Row(id, item.optString("label"), target, counts?.optInt(id, 0) ?: 0))
                        }
                        continue
                    }
                    if (!Sched.scheduledOn(s, day)) continue
                    total++
                    if (checked) { done++; continue }
                    // a prayer shows up only once its time has come
                    if (isPrayers && PrayerTimes.NAMES.containsKey(id) && (ptToday[id] ?: 0) > nowMin) continue
                    rows.add(Row(id, item.optString("label"), target, counts?.optInt(id, 0) ?: 0))
                }
            }
        }

        v.setTextViewText(R.id.w_title, "يومي")
        v.setTextViewText(R.id.w_progress, if (total > 0) "${Notifs.ar(done)} / ${Notifs.ar(total)}" else "")

        if (hasFajr) {
            val fajrPassed = (ptToday["fajr"] ?: 0) <= nowMin
            val nextFajr = if (fajrPassed) PrayerTimes.compute(LocalDate.now(zone).plusDays(1), lat, lng, zone)["fajr"] ?: 0 else ptToday["fajr"] ?: 0
            var streak = 0
            var d = LocalDate.now(zone)
            if (days?.optJSONObject(d.toString())?.optJSONObject("checks")?.optBoolean("fajr", false) != true) d = d.minusDays(1)
            while (streak < 999 && days?.optJSONObject(d.toString())?.optJSONObject("checks")?.optBoolean("fajr", false) == true) {
                streak++
                d = d.minusDays(1)
            }
            val streakText = if (streak > 0) " · سلسلة ${Notifs.ar(streak)}" else ""
            v.setTextViewText(R.id.w_fajr, "🌅 الفجر ${time12(nextFajr)}$streakText")
            v.setViewVisibility(R.id.w_fajr, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.w_fajr, View.GONE)
        }

        for ((i, rid) in ROWS.withIndex()) {
            val r = rows.getOrNull(i)
            if (r == null) {
                v.setViewVisibility(rid, View.GONE)
                continue
            }
            val label = if (r.target > 1) "○  ${r.label}  ${Notifs.ar(r.count)}/${Notifs.ar(r.target)}" else "○  ${r.label}"
            v.setTextViewText(rid, label)
            v.setViewVisibility(rid, View.VISIBLE)
            val tap = Intent(ctx, YawmiWidget::class.java).setAction(ACT_TAP)
                .putExtra("item", r.id).putExtra("date", key).putExtra("target", r.target)
            v.setOnClickPendingIntent(
                rid,
                PendingIntent.getBroadcast(ctx, 300 + i, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }
        v.setViewVisibility(R.id.w_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.w_empty, if (total > 0) "خلصت اللي عليك النهارده ✓" else "افتح يومي وابني مساحتك")

        val open = PendingIntent.getActivity(
            ctx, 299, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        v.setOnClickPendingIntent(R.id.w_head, open)
        v.setOnClickPendingIntent(R.id.w_fajr, open)
        v.setOnClickPendingIntent(R.id.w_empty, open)
        return v
    }
}
