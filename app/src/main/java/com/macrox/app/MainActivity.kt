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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val Panel2 = Color(0xFF171717)
private val Red = Color(0xFFE11D2E)
private val RedDark = Color(0xFF5E1119)
private val Muted = Color(0xFF929292)
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

    override fun onResume() {
        super.onResume()
    }
}

@Composable
private fun MacroXApp(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    var macros by remember { mutableStateOf(MacroStore.getMacros(activity)) }
    var history by remember { mutableStateOf(MacroStore.getHistory(activity)) }
    var selectedId by remember {
        mutableLongStateOf(
            activity.getSharedPreferences(MacroPrefs.PREFS, 0)
                .getLong(MacroPrefs.SELECTED_MACRO, -1L)
        )
    }
    var enabled by remember { mutableStateOf(false) }
    var accessibility by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf(false) }

    fun refresh() {
        macros = MacroStore.getMacros(activity)
        history = MacroStore.getHistory(activity)
        selectedId = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
            .getLong(MacroPrefs.SELECTED_MACRO, -1L)
        enabled = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
            .getBoolean(MacroPrefs.ENABLED, false)
        accessibility = isAccessibilityEnabled(activity)
        overlay = Settings.canDrawOverlays(activity)
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(activity.packageName)
        } else true
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(800)
        }
    }

    fun openAccessibility() {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlay() {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
        )
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

    fun canArm(): Boolean = accessibility && overlay && macros.isNotEmpty()

    fun arm() {
        if (!canArm()) return
        activity.getSharedPreferences(MacroPrefs.PREFS, 0).edit()
            .putBoolean(MacroPrefs.ENABLED, true).apply()
        ContextCompat.startForegroundService(activity, Intent(activity, MacroEngineService::class.java))
        enabled = true
    }

    fun disarm() {
        activity.getSharedPreferences(MacroPrefs.PREFS, 0).edit()
            .putBoolean(MacroPrefs.ENABLED, false).apply()
        activity.startService(
            Intent(activity, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_STOP)
        )
        enabled = false
    }

    fun runSelected() {
        if (!canArm()) return
        val id = selectedId
        if (id < 0L) return
        activity.getSharedPreferences(MacroPrefs.PREFS, 0).edit()
            .putBoolean(MacroPrefs.ENABLED, true).apply()
        ContextCompat.startForegroundService(activity, Intent(activity, MacroEngineService::class.java))
        activity.startService(
            Intent(activity, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_RUN_SELECTED)
        )
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
            outline = Color(0xFF343434)
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
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Macro X", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    StatusPill(enabled)
                }

                when (tab) {
                    0 -> HomeScreen(
                        macros = macros,
                        selectedId = selectedId,
                        enabled = enabled,
                        canArm = canArm(),
                        onSelect = {
                            selectedId = it
                            activity.getSharedPreferences(MacroPrefs.PREFS, 0).edit()
                                .putLong(MacroPrefs.SELECTED_MACRO, it).apply()
                        },
                        onRun = ::runSelected,
                        onArm = { if (enabled) disarm() else arm() }
                    )
                    1 -> MacroScreen(
                        activity = activity,
                        macros = macros,
                        maxRepeat = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
                            .getInt(MacroPrefs.MAX_REPEAT, 100).coerceIn(1, 1000),
                        onChanged = ::refresh
                    )
                    2 -> HistoryScreen(history) {
                        MacroStore.clearHistory(activity)
                        refresh()
                    }
                    else -> SettingsScreen(
                        activity = activity,
                        accessibility = accessibility,
                        overlay = overlay,
                        battery = battery,
                        onAccessibility = ::openAccessibility,
                        onOverlay = ::openOverlay,
                        onBattery = ::openBattery,
                        onChanged = ::refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (enabled) Color(0xFF241014) else Panel) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (enabled) Red else Muted, RoundedCornerShape(50)))
            Spacer(Modifier.width(7.dp))
            Text(if (enabled) "ARMED" else "IDLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (enabled) Red else Muted)
        }
    }
}

