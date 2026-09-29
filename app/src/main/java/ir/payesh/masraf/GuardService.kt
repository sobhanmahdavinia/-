package ir.payesh.masraf

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * قلب برنامه: هر ثانیه برنامه‌ی جلوی صفحه را می‌سنجد، مصرف را جمع می‌زند
 * و اگر سقف تمام شده باشد یک صفحه‌ی تمام‌صفحه روی آن می‌کشد.
 */
class GuardService : AccessibilityService() {

    companion object {
        private const val TAG = "GuardService"
        private const val EMERGENCY_WAIT_MS = 10_000L
        private val IGNORED = setOf("com.android.systemui", "android")
        private val GUARDED_SETTINGS = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var detector: ForegroundDetector? = null
    private var lastTick = 0L
    private var tickCount = 0
    private var hintPkg: String? = null
    private var hintAt = 0L
    private var overlay: Overlay? = null
    private var imePkgs: Set<String> = emptySet()
    private var imeAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            try { tick() } catch (t: Throwable) { Log.e(TAG, "tick failed", t) }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        TrustedClock.init(this)
        LimitStore.init(this)
        detector = ForegroundDetector(this)
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        reconcileAsync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED || isIme(pkg)) return
        hintPkg = pkg
        hintAt = SystemClock.elapsedRealtime()
        if (pkg in GUARDED_SETTINGS) guardSettings()
        try { evaluate(pkg) } catch (t: Throwable) { Log.e(TAG, "evaluate failed", t) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        io.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- tick

    private fun tick() {
        val nowE = SystemClock.elapsedRealtime()
        val delta = (nowE - lastTick).coerceIn(0L, 5000L)
        lastTick = nowE
        LimitStore.rollDayIfNeeded(TrustedClock.now())

        tickCount++
        if (tickCount % 5 == 0) { LimitStore.flush(); TrustedClock.save() }
        if (tickCount % 60 == 0) reconcileAsync()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) { hideOverlay(); return }

        val pkg = foregroundPkg()
        if (pkg != null) {
            val a = LimitStore.get(pkg)
            if (a != null && !a.blocked) LimitStore.addUsage(pkg, delta)
        }
        evaluate(pkg)
    }

    private fun foregroundPkg(): String? {
        val e = SystemClock.elapsedRealtime()
        val hint = hintPkg
        if (hint != null && e - hintAt < 2500) return hint
        val d = detector
        return if (d != null && UsageReader.hasAccess(this)) d.current() else hint
    }

    private fun evaluate(pkg: String?) {
        val a = pkg?.let { LimitStore.get(it) }
        if (a != null && a.blocked) showOverlay(a) else hideOverlay()
    }

    private fun reconcileAsync() {
        io.execute {
            try {
                if (UsageReader.hasAccess(this)) {
                    val totals = UsageReader.todayTotals(this)
                    if (totals.isNotEmpty()) LimitStore.reconcile(totals)
                }
            } catch (t: Throwable) { Log.e(TAG, "reconcile failed", t) }
        }
    }

