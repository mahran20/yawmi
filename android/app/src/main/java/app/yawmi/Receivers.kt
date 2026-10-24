package app.yawmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

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
        var title = intent.getStringExtra("title") ?: ""
        var text = intent.getStringExtra("text") ?: ""
        var action: String? = null
        when (kind) {
            "tick" -> return
            "prayer" -> {
                if (Store.isChecked(ctx, date, item)) return
                action = "صليت ✓"
            }
            "item" -> {
                if (Store.isChecked(ctx, date, item)) return
                action = "اتعمل ✓"
            }
            "task" -> {
                if (Store.isTaskDone(ctx, item)) return
                action = "خلصت ✓"
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
        Notifs.post(ctx, code, channel, title, text, action, kind, item, date)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val test = intent.getBooleanExtra("test", false)
        try {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, AlarmService::class.java).putExtra("test", test)
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
