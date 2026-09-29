package ir.payesh.masraf

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** مدیر دستگاه: تا وقتی فعال است، برنامه را نمی‌شود به‌سادگی حذف کرد. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "با غیرفعال کردن این گزینه، محافظت برنامه در برابر حذف از بین می‌رود."
}
