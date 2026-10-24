package app.yawmi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

object Notifs {
    const val CH_ALARM = "fajr_alarm"
    const val CH_PRAYER = "prayers"
    const val CH_REMIND = "reminders"
    private const val COLOR = 0xFF1F6B5C.toInt()

    private val arFormat: NumberFormat = NumberFormat.getInstance(Locale.forLanguageTag("ar-EG"))
    fun ar(n: Int): String = arFormat.format(n.toLong())

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "منبه الفجر", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "المنبه اللي مش بيسكت غير لما تقوم"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val prayer = NotificationChannel(CH_PRAYER, "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تذكير عند كل أذان"
        }
        val remind = NotificationChannel(CH_REMIND, "التذكيرات", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "البنود والتاسكات وتذكير آخر اليوم"
        }
        nm.createNotificationChannels(listOf(alarm, prayer, remind))
    }

    fun canPost(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    private fun openApp(ctx: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        ctx, 20000 + code,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun alarmActivityIntent(ctx: Context, test: Boolean): PendingIntent = PendingIntent.getActivity(
        ctx, 7000,
        Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra("test", test),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @SuppressLint("MissingPermission")
    fun post(
        ctx: Context, code: Int, channel: String, title: String, text: String,
        action: String?, kind: String, item: String, date: String
    ) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx, code))
            .setAutoCancel(true)
            .setPriority(
                if (channel == CH_PRAYER) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
        if (action != null) {
            val mark = Intent(ctx, ReminderReceiver::class.java).setAction(Scheduler.ACT_MARK)
                .putExtra("kind", kind).putExtra("item", item).putExtra("date", date)
                .putExtra("nid", code)
            val pi = PendingIntent.getBroadcast(
                ctx, 10000 + code, mark,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, action, pi)
        }
        try {
            NotificationManagerCompat.from(ctx).notify(code, b.build())
        } catch (e: SecurityException) {
            // permission revoked between check and post
        }
    }

    /** Used only if the ringing service cannot start: a loud full-screen notification instead. */
    @SuppressLint("MissingPermission")
    fun alarmFallback(ctx: Context, test: Boolean) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val full = alarmActivityIntent(ctx, test)
        val n = NotificationCompat.Builder(ctx, CH_PRAYER)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(COLOR)
            .setContentTitle("الفجر")
            .setContentText("قوم، الصلاة خير من النوم")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(7001, n)
        } catch (e: SecurityException) {
            // nothing else we can do
        }
    }
}
