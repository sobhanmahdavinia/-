package ir.payesh.masraf

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.util.Calendar
import kotlin.math.abs

/**
 * ساعت قابل‌اعتماد: بر پایه‌ی elapsedRealtime (که با تغییر ساعت گوشی عوض نمی‌شود).
 * اگر کاربر ساعت گوشی را بیش از ۲ دقیقه جابه‌جا کند، این ساعت تغییر را نادیده می‌گیرد.
 * (با ریستارت گوشی، ساعت دوباره از ساعت سیستم مقداردهی می‌شود.)
 */
object TrustedClock {
    private const val TOLERANCE_MS = 120_000L
    private lateinit var prefs: SharedPreferences
    private var base = 0L // = wallClock - elapsedRealtime

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("clock", Context.MODE_PRIVATE)
        val e = SystemClock.elapsedRealtime()
        val w = System.currentTimeMillis()
        val savedBase = prefs.getLong("base", 0L)
        val lastE = prefs.getLong("lastE", Long.MAX_VALUE)
        base = when {
            savedBase == 0L || e < lastE -> w - e // اولین اجرا یا ریستارت
            abs((w - e) - savedBase) <= TOLERANCE_MS -> w - e
            else -> savedBase // ساعت دستی تغییر کرده؛ نادیده بگیر
        }
        save()
    }

    @Synchronized
    fun now(): Long {
        val e = SystemClock.elapsedRealtime()
        val cur = System.currentTimeMillis() - e
        if (abs(cur - base) <= TOLERANCE_MS) base = cur // اصلاح‌های کوچک (NTP)
        return base + e
    }

    @Synchronized
    fun save() {
        if (!::prefs.isInitialized) return
        prefs.edit().putLong("base", base).putLong("lastE", SystemClock.elapsedRealtime()).apply()
    }

    fun startOfDay(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun nextMidnight(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(t)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
