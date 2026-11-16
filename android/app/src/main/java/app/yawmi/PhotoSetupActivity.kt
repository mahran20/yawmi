package app.yawmi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Registers the sink photo: two shots of the same spot, keeping the labels both agree on. */
class PhotoSetupActivity : AppCompatActivity() {
    private lateinit var check: PhotoCheck
    private lateinit var status: TextView
    private lateinit var shoot: Button
    private var first: List<String>? = null
    private var busy = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) check.start { fail("الكاميرا مش شغالة. اقفل أي تطبيق تاني بيستخدمها وجرب تاني.") }
        else fail("محتاج إذن الكاميرا عشان تصوّر.")
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF141C38.toInt())
            setPadding(dp(20), dp(24), dp(20), dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply {
            text = "صوّر الحوض (تجريبي)"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "وجّه الكاميرا على الحوض والحنفية، ونور الحمام مفتوح زي ما بيكون الفجر. هنحتاج صورتين من نفس المكان."
            textSize = 15f
            setTextColor(0xFFCBD3EE.toInt())
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(status)
        val preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        shoot = Button(this).apply {
            text = "صوّر"
            textSize = 17f
            isAllCaps = false
            setTextColor(0xFF141C38.toInt())
            setBackgroundColor(0xFFF4B266.toInt())
            setOnClickListener { take() }
        }
        root.addView(shoot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        root.addView(Button(this).apply {
            text = "إلغاء"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x22FFFFFF)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(20) + b.left, dp(24) + b.top, dp(20) + b.right, dp(20) + b.bottom)
            insets
        }

        check = PhotoCheck(this, preview)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            check.start { fail("الكاميرا مش شغالة. اقفل أي تطبيق تاني بيستخدمها وجرب تاني.") }
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun fail(msg: String) {
        status.text = msg
    }

    private fun take() {
        if (busy) return
        busy = true
        shoot.isEnabled = false
        status.text = "ثانية..."
        check.shoot { labels ->
            busy = false
            shoot.isEnabled = true
            when {
                labels == null -> fail("مقدرتش أتعرّف على الصورة. لو دي أول مرة، الموبايل بينزّل أداة التعرّف على الصور: اتأكد إن النت شغال وجرب كمان دقيقة.")
                labels.size < PhotoCheck.MIN_REFERENCE -> {
                    first = null
                    fail("مش شايف تفاصيل كفاية. قرّب من الحوض، وخلّي النور مفتوح، وجرب تاني.")
                }
                first == null -> {
                    first = labels
                    status.text = "تمام. صوّر نفس المكان تاني عشان نتأكد."
                    shoot.text = "صوّر تاني"
                }
                else -> {
                    val ref = PhotoCheck.reference(first!!, labels)
                    if (ref.size < PhotoCheck.MIN_REFERENCE) {
                        first = null
                        shoot.text = "صوّر"
                        fail("الصورتين مش شبه بعض كفاية. صوّر نفس الحتة بالظبط، وابدأ من الأول.")
                    } else {
                        Store.setPhoto(this, ref)
                        Scheduler.rescheduleAll(this)
                        status.text = "اتسجلت. المنبه هيسكت لما تصوّر نفس المكان ده."
                        shoot.visibility = View.GONE
                        window.decorView.postDelayed({ finish() }, 1800)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        check.close()
        super.onDestroy()
    }
}
