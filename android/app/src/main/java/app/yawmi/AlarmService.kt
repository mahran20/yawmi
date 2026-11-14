package app.yawmi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Rings until AlarmActivity stops it.
 * mode "fajr": the phone's alarm sound at full volume, for up to 30 minutes.
 * mode "task": the app's own chime, for up to 3 minutes, then a normal reminder.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var prevVolume = -1
    private var mode = "fajr"
    private var taskId = ""
    private var taskText = ""
    private var timedOut = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        timedOut = true
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ringing = player != null
        if (!ringing) {
            mode = intent?.getStringExtra("mode") ?: "fajr"
            taskId = intent?.getStringExtra("task") ?: ""
            taskText = intent?.getStringExtra("text") ?: ""
        }
        val test = intent?.getBooleanExtra("test", false) ?: false
        val isTask = mode == "task"
        Notifs.ensureChannels(this)
        val full = Notifs.alarmActivityIntent(this, mode, test, taskId, taskText)
        val n = NotificationCompat.Builder(this, Notifs.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (isTask) "تذكير" else if (test) "تجربة منبه الفجر" else "الفجر")
            .setContentText(if (isTask) taskText else "المنبه مش هيسكت غير لما تمسح الكود")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isTask) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            if (isTask) Notifs.post(this, 4500, Notifs.CH_PRAYER, "تذكير", taskText, "خلصت ✓", "task", taskId, "")
            else Notifs.alarmFallback(this, test)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (!ringing) {
            startRinging(isTask)
            handler.postDelayed(timeout, if (isTask) 3 * 60_000L else 30 * 60_000L)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(isTask: Boolean) {
        val am = getSystemService(AudioManager::class.java)
        if (!isTask) {
            try {
                prevVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (e: Exception) {
                prevVolume = -1
            }
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val chime = Uri.parse("android.resource://$packageName/${R.raw.task_chime}")
        val fallback = Uri.parse("android.resource://$packageName/${R.raw.alarm_fallback}")
        val uris = if (isTask) listOf(chime, fallback) else listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            fallback
        )
        for (u in uris) {
            var p: MediaPlayer? = null
            try {
                p = MediaPlayer()
                p.setAudioAttributes(attrs)
                p.setDataSource(this, u)
                p.isLooping = true
                p.prepare()
                p.start()
                player = p
                break
            } catch (e: Exception) {
                p?.release()
            }
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            val pattern = if (isTask) longArrayOf(0, 300, 200, 300, 1500) else longArrayOf(0, 900, 700)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
        } catch (e: Exception) {
            // vibration is optional
        }
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yawmi:alarm")
            .apply { acquire(31 * 60_000L) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        try {
            player?.stop()
        } catch (e: Exception) {
            // already stopped
        }
        player?.release()
        player = null
        vibrator?.cancel()
        if (prevVolume >= 0) {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0)
            } catch (e: Exception) {
                // leave volume as is
            }
        }
        wake?.let { if (it.isHeld) it.release() }
        if (timedOut && mode == "task" && taskId.isNotEmpty() && !Store.isTaskDone(this, taskId)) {
            Notifs.post(this, 4500, Notifs.CH_REMIND, "فاتك تذكير", taskText, "خلصت ✓", "task", taskId, "")
            Scheduler.scheduleNag(this, "task", taskId, "", "تذكير", taskText, 4500, 0)
        }
        running = false
        super.onDestroy()
    }

    companion object {
        const val ID = 7000

        @Volatile
        var running = false
    }
}
