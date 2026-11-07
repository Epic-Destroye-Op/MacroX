package com.macrox.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val Panel2 = Color(0xFF181818)
private val Red = Color(0xFFE11D2E)
private val RedDark = Color(0xFF4A0F16)
private val Muted = Color(0xFF8A8A8A)
private val Green = Color(0xFF45D483)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MacroXApp(this) }
    }
}

@Composable
private fun MacroXApp(activity: MainActivity) {
    val prefs = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
    var tab by remember { mutableIntStateOf(0) }
    var macros by remember { mutableStateOf(MacroStore.getMacros(activity)) }
    var history by remember { mutableStateOf(MacroStore.getHistory(activity)) }
    var selectedId by remember { mutableLongStateOf(prefs.getLong(MacroPrefs.SELECTED_MACRO, -1L)) }
    var volumeOn by remember { mutableStateOf(prefs.getBoolean(MacroPrefs.ENABLED, false)) }
    var running by remember { mutableStateOf(false) }
    var accessibility by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf(false) }

    fun refresh() {
        macros = MacroStore.getMacros(activity)
        history = MacroStore.getHistory(activity)
        selectedId = prefs.getLong(MacroPrefs.SELECTED_MACRO, -1L)
        volumeOn = prefs.getBoolean(MacroPrefs.ENABLED, false)
        running = MacroEngineService.isRunning
        accessibility = isAccessibilityEnabled(activity)
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(activity.packageName)
        } else true
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(500)
        }
    }

    val selected = macros.firstOrNull { it.id == selectedId } ?: macros.firstOrNull()
    val ready = accessibility && selected != null

    fun togglePower() {
        if (running) {
            activity.startService(
                Intent(activity, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_EMERGENCY_STOP)
            )
        } else {
            val target = selected ?: return
            if (!ready) return
            prefs.edit().putLong(MacroPrefs.SELECTED_MACRO, target.id).apply()
            ContextCompat.startForegroundService(
                activity,
                Intent(activity, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_RUN_SELECTED)
            )
        }
        refresh()
    }

    fun openAccessibility() {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red,
            secondary = Red,
            tertiary = Red,
            background = Bg,
            surface = Panel,
            surfaceVariant = Panel2,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            outline = Color(0xFF2E2E2E)
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    val labels = listOf("Home", "Macros", "History", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Tune, Icons.Default.History, Icons.Default.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Red,
                                selectedTextColor = Red,
                                indicatorColor = RedDark,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Macro X", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    StatusPill(running)
                }

                when (tab) {
                    0 -> HomeScreen(
                        selected = selected,
                        running = running,
                        ready = ready,
                        accessibility = accessibility,
                        onPower = ::togglePower,
                        onOpenAccessibility = ::openAccessibility,
                        onPick = { id ->
                            selectedId = id
                            prefs.edit().putLong(MacroPrefs.SELECTED_MACRO, id).apply()
                        },
                        macros = macros
                    )
                    1 -> MacroScreen(
                        activity = activity,
                        macros = macros,
                        maxRepeat = prefs.getInt(MacroPrefs.MAX_REPEAT, 100).coerceIn(1, 1000),
                        onChanged = ::refresh
                    )
                    2 -> HistoryScreen(history) {
                        MacroStore.clearHistory(activity)
                        refresh()
                    }
                    else -> SettingsScreen(
                        activity = activity,
                        accessibility = accessibility,
                        battery = battery,
                        volumeOn = volumeOn,
                        onAccessibility = ::openAccessibility,
                        onBattery = ::openBattery,
                        onVolumeChange = { on ->
                            volumeOn = on
                            prefs.edit().putBoolean(MacroPrefs.ENABLED, on).apply()
                        },
                        onChanged = ::refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Panel) {
        Column(Modifier.padding(16.dp), content = {
            Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            content()
        })
    }
}

@Composable
private fun StatusPill(running: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (running) RedDark else Panel) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (running) Red else Muted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(if (running) "RUNNING" else "IDLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (running) Red else Muted)
        }
    }
}

