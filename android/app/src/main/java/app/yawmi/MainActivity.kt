package app.yawmi

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView

    private val scanLauncher = registerForActivityResult(ScanContract()) { r ->
        val c = r.contents
        js("window.onNativeScan&&window.onNativeScan(" + (if (c == null) "null" else JSONObject.quote(c)) + ")")
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotifSettings()
        js("window.onNativeStatus&&window.onNativeStatus()")
    }

    private val locLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sendLocation() else js("window.onNativeLocation&&window.onNativeLocation(null)")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifs.ensureChannels(this)
        web = WebView(this)
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.textZoom = 100
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (e: Exception) {
                    // no browser
                }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onNativeBack&&window.onNativeBack())?'1':'0'") { r ->
                    if (r?.contains("1") != true) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        web.loadUrl("https://$HOST/assets/index.html")
        Thread { Scheduler.rescheduleAll(this) }.start()
    }

    override fun onResume() {
        super.onResume()
        js("window.onNativeResume&&window.onNativeResume()")
    }

    private fun js(code: String) {
        runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(code, null) }
    }

    private fun openNotifSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            // nothing to open
        }
    }

    private fun open(intent: Intent) {
        runOnUiThread {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                openAppDetails()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLocation() {
        val deliver = { l: Location? ->
            js(
                if (l == null) "window.onNativeLocation&&window.onNativeLocation(null)"
                else "window.onNativeLocation&&window.onNativeLocation({lat:${l.latitude},lng:${l.longitude}})"
            )
        }
        try {
            val lm = getSystemService(LocationManager::class.java)
            val last = listOf(
                LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { p ->
                try {
                    lm.getLastKnownLocation(p)
                } catch (e: Exception) {
                    null
                }
            }.maxByOrNull { it.time }
            if (last != null) {
                deliver(last)
                return
            }
            if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, mainExecutor) { l -> deliver(l) }
                return
            }
        } catch (e: Exception) {
            // fall through
        }
        deliver(null)
    }

    inner class Bridge {
        private val ctx get() = this@MainActivity

        @JavascriptInterface
        fun load(): String = Store.read(ctx)

        @JavascriptInterface
        fun save(json: String) {
            Store.write(ctx, json)
            Scheduler.rescheduleAll(ctx)
        }

        @JavascriptInterface
        fun prayerTimes(date: String): String {
            val d = try {
                LocalDate.parse(date)
            } catch (e: Exception) {
                LocalDate.now()
            }
            val (lat, lng) = Scheduler.location(Store.settings(ctx))
            val o = JSONObject()
            PrayerTimes.compute(d, lat, lng, ZoneId.systemDefault()).forEach { (k, v) ->
                o.put(k, String.format(Locale.US, "%02d:%02d", v / 60, v % 60))
            }
            return o.toString()
        }

        @JavascriptInterface
        fun status(): String {
            val o = JSONObject()
            o.put("notifications", Notifs.canPost(ctx))
            o.put("exact", Scheduler.canExact(ctx))
            o.put(
                "fullScreen",
                if (Build.VERSION.SDK_INT >= 34) getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                else true
            )
            o.put("battery", getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
            o.put(
                "camera",
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            )
            val pi = packageManager.getPackageInfo(packageName, 0)
            o.put("version", pi.versionName ?: "")
            o.put("versionCode", PackageInfoCompat.getLongVersionCode(pi))
            o.put("nextAlarm", Scheduler.nextAlarm(ctx))
            return o.toString()
        }

        @JavascriptInterface
        fun requestNotifications() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotifSettings()
                }
            }
        }

        @JavascriptInterface
        fun openExactAlarmSettings() {
            if (Build.VERSION.SDK_INT >= 31) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        @JavascriptInterface
        fun openFullScreenSettings() {
            if (Build.VERSION.SDK_INT >= 34) {
                open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }

        @SuppressLint("BatteryLife")
        @JavascriptInterface
        fun requestBattery() {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        @JavascriptInterface
        fun scanCode() {
            runOnUiThread {
                scanLauncher.launch(
                    ScanOptions()
                        .setPrompt("امسح باركود حاجة بعيدة عن سريرك")
                        .setBeepEnabled(true)
                        .setOrientationLocked(false)
                )
            }
        }

        @JavascriptInterface
        fun testAlarm(seconds: Int) {
            Scheduler.testAlarm(ctx, seconds)
        }

        @JavascriptInterface
        fun requestLocation() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    sendLocation()
                } else {
                    locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        /** Status bar follows the in-app theme (which can differ from the system theme). */
        @JavascriptInterface
        fun setBars(color: String, light: Boolean) {
            runOnUiThread {
                try {
                    val c = android.graphics.Color.parseColor(color)
                    window.statusBarColor = c
                    window.navigationBarColor = c
                    web.setBackgroundColor(c)
                    val ctl = WindowCompat.getInsetsController(window, window.decorView)
                    ctl.isAppearanceLightStatusBars = light
                    ctl.isAppearanceLightNavigationBars = light
                } catch (e: Exception) {
                    // keep the theme colors
                }
            }
        }

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("yawmi", text))
            }
        }
    }

    companion object {
        const val HOST = "appassets.androidplatform.net"
    }
}
