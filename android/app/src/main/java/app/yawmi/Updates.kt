package app.yawmi

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Notifications that keep people up to date without a server:
 * - after the app updates: what's new in this version (read from WHATS_NEW in the page);
 * - once a day, with the morning brief: whether a newer version is out (version.json on GitHub Pages);
 * - rarely: a one-time tip about a key feature the user hasn't set up yet.
 * All of it can be turned off in Settings ("updatesNotify").
 */
object Updates {
    private const val SITE = "https://mahran20.github.io/yawmi/"
    const val APK = "https://github.com/mahran20/yawmi/releases/latest/download/yawmi.apk"
    private const val DAY = 24 * 3600_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("yawmi_notes", Context.MODE_PRIVATE)

    private fun enabled(ctx: Context) = Store.settings(ctx).optBoolean("updatesNotify", true)

    fun currentVersion(ctx: Context): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        0
    }

    private fun bullets(notes: List<String>) = notes.take(4).joinToString("\n") { "• $it" }

    /** Right after an update: tell the user what's new, once per version. */
    fun justUpdated(ctx: Context) {
        val v = currentVersion(ctx)
        val p = prefs(ctx)
        if (v <= 0 || p.getInt("newsNotified", 0) >= v) return
        p.edit().putInt("newsNotified", v).apply()
        if (!enabled(ctx)) return
        val notes = PageData.whatsNew(ctx, v)
        if (notes.isEmpty()) return
        Notifs.post(ctx, 4310, Notifs.CH_REMIND, "✨ يومي اتحدّث، جرّب الجديد", bullets(notes), null, "news", "", "")
    }

    /** Runs off the main thread with the morning brief; always finishes [pending]. */
    fun daily(ctx: Context, pending: BroadcastReceiver.PendingResult?) {
        Thread {
            try {
                if (enabled(ctx)) {
                    checkForUpdate(ctx)
                    tip(ctx)
                }
            } catch (e: Exception) {
                // offline or no data yet: try again tomorrow
            } finally {
                pending?.finish()
            }
        }.start()
    }

    private fun checkForUpdate(ctx: Context) {
        val conn = URL(SITE + "version.json").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        val body = try {
            if (conn.responseCode != 200) return
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val j = JSONObject(body)
        val v = j.optInt("versionCode", 0)
        val p = prefs(ctx)
        if (v <= currentVersion(ctx) || v <= p.getInt("updNotified", 0)) return
        p.edit().putInt("updNotified", v).apply()
        val arr = j.optJSONArray("notes")
        val notes = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        val name = j.optString("versionName", "")
        val title = "🎉 فيه تحديث جديد ليومي" + if (name.isNotEmpty()) " ($name)" else ""
        val text = (if (notes.isNotEmpty()) bullets(notes) + "\n" else "") + "دوس عشان تنزّله. بياناتك هتفضل زي ما هي."
        Notifs.link(ctx, 4300, title, text, APK)
    }

    /** At most one tip every 3 days, each tip once, and not in the first 2 days. */
    private fun tip(ctx: Context) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val data = Store.obj(ctx)
        val installAt = data.optJSONObject("meta")?.optLong("installAt", now) ?: now
        if (now - installAt < 2 * DAY || now - p.getLong("lastTip", 0L) < 3 * DAY) return
        val shown = p.getStringSet("tipsShown", emptySet()) ?: emptySet()
        val widgets = try {
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, YawmiWidget::class.java)).size
        } catch (e: Exception) {
            1
        }
        val t = Tips.next(data, shown, widgets > 0) ?: return
        p.edit().putLong("lastTip", now).putStringSet("tipsShown", shown + t.id).apply()
        Notifs.post(ctx, 4400, Notifs.CH_REMIND, t.title, t.text, null, "tip", "", "")
    }
}

/** One-time suggestions for key features the user hasn't set up. Plain logic, unit tested. */
object Tips {
    data class Tip(val id: String, val title: String, val text: String)

    fun next(data: JSONObject, shown: Set<String>, hasWidget: Boolean): Tip? {
        val alarm = data.optJSONObject("settings")?.optJSONObject("alarm") ?: JSONObject()
        val hasPhoto = alarm.optString("method") == "photo" && alarm.optJSONObject("photo") != null
        val hasTasks = (data.optJSONObject("tasks")?.length() ?: 0) > 0
        val candidates = listOf(
            Tip("alarm", "🌅 جرّبت منبه الفجر؟", "مش بيسكت غير لما تقوم تمسح كود في الحمام. شغّله من كارت الفجر في التطبيق.")
                .takeIf { !alarm.optBoolean("enabled", false) },
            Tip("code", "منبه الفجر محتاج كود", "من غيره هيسكت بمسائل حساب بس. امسح باركود معجون السنان من الإعدادات، في أقل من دقيقة.")
                .takeIf { alarm.optBoolean("enabled", false) && alarm.str("code") == null && !hasPhoto },
            Tip("widget", "📌 خلّي يومك قدامك", "ضيف Widget يومي: دوس كتير على الشاشة الرئيسية، وبعدين Widgets، واختار يومي. وعلّم من غير ما تفتح التطبيق.")
                .takeIf { !hasWidget },
            Tip("tasks", "📝 عندك ميتنج أو مشوار؟", "ضيفه في التاسكات بمعاده، ويومي يفكّرك بيه. والميتنج يفكّرك قبله بربع ساعة.")
                .takeIf { !hasTasks }
        )
        return candidates.filterNotNull().firstOrNull { it.id !in shown }
    }
}
