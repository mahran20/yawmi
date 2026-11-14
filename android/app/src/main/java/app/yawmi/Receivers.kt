package app.yawmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val kind = intent.getStringExtra("kind") ?: ""
        val item = intent.getStringExtra("item") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        when (intent.action) {
            Scheduler.ACT_MARK -> {
                if (kind == "task") {
                    if (item.isNotEmpty()) Store.markTask(ctx, item)
                } else if (item.isNotEmpty() && date.isNotEmpty()) {
                    Store.markItem(ctx, date, item)
                }
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
            }
            Scheduler.ACT_REMIND -> show(ctx, intent, kind, item, date)
        }
        Scheduler.rescheduleAll(ctx)
    }

    private fun show(ctx: Context, intent: Intent, kind: String, item: String, date: String) {
        val code = intent.getIntExtra("code", 0)
        val attempt = intent.getIntExtra("attempt", 0)
        val baseTitle = intent.getStringExtra("title") ?: ""
        val baseText = intent.getStringExtra("text") ?: ""
        var title = baseTitle
        var text = baseText
        var action: String? = null
        when (kind) {
            "tick" -> return
            "prayer" -> {
                if (Store.isChecked(ctx, date, item)) return
                action = "صليت ✓"
            }
            "item" -> {
                val data = Store.obj(ctx)
                val found = Store.findItem(data, item) ?: return
                if (Store.isChecked(ctx, date, item)) return
                val day = try {
                    LocalDate.parse(date)
                } catch (e: Exception) {
                    LocalDate.now()
                }
                val ws = Sched.weekStart(data.optJSONObject("settings") ?: org.json.JSONObject())
                if (!Sched.dueOn(data, found.first, found.second, day, ws)) return
                action = "اتعمل ✓"
                if (attempt > 0) title = "لسه: $baseTitle"
            }
            "task" -> {
                if (Store.isTaskDone(ctx, item)) return
                action = "خلصت ✓"
                if (attempt > 0) title = "لسه ماخلصتش؟"
            }
            "nightly" -> {
                val r = Store.remaining(ctx, date)
                val name = Store.settings(ctx).str("name")
                if (r.left == 0) {
                    title = if (name != null) "خلصت يومك كله يا $name" else "خلصت يومك كله"
                    text = "تصبح على خير."
                } else {
                    title = "فاضلك ${Notifs.ar(r.left)} من ${Notifs.ar(r.total)}"
                    text = "علّم على اللي عملته قبل ما تنام"
                }
                if (r.tasks > 0) text += " · ${Notifs.ar(r.tasks)} تاسك لسه مفتوح"
            }
        }
        val channel = if (kind == "prayer") Notifs.CH_PRAYER else Notifs.CH_REMIND
        Notifs.post(ctx, code % 10000, channel, title, text, action, kind, item, date)
        if (kind == "item" || kind == "task") {
            Scheduler.scheduleNag(ctx, kind, item, date, baseTitle, baseText, code, attempt)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        if (intent.action == Scheduler.ACT_TASK_RING) {
            val id = intent.getStringExtra("task") ?: ""
            if (id.isEmpty() || Store.isTaskDone(ctx, id)) return
            val text = intent.getStringExtra("text") ?: ""
            try {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, AlarmService::class.java).putExtra("mode", "task").putExtra("task", id).putExtra("text", text)
                )
            } catch (e: Exception) {
                Notifs.post(ctx, 4500, Notifs.CH_PRAYER, "تذكير", text, "خلصت ✓", "task", id, "")
            }
            Scheduler.rescheduleAll(ctx)
            return
        }
        val test = intent.getBooleanExtra("test", false)
        try {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, AlarmService::class.java).putExtra("mode", "fajr").putExtra("test", test)
            )
        } catch (e: Exception) {
            Notifs.alarmFallback(ctx, test)
        }
        if (!test) Scheduler.rescheduleAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context.applicationContext)
    }
}