@Composable
private fun HomeScreen(
    macros: List<Macro>,
    selectedId: Long,
    enabled: Boolean,
    canArm: Boolean,
    onSelect: (Long) -> Unit,
    onRun: () -> Unit,
    onArm: () -> Unit
) {
    val selected = macros.firstOrNull { it.id == selectedId } ?: macros.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Text("CONTROL", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            EmptyCard("No macros saved", "Create a macro before the engine can run.")
        } else {
            MacroPicker(macros, selected, onSelect)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRun,
                enabled = canArm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red, disabledContainerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("RUN MACRO", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onArm,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (enabled) Red else Color.White),
                border = BorderStroke(1.dp, if (enabled) Red else Color(0xFF333333)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (enabled) "DISARM ENGINE" else "ARM ENGINE", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("REQUIREMENTS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        RequirementRow("Accessibility service", isAccessibilityEnabled(androidx.compose.ui.platform.LocalContext.current))
        RequirementRow("Floating controls", Settings.canDrawOverlays(androidx.compose.ui.platform.LocalContext.current))
        if (!canArm) {
            Text(
                "Enable the missing requirements in Settings. Macro X does not report them as ready until Android confirms them.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun MacroPicker(macros: List<Macro>, selected: Macro, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Panel
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SELECTED MACRO", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    Text(selected.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("${selected.steps.size} steps • ${selected.repeatCount} repeat(s)", color = Muted, fontSize = 11.sp)
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
private fun RequirementRow(label: String, granted: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null,
            tint = if (granted) Green else Red,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(if (granted) "Enabled" else "Missing", color = if (granted) Green else Muted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = Panel) {
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
                Text("MACROS", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Create and edit real saved sequences.", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Default.AddCircle, "Create macro", tint = Red)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (macros.isEmpty()) {
            EmptyCard("No saved macros", "Your macro list is empty. Create one to begin.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                itemsIndexed(macros, key = { _, m -> m.id }) { _, macro ->
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = Panel) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(macro.name, fontWeight = FontWeight.SemiBold)
                                Text("${macro.steps.size} steps • repeat ${macro.repeatCount}", color = Muted, fontSize = 11.sp)
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
        MacroEditorDialog(activity, null, maxRepeat, {
            creating = false
            onChanged()
        })
    }
    editing?.let { macro ->
        MacroEditorDialog(activity, macro, maxRepeat, {
            editing = null
            onChanged()
        })
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steps", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { stepDialog = -1 }) { Text("ADD STEP", color = Red) }
                }
                if (steps.isEmpty()) {
                    Text("No steps yet.", color = Muted, fontSize = 12.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(steps) { index, step ->
                            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(9.dp), color = Panel2) {
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
    ActionType.TAP -> "Screen tap"
    ActionType.SWIPE -> "Swipe gesture"
    ActionType.DELAY -> "Delay"
}

private fun stepDetail(step: MacroStep) = when (step.type) {
    ActionType.TAP -> "(${step.x1}, ${step.y1}) • +${step.delayAfterMs} ms"
    ActionType.SWIPE -> "(${step.x1},${step.y1}) → (${step.x2},${step.y2}) • +${step.delayAfterMs} ms"
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
                            label = { Text(option.name.replace('_', ' ')) },
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
                    ActionType.DELAY -> CoordinateField("Delay duration (ms)", duration) { duration = it }
                }
                if (type != ActionType.DELAY) CoordinateField("Delay after (ms)", after) { after = it }
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
                        val d = intValue(duration, "Delay")
                        if (d == null || d == 0) { error = "Delay must be greater than 0."; return@Button }
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
                Text("HISTORY", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Only completed and failed executions are stored.", color = Muted, fontSize = 12.sp)
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("CLEAR", color = Red) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            EmptyCard("No run history", "Nothing has been executed yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(history, key = { _, h -> h.id }) { _, item ->
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = Panel) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.macroName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(item.status, color = if (item.status == "COMPLETED") Green else Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.startedAt)), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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
    overlay: Boolean,
    battery: Boolean,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
    onChanged: () -> Unit
) {
    val prefs = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
    var trigger by remember { mutableStateOf(prefs.getString(MacroPrefs.TRIGGER, "VOLUME_UP") ?: "VOLUME_UP") }
    var speed by remember { mutableFloatStateOf(prefs.getFloat(MacroPrefs.SPEED, 1f)) }
    var maxRepeat by remember { mutableIntStateOf(prefs.getInt(MacroPrefs.MAX_REPEAT, 100).coerceIn(1, 1000)) }

    Column(Modifier.fillMaxSize()) {
        Text("SETTINGS", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Android permissions and execution controls.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(16.dp))
        Text("ANDROID STATUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        PermissionRow("Accessibility service", accessibility, onAccessibility)
        PermissionRow("Floating controls", overlay, onOverlay)
        PermissionRow("Battery optimization exemption", battery, if (!battery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) onBattery else null)
        Spacer(Modifier.height(14.dp))
        Text("VOLUME TRIGGER", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(trigger == "VOLUME_UP", { trigger = "VOLUME_UP"; prefs.edit().putString(MacroPrefs.TRIGGER, trigger).apply() }, colors = RadioButtonDefaults.colors(selectedColor = Red))
            Text("Volume Up")
            Spacer(Modifier.width(18.dp))
            RadioButton(trigger == "VOLUME_DOWN", { trigger = "VOLUME_DOWN"; prefs.edit().putString(MacroPrefs.TRIGGER, trigger).apply() }, colors = RadioButtonDefaults.colors(selectedColor = Red))
            Text("Volume Down")
        }
        Spacer(Modifier.height(10.dp))
        Text("EXECUTION SPEED", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Text("${"%.2f".format(speed)}×", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = speed, onValueChange = {
                speed = it
                prefs.edit().putFloat(MacroPrefs.SPEED, it).apply()
            }, valueRange = 0.25f..4f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
        )
        Text("0.25× is slower; 4× is faster.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Text("MAX REPEAT LIMIT", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text("Macro repeat counts cannot exceed this limit.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (granted) Green else Red)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            Text(if (granted) "Confirmed by Android" else "Not enabled", color = if (granted) Green else Muted, fontSize = 10.sp)
        }
        if (!granted && action != null) TextButton(onClick = action) { Text("OPEN", color = Red) }
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
