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
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shown over the lock screen while the alarm rings.
 * The alarm stops only when the registered barcode is scanned,
 * or (backup, so nobody is ever locked out) after 3 math problems.
 */
class AlarmActivity : AppCompatActivity() {
    private var scanner: DecoratedBarcodeView? = null
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

    private val bg = 0xFF0F1A17.toInt()
    private val mint = 0xFF6CC2AB.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val soft = 0xFFC9DDD5.toInt()
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // the alarm cannot be dismissed with the back button
            }
        })
        test = intent.getBooleanExtra("test", false)
        val st = Store.settings(this)
        code = st.optJSONObject("alarm")?.str("code")
        buildUi(st.str("name"))
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
            setBackgroundColor(bg)
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

        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (code != null && camOk) {
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

    private fun onScan(value: String?) {
        if (done || value == null) return
        if (value == code) dismiss() else status.text = "ده مش نفس الكود. امسح الحاجة اللي سجلتها."
    }

    private fun showMath() {
        scanner?.pause()
        scanner?.visibility = View.GONE
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
        stopService(Intent(this, AlarmService::class.java))
        if (!test) Scheduler.followUp(this)
        scanner?.pause()
        scanner?.visibility = View.GONE
        mathBox.visibility = View.GONE
        switchBtn.visibility = View.GONE
        status.textSize = 22f
        status.text = if (test) "التجربة نجحت. كده المنبه جاهز." else "صباح النور. اتوضا وصلّي."
        window.decorView.postDelayed({ finish() }, 2200)
    }

    override fun onResume() {
        super.onResume()
        if (!done && scanner?.visibility == View.VISIBLE) scanner?.resume()
    }

    override fun onPause() {
        super.onPause()
        scanner?.pause()
    }
}
