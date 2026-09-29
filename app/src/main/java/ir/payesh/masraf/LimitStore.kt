package ir.payesh.masraf

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

const val MAX_EMERGENCY = 3
const val EMERGENCY_MS = 10 * 60_000L

/**
 * pendingMin:  -1 = چیزی در انتظار نیست | 0 = حذف از فردا | >0 = سقف جدید از فردا
 * (کاهش سقف فوری اعمال می‌شود؛ افزایش یا حذف فقط از روز بعد)
 */
data class AppLimit(
    val pkg: String,
    val limitMin: Int,
    val pendingMin: Int = -1,
    val usedMs: Long = 0L,
    val emergUsed: Int = 0,
    val emergRemainMs: Long = 0L,
) {
    val limitMs: Long get() = limitMin * 60_000L
    val blocked: Boolean get() = usedMs >= limitMs && emergRemainMs <= 0L
    val emergLeft: Int get() = (MAX_EMERGENCY - emergUsed).coerceAtLeast(0)
}

object LimitStore {
    private lateinit var prefs: SharedPreferences
    private val _limits = MutableStateFlow<List<AppLimit>>(emptyList())
    val limits: StateFlow<List<AppLimit>> = _limits
    private var dayStart = 0L

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("limits", Context.MODE_PRIVATE)
        dayStart = prefs.getLong("day", 0L)
        val arr = JSONArray(prefs.getString("apps", "[]"))
        _limits.value = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AppLimit(
                pkg = o.getString("p"),
                limitMin = o.getInt("l"),
                pendingMin = o.optInt("n", -1),
                usedMs = o.optLong("u", 0L),
                emergUsed = o.optInt("e", 0),
                emergRemainMs = o.optLong("r", 0L),
            )
        }
        rollDayIfNeeded(TrustedClock.now())
    }

    @Synchronized
    fun persist() {
        if (!::prefs.isInitialized) return
        val arr = JSONArray()
        _limits.value.forEach {
            arr.put(
                JSONObject().put("p", it.pkg).put("l", it.limitMin).put("n", it.pendingMin)
                    .put("u", it.usedMs).put("e", it.emergUsed).put("r", it.emergRemainMs)
            )
        }
        prefs.edit().putLong("day", dayStart).putString("apps", arr.toString()).apply()
    }

    fun flush() = persist()

    @Synchronized
    fun get(pkg: String): AppLimit? = _limits.value.firstOrNull { it.pkg == pkg }

    @Synchronized
    fun anyBlocked(): Boolean = _limits.value.any { it.blocked }

    private fun update(pkg: String, f: (AppLimit) -> AppLimit) {
        _limits.value = _limits.value.map { if (it.pkg == pkg) f(it) else it }
    }

    @Synchronized
    fun setLimit(pkg: String, minutes: Int, seedUsedMs: Long) {
        val cur = get(pkg)
        when {
            cur == null -> _limits.value = _limits.value + AppLimit(pkg, minutes, usedMs = seedUsedMs)
            minutes < cur.limitMin -> update(pkg) { it.copy(limitMin = minutes, pendingMin = -1) }
            minutes == cur.limitMin -> update(pkg) { it.copy(pendingMin = -1) }
            else -> update(pkg) { it.copy(pendingMin = minutes) }
        }
        persist()
    }

    @Synchronized
    fun requestRemove(pkg: String) {
        update(pkg) { it.copy(pendingMin = 0) }
        persist()
    }

    @Synchronized
    fun addUsage(pkg: String, deltaMs: Long) {
        update(pkg) {
            it.copy(
                usedMs = it.usedMs + deltaMs,
                emergRemainMs = if (it.emergRemainMs > 0) maxOf(0L, it.emergRemainMs - deltaMs) else 0L,
            )
        }
    }

    /** شروع یک نوبت ۱۰ دقیقه‌ای اضطراری (فقط وقتی برنامه قفل است و نوبت باقی مانده). */
    @Synchronized
    fun grantEmergency(pkg: String): Boolean {
        val a = get(pkg) ?: return false
        if (!a.blocked || a.emergLeft <= 0) return false
        update(pkg) { it.copy(emergUsed = it.emergUsed + 1, emergRemainMs = EMERGENCY_MS) }
        persist()
        return true
    }

    /** اگر سرویس مدتی کشته شده بود، مصرف را با آمار سیستم هم‌تراز می‌کند (فقط افزایشی). */
    @Synchronized
    fun reconcile(totals: Map<String, Long>) {
        var changed = false
        _limits.value = _limits.value.map {
            val t = totals[it.pkg] ?: 0L
            if (t > it.usedMs) { changed = true; it.copy(usedMs = t) } else it
        }
        if (changed) persist()
    }

    @Synchronized
    fun rollDayIfNeeded(now: Long) {
        val sod = TrustedClock.startOfDay(now)
        if (dayStart == 0L) { dayStart = sod; persist(); return }
        if (sod > dayStart) {
            dayStart = sod
            _limits.value = _limits.value.mapNotNull { a ->
                if (a.pendingMin == 0) null
                else a.copy(
                    limitMin = if (a.pendingMin > 0) a.pendingMin else a.limitMin,
                    pendingMin = -1, usedMs = 0L, emergUsed = 0, emergRemainMs = 0L,
                )
            }
            persist()
        }
    }
}
