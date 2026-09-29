@file:OptIn(ExperimentalMaterial3Api::class)

package ir.payesh.masraf

import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(250)
    fun get(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return try {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            cache.put(pkg, bmp)
            bmp
        } catch (e: Exception) { null }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.Default) { IconCache.get(ctx, pkg) }
    }
    Box(Modifier.size(44.dp)) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(44.dp)) }
    }
}

@Composable
fun GateScreen(message: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Text("پایش مصرف", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text("برای ورود، هویت خود را با قفل گوشی تأیید کنید.")
        if (message != null) {
            Spacer(Modifier.size(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRetry) { Text("ورود") }
    }
}

@Composable
fun HomeScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val limits by LimitStore.limits.collectAsState()
    val status = remember(resumeTick) { Permissions.read(ctx) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }

    val limitMap = limits.associateBy { it.pkg }
    val shown = remember(apps, query, limits) {
        apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedWith(compareByDescending<AppEntry> { limitMap.containsKey(it.pkg) }.thenBy { it.label.lowercase() })
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("پایش مصرف") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { StatusCard(status) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجوی برنامه") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(shown, key = { it.pkg }) { app ->
                AppRow(app, limitMap[app.pkg]) { editing = app }
            }
        }
    }

    editing?.let { app ->
        LimitDialog(
            app = app,
            current = limitMap[app.pkg],
            onDismiss = { editing = null },
            onSave = { minutes ->
                editing = null
                scope.launch {
                    val seed = withContext(Dispatchers.IO) { UsageReader.todayTotal(ctx, app.pkg) }
                    LimitStore.setLimit(app.pkg, minutes, seed)
                }
            },
            onRemove = {
                editing = null
                LimitStore.requestRemove(app.pkg)
            },
        )
    }
}

@Composable
private fun StatusCard(s: Permissions.Status) {
    val ctx = LocalContext.current
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (s.all) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (s.all) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("همه‌ی دسترسی‌ها فعال است؛ محافظت برقرار است.", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null)
                    Spacer(Modifier.width(8.dp))
                    Text("برای کار کردن کامل برنامه، این موارد را فعال کنید:", fontWeight = FontWeight.Bold)
                }
                if (!s.usage || !s.a11y) {
                    Card(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("قدم اول (لازم برای دو مورد بعدی)", fontWeight = FontWeight.Bold)
                            Text(
                                "چون برنامه را از بیرون پلی‌استور نصب کرده‌ای، اندروید دسترسی‌ها را «محدودشده» می‌کند. " +
                                    "«باز کردن اطلاعات برنامه» را بزن، بالا سمت راست منوی سه‌نقطه (⋮) ← «اجازه‌ی تنظیمات محدودشده» " +
                                    "(Allow restricted settings) ← تأیید با قفل گوشی. بعد برگرد و دو مورد زیر را فعال کن.",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                "اگر ⋮ خاکستری بود: یک بار روی «فعال‌سازی» بزن تا خطا بدهد، برگرد و دوباره اطلاعات برنامه را باز کن.",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Button(onClick = { Permissions.openAppInfo(ctx) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("باز کردن اطلاعات برنامه")
                            }
                        }
                    }
                }
                PermRow("دسترسی به آمار مصرف", "برای اندازه‌گیری زمان استفاده از هر برنامه", s.usage) { Permissions.openUsageAccess(ctx) }
                PermRow("سرویس دسترسی‌پذیری", "برای قفل کردن برنامه‌ها (اجباری)", s.a11y) { Permissions.openAccessibility(ctx) }
                PermRow("مدیر دستگاه", "جلوگیری از حذف ساده‌ی برنامه", s.admin) { Permissions.openAdmin(ctx) }
                PermRow("بدون محدودیت باتری", "تا سیستم سرویس را نکشد", s.battery) { Permissions.openBattery(ctx) }
            }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp)
        }
        if (ok) Icon(Icons.Filled.CheckCircle, contentDescription = "فعال")
        else Button(onClick = onClick) { Text("فعال‌سازی") }
    }
}

@Composable
private fun AppRow(app: AppEntry, limit: AppLimit?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1)
                val sub = if (limit == null) "بدون محدودیت" else buildString {
                    append("سقف: ${formatMinutes(limit.limitMin)}  •  امروز: ${formatMinutes((limit.usedMs / 60_000).toInt())}")
                    when {
                        limit.pendingMin == 0 -> append("  •  حذف از فردا")
                        limit.pendingMin > 0 -> append("  •  از فردا: ${formatMinutes(limit.pendingMin)}")
                    }
                }
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (limit != null && limit.blocked) Text("🔒 قفل")
        }
        if (limit != null) {
            LinearProgressIndicator(
                progress = { (limit.usedMs.toFloat() / limit.limitMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LimitDialog(
    app: AppEntry,
    current: AppLimit?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val initial = current?.let { if (it.pendingMin > 0) it.pendingMin else it.limitMin } ?: 60
    var hours by remember { mutableStateOf((initial / 60).toString()) }
    var mins by remember { mutableStateOf((initial % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val valid = total in 1..1439

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text("سقف مصرف روزانه")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 60, 120, 180, 240).forEach { m ->
                        FilterChip(
                            selected = total == m,
                            onClick = { hours = (m / 60).toString(); mins = (m % 60).toString() },
                            label = { Text(formatMinutes(m)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.toDigits().take(2) },
                        label = { Text("ساعت") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mins,
                        onValueChange = { mins = it.toDigits().take(2) },
                        label = { Text("دقیقه") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "بعد از پایان سقف، برنامه تا ساعت ${"00:00".fa()} قفل می‌شود. در هر روز، ${MAX_EMERGENCY} بار و هر بار ۱۰ دقیقه مصرف اضطراری برای همین برنامه در دسترس است.".fa(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (current != null) {
                    Text(
                        "کاهش سقف فوراً اعمال می‌شود؛ افزایش یا حذف سقف از فردا اعمال می‌شود.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(total) }) { Text("ذخیره") } },
        dismissButton = {
            Row {
                if (current != null && current.pendingMin != 0) {
                    TextButton(onClick = onRemove) { Text("حذف محدودیت") }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}