    private fun isIme(pkg: String): Boolean {
        val e = SystemClock.elapsedRealtime()
        if (imePkgs.isEmpty() || e - imeAt > 60_000) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imePkgs = imm.enabledInputMethodList.map { it.packageName }.toSet()
            imeAt = e
        }
        return pkg in imePkgs
    }

    // ------------------------------------------------- محافظت از صفحه‌های تنظیمات

    /** وقتی حداقل یک برنامه قفل است، صفحه‌هایی که نام این برنامه را نشان می‌دهند (حذف، غیرفعال‌سازی سرویس، ...) بسته می‌شوند. */
    private fun guardSettings() {
        if (!LimitStore.anyBlocked()) return
        val label = getString(R.string.app_name)
        val check = Runnable {
            val root = rootInActiveWindow
            if (root != null && containsText(root, label, 0)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                Toast.makeText(this, "تا ساعت ۰۰:۰۰ این بخش قفل است", Toast.LENGTH_SHORT).show()
            }
        }
        handler.postDelayed(check, 300)
        handler.postDelayed(check, 1200)
    }

    private fun containsText(n: AccessibilityNodeInfo?, s: String, depth: Int): Boolean {
        if (n == null || depth > 40) return false
        if (n.text?.contains(s) == true || n.contentDescription?.contains(s) == true) return true
        for (i in 0 until n.childCount) {
            if (containsText(n.getChild(i), s, depth + 1)) return true
        }
        return false
    }

    // ------------------------------------------------------------- overlay

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun showOverlay(a: AppLimit) {
        var o = overlay
        if (o == null || o.pkg != a.pkg) {
            hideOverlay()
            o = Overlay(a.pkg)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE,
            )
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(o.root, lp)
                overlay = o
            } catch (t: Throwable) {
                Log.e(TAG, "addView failed", t)
                return
            }
        }
        o.update(a)
    }

    private fun hideOverlay() {
        val o = overlay ?: return
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(o.root) } catch (_: Throwable) {}
        overlay = null
    }

    private inner class Overlay(val pkg: String) {
        private val label: String = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        private val shownAt = SystemClock.elapsedRealtime()
        private var autoHomeAt = 0L

        private val appLine = tv("", 15f, 0xFFB0BEC5.toInt(), false)
        private val countdown = tv("", 40f, Color.WHITE, true)
        private val emergBtn = btn("", 0xFFB45309.toInt()) {
            if (LimitStore.grantEmergency(pkg)) { lastTick = SystemClock.elapsedRealtime(); hideOverlay() }
        }
        private val homeBtn = btn("بازگشت به صفحه‌ی اصلی", 0xFF0F766E.toInt()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        val root = LinearLayout(this@GuardService).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFF0F1419.toInt())
            setPadding(dp(28), dp(48), dp(28), dp(36))
            isClickable = true
            val center = LinearLayout(this@GuardService).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(tv("🔒", 56f, Color.WHITE, false))
                addView(tv("سقف مصرف شما در روز جاری به پایان رسیده است", 22f, Color.WHITE, true).apply {
                    setPadding(0, dp(16), 0, dp(8))
                })
                addView(appLine)
                addView(tv("زمان باقی‌مانده تا باز شدن قفل", 13f, 0xFF90A4AE.toInt(), false).apply {
                    setPadding(0, dp(32), 0, dp(4))
                })
                addView(countdown)
                addView(emergBtn, lpBtn(top = 40))
                addView(homeBtn, lpBtn(top = 12))
            }
            addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(tv("قفل تا ساعت ${"00:00".fa()}", 16f, 0xFFFFB74D.toInt(), true))
        }

        fun update(a: AppLimit) {
            val now = TrustedClock.now()
            val e = SystemClock.elapsedRealtime()
            appLine.text = "$label  •  سقف روزانه: ${formatMinutes(a.limitMin)}"
            countdown.text = formatHms(TrustedClock.nextMidnight(now) - now)
            if (a.emergLeft > 0) {
                val wait = ((EMERGENCY_WAIT_MS - (e - shownAt) + 999) / 1000).coerceAtLeast(0)
                if (wait > 0) {
                    emergBtn.text = "مصرف اضطراری (${wait} ثانیه صبر کنید)".fa()
                    emergBtn.alpha = 0.5f
                    emergBtn.isEnabled = false
                } else {
                    emergBtn.text = "مصرف اضطراری ۱۰ دقیقه‌ای (${a.emergLeft} از ${MAX_EMERGENCY} باقی‌مانده)".fa()
                    emergBtn.alpha = 1f
                    emergBtn.isEnabled = true
                }
            } else {
                emergBtn.text = "مصرف اضطراری امروز تمام شده است"
                emergBtn.alpha = 0.4f
                emergBtn.isEnabled = false
                if (autoHomeAt == 0L) autoHomeAt = e + 6000
                if (e >= autoHomeAt) {
                    autoHomeAt = e + 6000
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }

        private fun lpBtn(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

        private fun tv(text: String, sp: Float, color: Int, bold: Boolean) = TextView(this@GuardService).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        private fun btn(text: String, color: Int, onClick: () -> Unit) = TextView(this@GuardService).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }
}
