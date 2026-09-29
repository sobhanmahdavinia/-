package ir.payesh.masraf

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object Permissions {
    data class Status(val usage: Boolean, val a11y: Boolean, val admin: Boolean, val battery: Boolean) {
        val all: Boolean get() = usage && a11y && admin && battery
    }

    fun read(ctx: Context) = Status(
        usage = UsageReader.hasAccess(ctx),
        a11y = a11yEnabled(ctx),
        admin = (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isAdminActive(ComponentName(ctx, AdminReceiver::class.java)),
        battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName),
    )

    private fun a11yEnabled(ctx: Context): Boolean {
        val v = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, GuardService::class.java)
        return v.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun launch(ctx: Context, intent: Intent, fallback: Intent? = null) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { if (fallback != null) ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    fun openUsageAccess(ctx: Context) = launch(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openAccessibility(ctx: Context) = launch(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun openAdmin(ctx: Context) = launch(
        ctx,
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "برای جلوگیری از حذف ساده‌ی برنامه در زمان قفل بودن، این گزینه را فعال کنید.",
            ),
    )

    fun openBattery(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
}
