package app.yawmi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shown over the lock screen while the alarm rings.
 * The alarm stops only when the registered barcode is scanned (or the registered sink is
 * photographed, if the user chose that),
 * or (backup, so nobody is ever locked out) after 3 math problems.
 */
class AlarmActivity : AppCompatActivity() {
    private var scanner: DecoratedBarcodeView? = null
    private var photo: PhotoCheck? = null
    private var photoView: View? = null
    private var photoRef: List<String> = emptyList()
    private var shootBtn: Button? = null
    private lateinit var status: TextView
    private lateinit var mathBox: LinearLayout
    private lateinit var problem: TextView
    private lateinit var answerInput: EditText
    private lateinit var switchBtn: Button
    private var answer = 0
    private var solved = 0
    private var code: String? = null
    private var done = false
    private var test = false

    private val bg = 0xFF141C38.toInt()
    private val mint = 0xFFF4B266.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val soft = 0xFFCBD3EE.toInt()
    private val M = ViewGroup.LayoutParams.MATCH_PARENT
    private val W = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val isTask = intent.getStringExtra("mode") == "task"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // the Fajr alarm cannot be dismissed with the back button; a task reminder just closes
                if (isTask) {
                    stopRing()
                    finish()
                }
            }
        })
        if (isTask) {
            buildTaskUi()
            return
        }
        test = intent.getBooleanExtra("test", false)
        val st = Store.settings(this)
        val al = st.optJSONObject("alarm")
        code = al?.str("code")
        if (al?.optString("method") == "photo") {
            val arr = al.optJSONObject("photo")?.optJSONArray("labels")
            if (arr != null) photoRef = (0 until arr.length()).map { arr.optString(it) }
        }
        buildUi(st.str("name"))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** Night to first light, the same sky as the app's Fajr card. */
    private fun dawnSky(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFF141C38.toInt(), 0xFF24346B.toInt(), 0xFF6D4D63.toInt(), 0xFFC9783A.toInt())
    )

    private fun stopRing() {
        stopService(Intent(this, AlarmService::class.java))
    }

    /** Keep content clear of the status and navigation bars (the app draws edge to edge). */
    private fun fitInsets(v: View, l: Int, t: Int, r: Int, b: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun buildTaskUi() {
        val taskId = intent.getStringExtra("task") ?: ""
        val taskText = intent.getStringExtra("text") ?: ""
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = dawnSky()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(label(18f, mint).apply { text = "تذكير" })
        root.addView(label(56f, white, true).apply {
            text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm"))
        })
        root.addView(label(24f, white, true).apply {
            text = taskText
            setPadding(0, dp(12), 0, dp(28))
        }, LinearLayout.LayoutParams(M, W))
        val done = pill("خلصتها ✓", mint, bg).apply {
            setOnClickListener {
                if (taskId.isNotEmpty()) Store.markTask(this@AlarmActivity, taskId)
                stopRing()
                Thread { Scheduler.rescheduleAll(this@AlarmActivity) }.start()
                finish()
            }
        }
        val snooze = pill("فكّرني بعد ١٠ دقايق", 0x22FFFFFF, white).apply {
            setOnClickListener {
                stopRing()
                if (taskId.isNotEmpty()) Scheduler.snoozeTask(this@AlarmActivity, taskId, taskText, 10)
                finish()
            }
        }
        val close = pill("اقفل", 0x00000000, soft).apply {
            setOnClickListener {
                stopRing()
                finish()
            }
        }
        for (b in listOf(done, snooze, close)) {
            root.addView(b, LinearLayout.LayoutParams(M, W).apply { topMargin = dp(10) })
        }
        setContentView(root)
        fitInsets(root, dp(24), dp(40), dp(24), dp(24))
    }

    private fun label(size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun pill(text: String, fill: Int, textColor: Int): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 17f
        setTextColor(textColor)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(fill)
        }
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun buildUi(name: String?) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = dawnSky()
            setPadding(dp(24), dp(40), dp(24), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(label(18f, mint).apply { text = if (test) "تجربة المنبه" else "الفجر" })
        root.addView(label(64f, white, true).apply {
            text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm"))
        })
        root.addView(label(20f, white).apply {
            text = if (name != null) "قوم يا $name، الصلاة خير من النوم" else "قوم، الصلاة خير من النوم"
            setPadding(0, dp(4), 0, dp(14))
        })
        status = label(16f, soft)
        root.addView(status, LinearLayout.LayoutParams(M, W).apply { bottomMargin = dp(12) })

        val frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(M, 0, 1f))

        mathBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        problem = label(36f, white, true).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
        }
        answerInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 28f
            setTextColor(white)
            setHintTextColor(0x88FFFFFF.toInt())
            hint = "الإجابة"
            gravity = Gravity.CENTER
            setOnEditorActionListener { _, _, _ ->
                check()
                true
            }
        }
        mathBox.addView(problem, LinearLayout.LayoutParams(M, W))
        mathBox.addView(answerInput, LinearLayout.LayoutParams(dp(220), W).apply { topMargin = dp(8) })
        mathBox.addView(
            pill("تأكيد", mint, bg).apply { setOnClickListener { check() } },
            LinearLayout.LayoutParams(dp(220), W).apply { topMargin = dp(12) }
        )
        frame.addView(mathBox, FrameLayout.LayoutParams(M, W, Gravity.CENTER))

        switchBtn = pill("مش قادر أمسح؟ حل ٣ مسائل بدلها", 0x22FFFFFF, white).apply {
            setOnClickListener { showMath() }
        }
        root.addView(switchBtn, LinearLayout.LayoutParams(M, W).apply { topMargin = dp(12) })
        setContentView(root)
        fitInsets(root, dp(24), dp(40), dp(24), dp(24))

        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (photoRef.size >= PhotoCheck.MIN_REFERENCE && camOk) {
            val pv = androidx.camera.view.PreviewView(this)
            frame.addView(pv, 0, FrameLayout.LayoutParams(M, M))
            photoView = pv
            val p = PhotoCheck(this, pv)
            photo = p
            p.start { showMath() }
            val btn = pill("صوّر الحوض", mint, bg).apply { setOnClickListener { shootPhoto() } }
            root.addView(btn, root.indexOfChild(switchBtn), LinearLayout.LayoutParams(M, W).apply { topMargin = dp(12) })
            shootBtn = btn
            status.text = "روح الحمام وصوّر الحوض عشان المنبه يسكت"
        } else if (code != null && camOk) {
            val bv = DecoratedBarcodeView(this)
            bv.setStatusText("")
            frame.addView(bv, 0, FrameLayout.LayoutParams(M, M))
            bv.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    onScan(result.text)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
            })
            scanner = bv
            status.text = "امسح الكود اللي سجلته عشان المنبه يسكت"
        } else {
            showMath()
            status.text = if (code == null) {
                "مفيش كود متسجل. حل ٣ مسائل عشان المنبه يسكت"
            } else {
                "الكاميرا مش متاحة. حل ٣ مسائل عشان المنبه يسكت"
            }
        }
    }

    private fun shootPhoto() {
        val p = photo ?: return
        if (done) return
        shootBtn?.isEnabled = false
        status.text = "ثانية..."
        p.shoot { labels ->
            shootBtn?.isEnabled = true
            if (done) return@shoot
            when {
                labels == null -> status.text = "مقدرتش أصوّر. جرب تاني."
                PhotoCheck.matches(photoRef, labels) -> dismiss()
                else -> status.text = "مش شبه المكان اللي سجلته. صوّر الحوض من نفس الزاوية والنور مفتوح."
            }
        }
    }

    private fun onScan(value: String?) {
        if (done || value == null) return
        if (value == code) dismiss() else status.text = "ده مش نفس الكود. امسح الحاجة اللي سجلتها."
    }

    private fun showMath() {
        scanner?.pause()
        scanner?.visibility = View.GONE
        photoView?.visibility = View.GONE
        shootBtn?.visibility = View.GONE
        mathBox.visibility = View.VISIBLE
        switchBtn.visibility = View.GONE
        status.text = "حل ٣ مسائل صح عشان المنبه يسكت"
        newProblem()
        answerInput.requestFocus()
    }

    private fun newProblem() {
        val a = (12..49).random()
        val b = (3..9).random()
        val c = (10..99).random()
        answer = a * b + c
        problem.text = "$a × $b + $c"
        answerInput.setText("")
    }

    private fun check() {
        if (done) return
        val raw = answerInput.text.toString().trim()
            .map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }
            .joinToString("")
        if (raw.toIntOrNull() == answer) {
            solved++
            if (solved >= 3) {
                dismiss()
                return
            }
            status.text = "صح. فاضل ${Notifs.ar(3 - solved)}"
            newProblem()
        } else {
            status.text = "غلط، جرب تاني"
            answerInput.setText("")
        }
    }

    private fun dismiss() {
        done = true
        stopRing()
        if (!test) {
            val now = java.time.LocalDateTime.now()
            Store.setWake(this, now.toLocalDate().toString(), String.format(java.util.Locale.US, "%02d:%02d", now.hour, now.minute))
            Scheduler.followUp(this)
        }
        scanner?.pause()
        scanner?.visibility = View.GONE
        photoView?.visibility = View.GONE
        shootBtn?.visibility = View.GONE
        mathBox.visibility = View.GONE
        switchBtn.visibility = View.GONE
        status.textSize = 22f
        status.text = if (test) "التجربة نجحت. كده المنبه جاهز." else "صباح النور. اتوضا وصلّي."
        var stay = 2200L
        if (!test && Store.settings(this).optBoolean("prayerWords", true)) {
            PageData.prayerWord(this, "fajr")?.let { (_, word) ->
                status.text = "صباح النور. اتوضا وصلّي.\n\n$word"
                status.textSize = 18f
                stay = 7000L
            }
        }
        window.decorView.postDelayed({ finish() }, stay)
    }

    override fun onResume() {
        super.onResume()
        if (!done && scanner?.visibility == View.VISIBLE) scanner?.resume()
    }

    override fun onPause() {
        super.onPause()
        scanner?.pause()
    }

    override fun onDestroy() {
        photo?.close()
        super.onDestroy()
    }
}
