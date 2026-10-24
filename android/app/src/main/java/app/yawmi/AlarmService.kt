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

/** Rings at full alarm volume until AlarmActivity stops it (or 30 minutes pass). */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var prevVolume = -1
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val test = intent?.getBooleanExtra("test", false) ?: false
        Notifs.ensureChannels(this)
        val full = Notifs.alarmActivityIntent(this, test)
        val n = NotificationCompat.Builder(this, Notifs.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (test) "تجربة منبه الفجر" else "الفجر")
            .setContentText("المنبه مش هيسكت غير لما تمسح الكود")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            Notifs.alarmFallback(this, test)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (player == null) {
            startRinging()
            handler.postDelayed(timeout, 30 * 60_000L)
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        val am = getSystemService(AudioManager::class.java)
        try {
            prevVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: Exception) {
            prevVolume = -1
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uris = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            Uri.parse("android.resource://$packageName/${R.raw.alarm_fallback}")
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
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0), attrs)
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
        running = false
        super.onDestroy()
    }

    companion object {
        const val ID = 7000

        @Volatile
        var running = false
    }
}
