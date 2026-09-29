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
import android.os.Build
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var status by remember { mutableStateOf(Permissions.read(ctx)) }
    LaunchedEffect(resumeTick) { status = Permissions.read(ctx) }
    LaunchedEffect(Unit) { while (true) { delay(1200); status = Permissions.read(ctx) } }
    // 0 = سؤال اولیه، 1 = در حال راه‌اندازی، 2 = بسته
    var wizard by remember { mutableIntStateOf(if (status.a11y) 2 else 0) }

    if (wizard != 2) {
        Wizard(status, wizard, onStart = { wizard = 1 }, onLater = { wizard = 2 }, onFinish = { wizard = 2 })
    } else {
        HomeContent(status, onOpenWizard = { wizard = 1 })
    }
}

@Composable
private fun StepScreen(
    title: String,
    body: String,
    progress: Float,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(24.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        Text(body, fontSize = 15.sp, lineHeight = 26.sp)
        Spacer(Modifier.size(24.dp))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
        if (secondary != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
        if (tertiary != null && onTertiary != null) {
            TextButton(onClick = onTertiary) { Text(tertiary, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Wizard(s: Permissions.Status, mode: Int, onStart: () -> Unit, onLater: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var skipped by remember { mutableStateOf(setOf<String>()) }
    var restrictedAck by remember { mutableStateOf(false) }
    val needRestricted = Build.VERSION.SDK_INT >= 33

    val order = buildList {
        if (needRestricted && !s.a11y && !restrictedAck) add("restricted")
        if (!s.a11y) add("a11y")
        if (!s.usage && "usage" !in skipped) add("usage")
        if (!s.battery && "battery" !in skipped) add("battery")
        if (!s.admin && "admin" !in skipped) add("admin")
    }
    val current = order.firstOrNull()
    val progress = ((5 - order.size) / 5f).coerceIn(0f, 1f)
    if (mode == 1 && current == null) LaunchedEffect(Unit) { onFinish() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mode == 0) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text("خوش آمدی 👋", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            Text(
                "برای اینکه قفل برنامه‌ها کار کند، چند دسترسی لازم است.\nمی‌خواهی همین الان قدم‌به‌قدم و ساده فعالشان کنیم؟",
                fontSize = 16.sp,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.size(28.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("بله، شروع کن") }
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("نه، بعداً") }
        } else when (current) {
            "restricted" -> StepScreen(
                title = "قدم ۱: آزاد کردن تنظیمات محدودشده",
                body = "اندروید برای برنامه‌های نصب‌شده از بیرون پلی‌استور، دسترسی‌ها را قفل می‌کند. یک بار آزادش کن:\n" +
                    "۱) دکمه‌ی پایین را بزن تا «اطلاعات برنامه» باز شود.\n" +
                    "۲) بالا سمت راست روی ⋮ (سه‌نقطه) بزن.\n" +
                    "۳) «اجازه‌ی تنظیمات محدودشده» (Allow restricted settings) را بزن و با قفل گوشی تأیید کن.\n" +
                    "۴) برگرد به همین برنامه و «انجام دادم» را بزن.\n\n" +
                    "اگر ⋮ نبود یا خاکستری بود: «انجام دادم» را بزن، در قدم بعد یک بار سعی کن سرویس را روشن کنی تا خطا بدهد، بعد دوباره برگرد و این کار را تکرار کن.",
                progress = progress,
                primary = "باز کردن اطلاعات برنامه",
                onPrimary = { Permissions.openAppInfo(ctx) },
                secondary = "انجام دادم، ادامه",
                onSecondary = { restrictedAck = true },
            )
            "a11y" -> StepScreen(
                title = if (needRestricted) "قدم ۲: روشن کردن سرویس قفل" else "قدم ۱: روشن کردن سرویس قفل",
                body = "این مهم‌ترین دسترسی است؛ بدون آن قفل کار نمی‌کند.\n" +
                    "۱) دکمه‌ی پایین را بزن.\n" +
                    "۲) در لیست، «پایش مصرف» را پیدا کن (معمولاً زیر «برنامه‌های دانلودشده / Installed apps»).\n" +
                    "۳) روشنش کن و «اجازه / OK» را بزن.\n" +
                    "۴) با دکمه‌ی برگشت به همین برنامه برگرد؛ خودش می‌فهمد و می‌رود قدم بعد.",
                progress = progress,
                primary = "باز کردن تنظیمات دسترسی‌پذیری",
                onPrimary = { Permissions.openAccessibility(ctx) },
                tertiary = if (needRestricted) "خطای «تنظیمات محدودشده» آمد؟ اینجا بزن" else null,
                onTertiary = { restrictedAck = false },
            )
            "usage" -> StepScreen(
                title = "آمار مصرف (توصیه می‌شود)",
                body = "برای اندازه‌گیری دقیق‌تر زمان، اگر سرویس مدتی بسته شد.\n" +
                    "دکمه‌ی پایین را بزن، «پایش مصرف» را روشن کن و برگرد. اگر نمی‌خواهی، رد کن؛ قفل باز هم کار می‌کند.",
                progress = progress,
                primary = "باز کردن تنظیمات",
                onPrimary = { Permissions.openUsageAccess(ctx) },
                secondary = "رد کردن",
                onSecondary = { skipped = skipped + "usage" },
            )
            "battery" -> StepScreen(
                title = "بدون محدودیت باتری (توصیه می‌شود)",
                body = "تا گوشی سرویس قفل را برای صرفه‌جویی باتری نکشد.\nدکمه‌ی پایین را بزن و «اجازه / Allow» را انتخاب کن.",
                progress = progress,
                primary = "فعال‌سازی",
                onPrimary = { Permissions.openBattery(ctx) },
                secondary = "رد کردن",
                onSecondary = { skipped = skipped + "battery" },
            )
            "admin" -> StepScreen(
                title = "جلوگیری از حذف برنامه (توصیه می‌شود)",
                body = "با این گزینه، وقتی برنامه‌ای قفل است نمی‌شود پایش مصرف را به‌راحتی حذف کرد.\nدکمه‌ی پایین را بزن و «فعال‌سازی» را بزن.",
                progress = progress,
                primary = "فعال‌سازی",
                onPrimary = { Permissions.openAdmin(ctx) },
                secondary = "رد کردن",
                onSecondary = { skipped = skipped + "admin" },
            )
            else -> Text("✅ همه چیز آماده است")
        }
    }
}

@Composable
private fun HomeContent(status: Permissions.Status, onOpenWizard: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val limits by LimitStore.limits.collectAsState()
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
            item {
                if (!status.a11y) {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("⚠️ سرویس قفل روشن نیست؛ قفل‌ها کار نمی‌کنند.", fontWeight = FontWeight.Bold)
                            Button(onClick = onOpenWizard, modifier = Modifier.padding(top = 8.dp)) { Text("راه‌اندازی") }
                        }
                    }
                } else if (!status.all) {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("چند دسترسی توصیه‌شده هنوز فعال نیست.", Modifier.weight(1f), fontSize = 13.sp)
                            TextButton(onClick = onOpenWizard) { Text("ادامه‌ی راه‌اندازی") }
                        }
                    }
                }
            }
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
