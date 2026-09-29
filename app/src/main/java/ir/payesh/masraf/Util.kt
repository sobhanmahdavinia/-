package ir.payesh.masraf

import java.util.Locale

fun String.fa(): String = buildString {
    for (c in this@fa) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

/** ارقام فارسی/عربی را به لاتین تبدیل می‌کند و بقیه‌ی کاراکترها را حذف می‌کند. */
fun String.toDigits(): String = buildString {
    for (c in this@toDigits) when (c) {
        in '0'..'9' -> append(c)
        in '۰'..'۹' -> append('0' + (c - '۰'))
        in '٠'..'٩' -> append('0' + (c - '٠'))
    }
}

fun formatMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h ساعت و $m دقیقه"
        h > 0 -> "$h ساعت"
        else -> "$m دقیقه"
    }.fa()
}

fun formatHms(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60).fa()
}
