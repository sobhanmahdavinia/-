package ir.payesh.masraf

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** ردیاب ساده‌ی «برنامه‌ی فعلی» بر اساس رویدادهای UsageStats. */
private class Tracker {
    var cur: String? = null
    private var since = 0L
    private val classes = HashSet<String>()
    val total = HashMap<String, Long>()

    fun feed(type: Int, pkg: String, cls: String, t: Long) {
        when (type) {
            1 -> { // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                if (pkg != cur) { close(t); cur = pkg; since = t; classes.clear() }
                classes.add(cls)
            }
            2 -> { // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
                if (pkg == cur) { classes.remove(cls); if (classes.isEmpty()) close(t) }
            }
            16 -> close(t) // SCREEN_NON_INTERACTIVE
        }
    }

    fun close(t: Long) {
        val c = cur ?: return
        total[c] = (total[c] ?: 0L) + (t - since).coerceAtLeast(0L)
        cur = null
        classes.clear()
    }

    fun reset() { cur = null; classes.clear() }
}

object UsageReader {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** مجموع زمان جلوی صفحه بودن هر برنامه (نشست‌های بسته‌شده) در بازه‌ی داده‌شده. */
    fun closedTotals(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(from, to) ?: return emptyMap()
        val e = UsageEvents.Event()
        val tr = Tracker()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val p = e.packageName ?: continue
            tr.feed(e.eventType, p, e.className ?: "", e.timeStamp)
        }
        return tr.total
    }

    /** مصرف امروز (بر اساس ساعت قابل‌اعتماد، با تبدیل به ساعت سیستم برای UsageStats). */
    fun todayTotals(ctx: Context): Map<String, Long> {
        val wallNow = System.currentTimeMillis()
        val tNow = TrustedClock.now()
        val from = TrustedClock.startOfDay(tNow) + (wallNow - tNow)
        return closedTotals(ctx, from, wallNow + 1000)
    }

    fun todayTotal(ctx: Context, pkg: String): Long = todayTotals(ctx)[pkg] ?: 0L
}

class ForegroundDetector(ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val tracker = Tracker()
    private var lastEnd = 0L

    fun current(): String? {
        val now = System.currentTimeMillis()
        var from = if (lastEnd == 0L) now - 3 * 3_600_000L else lastEnd - 3000
        if (from > now) { from = now - 3 * 3_600_000L; tracker.reset() }
        val ev = usm.queryEvents(from, now + 1000) ?: return tracker.cur
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val p = e.packageName ?: continue
            tracker.feed(e.eventType, p, e.className ?: "", e.timeStamp)
        }
        lastEnd = now
        return tracker.cur
    }
}