@Composable
private fun HomeScreen(
    selected: Macro?,
    running: Boolean,
    ready: Boolean,
    accessibility: Boolean,
    macros: List<Macro>,
    onPower: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPick: (Long) -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (macros.isEmpty()) {
            EmptyCard("No macros saved", "Go to the Macros tab and create one first.")
        } else {
            MacroPicker(macros, selected, onPick)
        }

        Spacer(Modifier.weight(1f))

        PowerButton(running = running, enabled = ready || running, onClick = onPower)

        Spacer(Modifier.height(18.dp))

        val hint = when {
            !accessibility -> "Turn on Accessibility to start macros"
            selected == null -> "Create a macro to get started"
            running -> "Running. Tap to stop"
            else -> "Tap to run ${selected?.name ?: "macro"}"
        }
        Text(
            hint,
            color = if (!accessibility) Red else Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        if (!accessibility) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenAccessibility,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Red)
            ) {
                Text("OPEN ACCESSIBILITY", color = Red, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PowerButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ring = when {
        running -> Red
        enabled -> Color(0xFF3A3A3A)
        else -> Color(0xFF262626)
    }
    val fill = if (running) RedDark else Color(0xFF121212)
    val icon = when {
        running -> Red
        enabled -> Color.White
        else -> Muted
    }
    Box(
        modifier = Modifier
            .size(210.dp)
            .clip(CircleShape)
            .background(fill)
            .border(4.dp, ring, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PowerSettingsNew,
            contentDescription = if (running) "Stop macro" else "Start macro",
            tint = icon,
            modifier = Modifier.size(84.dp)
        )
    }
}

@Composable
private fun MacroPicker(macros: List<Macro>, selected: Macro?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = Panel
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SELECTED MACRO", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    Text(selected?.name ?: "None", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (selected != null) {
                        Text(
                            "${selected.steps.size} steps • repeat ${selected.repeatCount}",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(Icons.Default.ExpandMore, null, tint = Muted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            macros.forEach { macro ->
                DropdownMenuItem(
                    text = { Text(macro.name) },
                    onClick = { onSelect(macro.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Panel) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MacroScreen(activity: Context, macros: List<Macro>, maxRepeat: Int, onChanged: () -> Unit) {
    var editing by remember { mutableStateOf<Macro?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Macros", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("${macros.size} saved", color = Muted, fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { creating = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedDark, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
        }
        Spacer(Modifier.height(14.dp))
        if (macros.isEmpty()) {
            EmptyCard("No saved macros", "Tap New to build your first sequence of taps, swipes and waits.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(macros, key = { _, m -> m.id }) { _, macro ->
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Panel) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(macro.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "${macro.steps.size} steps • repeat ${macro.repeatCount}x",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { editing = macro }) {
                                Icon(Icons.Default.Edit, "Edit", tint = Muted)
                            }
                            IconButton(onClick = {
                                MacroStore.saveMacros(activity, macros.filterNot { it.id == macro.id })
                                onChanged()
                            }) {
                                Icon(Icons.Default.DeleteOutline, "Delete", tint = Red)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        MacroEditorDialog(activity, null, maxRepeat) {
            creating = false
            onChanged()
        }
    }
    editing?.let { macro ->
        MacroEditorDialog(activity, macro, maxRepeat) {
            editing = null
            onChanged()
        }
    }
}

@Composable
private fun MacroEditorDialog(
    context: Context,
    original: Macro?,
    maxRepeat: Int,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var repeat by remember { mutableIntStateOf(original?.repeatCount ?: 1) }
    val steps = remember { mutableStateListOf<MacroStep>().apply { addAll(original?.steps ?: emptyList()) } }
    var stepDialog by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (original == null) "New Macro" else "Edit Macro") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Macro name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat count", modifier = Modifier.weight(1f))
                    IconButton(onClick = { repeat = (repeat - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, "Decrease") }
                    Text("$repeat", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { repeat = (repeat + 1).coerceAtMost(maxRepeat) }) { Icon(Icons.Default.Add, "Increase") }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steps (${steps.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { stepDialog = -1 }) { Text("ADD STEP", color = Red) }
                }
                if (steps.isEmpty()) {
                    Text("No steps yet.", color = Muted, fontSize = 12.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(steps) { index, step ->
                            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = Panel2) {
                                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stepLabel(step), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(stepDetail(step), color = Muted, fontSize = 10.sp)
                                    }
                                    IconButton(enabled = index > 0, onClick = {
                                        val tmp = steps[index - 1]; steps[index - 1] = steps[index]; steps[index] = tmp
                                    }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                    IconButton(enabled = index < steps.lastIndex, onClick = {
                                        val tmp = steps[index + 1]; steps[index + 1] = steps[index]; steps[index] = tmp
                                    }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                    IconButton(onClick = { stepDialog = index }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { steps.removeAt(index) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Red) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty() && steps.isNotEmpty()) {
                        val current = MacroStore.getMacros(context).toMutableList()
                        val macro = Macro(original?.id ?: System.currentTimeMillis(), cleanName, steps.toList(), repeat)
                        val idx = current.indexOfFirst { it.id == macro.id }
                        if (idx >= 0) current[idx] = macro else current.add(macro)
                        MacroStore.saveMacros(context, current)
                        if (original == null) {
                            context.getSharedPreferences(MacroPrefs.PREFS, 0).edit()
                                .putLong(MacroPrefs.SELECTED_MACRO, macro.id).apply()
                        }
                        onDone()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("CANCEL", color = Muted) } },
        containerColor = Panel
    )

    stepDialog?.let { index ->
        StepEditorDialog(
            initial = if (index >= 0) steps[index] else null,
            onCancel = { stepDialog = null },
            onSave = { step ->
                if (index >= 0) steps[index] = step else steps.add(step)
                stepDialog = null
            }
        )
    }
}

private fun stepLabel(step: MacroStep) = when (step.type) {
    ActionType.TAP -> "Tap"
    ActionType.SWIPE -> "Swipe"
    ActionType.DELAY -> "Wait"
}

private fun stepDetail(step: MacroStep) = when (step.type) {
    ActionType.TAP -> "x ${step.x1}, y ${step.y1}  •  then wait ${step.delayAfterMs} ms"
    ActionType.SWIPE -> "(${step.x1}, ${step.y1}) to (${step.x2}, ${step.y2})  •  ${step.durationMs} ms  •  then wait ${step.delayAfterMs} ms"
    ActionType.DELAY -> "${step.durationMs} ms"
}

@Composable
private fun StepEditorDialog(
    initial: MacroStep?,
    onCancel: () -> Unit,
    onSave: (MacroStep) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: ActionType.TAP) }
    var x1 by remember { mutableStateOf(initial?.x1?.toString() ?: "") }
    var y1 by remember { mutableStateOf(initial?.y1?.toString() ?: "") }
    var x2 by remember { mutableStateOf(initial?.x2?.toString() ?: "") }
    var y2 by remember { mutableStateOf(initial?.y2?.toString() ?: "") }
    var duration by remember { mutableStateOf(initial?.durationMs?.toString() ?: "250") }
    var after by remember { mutableStateOf(initial?.delayAfterMs?.toString() ?: "0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial == null) "Add Step" else "Edit Step") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stepLabel(MacroStep(option))) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RedDark,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (type) {
                    ActionType.TAP -> {
                        CoordinateField("X", x1) { x1 = it }
                        CoordinateField("Y", y1) { y1 = it }
                    }
                    ActionType.SWIPE -> {
                        CoordinateField("Start X", x1) { x1 = it }
                        CoordinateField("Start Y", y1) { y1 = it }
                        CoordinateField("End X", x2) { x2 = it }
                        CoordinateField("End Y", y2) { y2 = it }
                        CoordinateField("Swipe duration (ms)", duration) { duration = it }
                    }
                    ActionType.DELAY -> CoordinateField("Wait duration (ms)", duration) { duration = it }
                }
                if (type != ActionType.DELAY) CoordinateField("Wait after (ms)", after) { after = it }
                error?.let { Text(it, color = Red, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                fun intValue(v: String, label: String): Int? {
                    val n = v.trim().toIntOrNull()
                    if (n == null || n < 0) {
                        error = "$label must be a non-negative number."
                        return null
                    }
                    return n
                }
                val a = when (type) {
                    ActionType.TAP -> {
                        val ax = intValue(x1, "X"); val ay = intValue(y1, "Y")
                        if (ax == null || ay == null) return@Button
                        MacroStep(type, ax, ay, delayAfterMs = intValue(after, "Delay")?.toLong() ?: return@Button)
                    }
                    ActionType.SWIPE -> {
                        val ax = intValue(x1, "Start X"); val ay = intValue(y1, "Start Y")
                        val bx = intValue(x2, "End X"); val by = intValue(y2, "End Y")
                        val d = intValue(duration, "Duration")
                        val wait = intValue(after, "Delay")
                        if (ax == null || ay == null || bx == null || by == null || d == null || wait == null) return@Button
                        if (d == 0) { error = "Swipe duration must be greater than 0."; return@Button }
                        MacroStep(type, ax, ay, bx, by, d.toLong(), wait.toLong())
                    }
                    ActionType.DELAY -> {
                        val d = intValue(duration, "Wait")
                        if (d == null || d == 0) { error = "Wait must be greater than 0."; return@Button }
                        MacroStep(type, durationMs = d.toLong())
                    }
                }
                onSave(a)
            }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL", color = Muted) } },
        containerColor = Panel
    )
}

@Composable
private fun CoordinateField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

@Composable
private fun HistoryScreen(history: List<HistoryEntry>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("History", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Completed and failed runs.", color = Muted, fontSize = 12.sp)
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("CLEAR", color = Red) }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (history.isEmpty()) {
            EmptyCard("No run history", "Nothing has been executed yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(history, key = { _, h -> h.id }) { _, item ->
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Panel) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.macroName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    item.status,
                                    color = if (item.status == "COMPLETED") Green else Red,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.startedAt)),
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text("Duration ${item.durationMs} ms", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    activity: Context,
    accessibility: Boolean,
    battery: Boolean,
    volumeOn: Boolean,
    onAccessibility: () -> Unit,
    onBattery: () -> Unit,
    onVolumeChange: (Boolean) -> Unit,
    onChanged: () -> Unit
) {
    val prefs = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
    var trigger by remember { mutableStateOf(prefs.getString(MacroPrefs.TRIGGER, "VOLUME_UP") ?: "VOLUME_UP") }
    var speed by remember { mutableFloatStateOf(prefs.getFloat(MacroPrefs.SPEED, 1f)) }
    var maxRepeat by remember { mutableIntStateOf(prefs.getInt(MacroPrefs.MAX_REPEAT, 100).coerceIn(1, 1000)) }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Settings", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Permissions and playback options.", color = Muted, fontSize = 12.sp)
        }

        SectionCard("PERMISSIONS") {
            PermissionRow("Accessibility service", "Needed to perform taps and swipes", accessibility, onAccessibility)
            PermissionRow(
                "Unrestricted battery",
                "Keeps Macro X from being stopped in the background",
                battery,
                if (!battery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) onBattery else null
            )
        }

        SectionCard("VOLUME KEY TRIGGER") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Run macro with a volume key", fontSize = 14.sp)
                    Text("Works while Accessibility is on", color = Muted, fontSize = 11.sp)
                }
                Switch(
                    checked = volumeOn,
                    onCheckedChange = onVolumeChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Red)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = trigger == "VOLUME_UP",
                    onClick = { trigger = "VOLUME_UP"; prefs.edit().putString(MacroPrefs.TRIGGER, trigger).apply() },
                    colors = RadioButtonDefaults.colors(selectedColor = Red)
                )
                Text("Volume up", fontSize = 13.sp)
                Spacer(Modifier.width(18.dp))
                RadioButton(
                    selected = trigger == "VOLUME_DOWN",
                    onClick = { trigger = "VOLUME_DOWN"; prefs.edit().putString(MacroPrefs.TRIGGER, trigger).apply() },
                    colors = RadioButtonDefaults.colors(selectedColor = Red)
                )
                Text("Volume down", fontSize = 13.sp)
            }
        }

        SectionCard("PLAYBACK") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${"%.2f".format(speed)}×", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    prefs.edit().putFloat(MacroPrefs.SPEED, it).apply()
                },
                valueRange = 0.25f..4f,
                colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
            )
            Text("0.25× is slower, 4× is faster.", color = Muted, fontSize = 11.sp)

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Max repeat limit", fontSize = 14.sp)
                    Text("Caps the repeat count on any macro", color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    maxRepeat = (maxRepeat - 1).coerceAtLeast(1)
                    prefs.edit().putInt(MacroPrefs.MAX_REPEAT, maxRepeat).apply()
                    onChanged()
                }) { Icon(Icons.Default.Remove, "Decrease") }
                Text("$maxRepeat", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    maxRepeat = (maxRepeat + 1).coerceAtMost(1000)
                    prefs.edit().putInt(MacroPrefs.MAX_REPEAT, maxRepeat).apply()
                    onChanged()
                }) { Icon(Icons.Default.Add, "Increase") }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, detail: String, granted: Boolean, action: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null,
            tint = if (granted) Green else Red
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        if (!granted && action != null) TextButton(onClick = action) { Text("OPEN", color = Red) }
        else if (granted) Text("On", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = "${context.packageName}/${MacroAccessibilityService::class.java.name}"
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
