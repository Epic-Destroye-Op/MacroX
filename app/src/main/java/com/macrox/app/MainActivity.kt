package com.macrox.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val Panel2 = Color(0xFF181818)
private val Red = Color(0xFFE11D2E)
private val RedDark = Color(0xFF4A0F16)
private val Orange = Color(0xFFFF7A1A)
private val Muted = Color(0xFF8A8A8A)
private val Green = Color(0xFF45D483)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
    var runningName by remember { mutableStateOf<String?>(null) }
    var accessibility by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(ShizukuState.NOT_RUNNING) }
    var keyboardSelected by remember { mutableStateOf(false) }
    var keyOutput by remember { mutableStateOf(MacroPrefs.OUTPUT_SHIZUKU) }

    fun refresh() {
        macros = MacroStore.getMacros(activity)
        history = MacroStore.getHistory(activity)
        selectedId = prefs.getLong(MacroPrefs.SELECTED_MACRO, -1L)
        volumeOn = prefs.getBoolean(MacroPrefs.ENABLED, false)
        running = MacroEngineService.isRunning
        runningName = MacroEngineService.currentMacroName
        accessibility = isAccessibilityEnabled(activity)
        shizuku = KeyInjector.shizukuState()
        keyboardSelected = MacroKeyboardService.isSelected(activity)
        keyOutput = prefs.getString(MacroPrefs.KEY_OUTPUT, MacroPrefs.OUTPUT_SHIZUKU) ?: MacroPrefs.OUTPUT_SHIZUKU
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
            Toast.makeText(activity, "Macro stopped", Toast.LENGTH_SHORT).show()
        } else {
            val target = selected
            when {
                !accessibility -> Toast.makeText(
                    activity, "Turn on Accessibility in Settings first", Toast.LENGTH_LONG
                ).show()
                target == null -> Toast.makeText(activity, "Create a macro first", Toast.LENGTH_SHORT).show()
                else -> {
                    prefs.edit().putLong(MacroPrefs.SELECTED_MACRO, target.id).apply()
                    ContextCompat.startForegroundService(
                        activity,
                        Intent(activity, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_RUN_SELECTED)
                    )
                    Toast.makeText(activity, "Started ${target.name}", Toast.LENGTH_SHORT).show()
                }
            }
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

    fun openKeyboardSettings() {
        activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    fun setKeyOutput(value: String) {
        keyOutput = value
        prefs.edit().putString(MacroPrefs.KEY_OUTPUT, value).apply()
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
                TopBar(running, runningName)

                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 8 })
                    },
                    label = "tabs"
                ) { current ->
                    when (current) {
                        0 -> HomeScreen(
                            selected = selected,
                            running = running,
                            ready = ready,
                            accessibility = accessibility,
                            macros = macros,
                            onPower = ::togglePower,
                            onOpenAccessibility = ::openAccessibility,
                            onPick = { id ->
                                selectedId = id
                                prefs.edit().putLong(MacroPrefs.SELECTED_MACRO, id).apply()
                            }
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
                            shizuku = shizuku,
                            keyboardSelected = keyboardSelected,
                            keyOutput = keyOutput,
                            volumeOn = volumeOn,
                            onAccessibility = ::openAccessibility,
                            onBattery = ::openBattery,
                            onShizuku = { KeyInjector.requestShizukuPermission() },
                            onKeyboard = ::openKeyboardSettings,
                            onKeyOutput = ::setKeyOutput,
                            onVolumeChange = { on ->
                                volumeOn = on
                                prefs.edit().putBoolean(MacroPrefs.ENABLED, on).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(running: Boolean, runningName: String?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                append("Macro ")
                withStyle(SpanStyle(color = Red)) { append("X") }
            },
            fontSize = 27.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
        StatusPill(running, runningName)
    }
}

@Composable
private fun StatusPill(running: Boolean, runningName: String?) {
    val label = if (running) "RUNNING" + (runningName?.let { " · $it" } ?: "") else "IDLE"
    Surface(shape = RoundedCornerShape(20.dp), color = if (running) RedDark else Panel) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp).widthIn(max = 200.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(if (running) Red else Muted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (running) Red else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
private fun EmptyCard(title: String, detail: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Panel) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (macros.isEmpty()) {
            EmptyCard("No macros saved", "Open the Macros tab and create one first.")
        } else {
            MacroPicker(macros, selected, onPick)
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            FlameRing(active = running, modifier = Modifier.fillMaxSize())
            PowerButton(running = running, ready = ready, onClick = onPower)
        }

        Spacer(Modifier.height(16.dp))

        val hint = when {
            !accessibility -> "Turn on Accessibility to start macros"
            selected == null -> "Create a macro to get started"
            running -> "Running ${selected.name}. Tap to stop"
            else -> "Tap to run ${selected.name}"
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
private fun PowerButton(running: Boolean, ready: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (running) 1.04f else 1f, label = "powerScale")
    val ring = when {
        running -> Red
        ready -> Color(0xFF3A3A3A)
        else -> Color(0xFF262626)
    }
    val fill = if (running) RedDark else Color(0xFF121212)
    val iconTint = when {
        running -> Red
        ready -> Color.White
        else -> Muted
    }
    Box(
        modifier = Modifier
            .size(210.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(4.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PowerSettingsNew,
            contentDescription = if (running) "Stop macro" else "Start macro",
            tint = iconTint,
            modifier = Modifier.size(84.dp)
        )
    }
}

/** Red and orange flames around the power button. They fade in while a macro runs. */
@Composable
private fun FlameRing(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flames")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val intensity by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(600),
        label = "intensity"
    )
    Canvas(modifier) {
        if (intensity <= 0.01f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.40f
        drawCircle(color = Red.copy(alpha = 0.18f * intensity), radius = base * 1.12f, center = center)
        val count = 26
        for (i in 0 until count) {
            val angle = (2.0 * PI * i / count).toFloat()
            val flicker = (sin(phase * 2f * PI.toFloat() * 2f + i * 1.3f) + 1f) / 2f
            val length = size.minDimension * (0.035f + 0.05f * flicker) * intensity
            val inner = base * 1.03f
            val start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
            val end = Offset(
                center.x + cos(angle) * (inner + length),
                center.y + sin(angle) * (inner + length)
            )
            drawLine(
                color = if (i % 3 == 0) Orange else Red,
                start = start,
                end = end,
                strokeWidth = size.minDimension * 0.03f,
                cap = StrokeCap.Round,
                alpha = intensity * (0.55f + 0.45f * flicker)
            )
        }
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
                        Text(macroSummary(selected), color = Muted, fontSize = 11.sp)
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

private fun macroSummary(macro: Macro) = "${macro.steps.size} steps  •  repeat ${macro.repeatCount}×"

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
            Button(
                onClick = { creating = true },
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
        }
        Spacer(Modifier.height(14.dp))
        if (macros.isEmpty()) {
            EmptyCard("No saved macros", "Tap New to build a sequence of key presses, clicks and waits.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(macros, key = { _, m -> m.id }) { _, macro ->
                    MacroCard(
                        macro = macro,
                        onEdit = { editing = macro },
                        onDelete = {
                            MacroStore.saveMacros(activity, macros.filterNot { it.id == macro.id })
                            onChanged()
                        }
                    )
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
private fun MacroCard(macro: Macro, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Panel) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    macro.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(macroSummary(macro), color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Red) }
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

    fun save() {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            Toast.makeText(context, "Enter a macro name.", Toast.LENGTH_SHORT).show()
            return
        }
        if (steps.isEmpty()) {
            Toast.makeText(context, "Add at least one step.", Toast.LENGTH_SHORT).show()
            return
        }
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

    fun swap(a: Int, b: Int) {
        val t = steps[a]
        steps[a] = steps[b]
        steps[b] = t
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Close") }
                    Text(
                        if (original == null) "New macro" else "Edit macro",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { save() },
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("SAVE", fontWeight = FontWeight.Bold) }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Macro name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat count", modifier = Modifier.weight(1f))
                    IconButton(onClick = { repeat = (repeat - 1).coerceAtLeast(1) }) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text("$repeat", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { repeat = (repeat + 1).coerceAtMost(maxRepeat) }) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Steps (${steps.size})", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                if (steps.isEmpty()) {
                    Text("No steps yet. Tap Add step below.", color = Muted, fontSize = 12.sp)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(steps) { index, step ->
                        StepCard(
                            index = index,
                            step = step,
                            canUp = index > 0,
                            canDown = index < steps.lastIndex,
                            onUp = { swap(index, index - 1) },
                            onDown = { swap(index, index + 1) },
                            onEdit = { stepDialog = index },
                            onDelete = { steps.removeAt(index) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { stepDialog = -1 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ADD STEP", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

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

@Composable
private fun StepCard(
    index: Int,
    step: MacroStep,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = Panel2) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stepLabel(step),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Red) }
            }
            Text(
                stepDetail(step),
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp, end = 8.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onUp, enabled = canUp) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = if (canUp) Muted else Color(0xFF333333))
                }
                IconButton(onClick = onDown, enabled = canDown) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move down", tint = if (canDown) Muted else Color(0xFF333333))
                }
            }
        }
    }
}

private fun stepLabel(step: MacroStep) = when (step.type) {
    ActionType.DELAY -> "Wait"
    ActionType.KEY_PRESS -> "Key press"
    ActionType.MOUSE_LEFT_CLICK -> "Left click"
    ActionType.MOUSE_LEFT_DOWN -> "Hold left button"
    ActionType.MOUSE_LEFT_UP -> "Release left button"
}

private fun stepDetail(step: MacroStep) = when (step.type) {
    ActionType.DELAY -> "${step.durationMs} ms"
    ActionType.KEY_PRESS -> "${MacroKeys.find(step.key)?.label ?: "No key"}  •  pause ${step.delayAfterMs} ms after"
    ActionType.MOUSE_LEFT_CLICK -> "x ${step.x1}, y ${step.y1}  •  hold ${step.durationMs} ms  •  pause ${step.delayAfterMs} ms after"
    ActionType.MOUSE_LEFT_DOWN -> "x ${step.x1}, y ${step.y1}  •  stays down until Release  •  pause ${step.delayAfterMs} ms after"
    ActionType.MOUSE_LEFT_UP -> "lifts the button  •  pause ${step.delayAfterMs} ms after"
}

private enum class EditorKind(val label: String) { KEY("Key"), MOUSE("Mouse"), WAIT("Wait") }

private enum class MouseMode(val label: String) { CLICK("Click"), DOWN("Hold start"), UP("Release") }

private fun ActionType.kind(): EditorKind = when (this) {
    ActionType.KEY_PRESS -> EditorKind.KEY
    ActionType.DELAY -> EditorKind.WAIT
    else -> EditorKind.MOUSE
}

private fun ActionType.mouseMode(): MouseMode = when (this) {
    ActionType.MOUSE_LEFT_DOWN -> MouseMode.DOWN
    ActionType.MOUSE_LEFT_UP -> MouseMode.UP
    else -> MouseMode.CLICK
}

@Composable
private fun StepEditorDialog(
    initial: MacroStep?,
    onCancel: () -> Unit,
    onSave: (MacroStep) -> Unit
) {
    var kind by remember { mutableStateOf(initial?.type?.kind() ?: EditorKind.KEY) }
    var mouseMode by remember { mutableStateOf(initial?.type?.mouseMode() ?: MouseMode.CLICK) }
    var x by remember { mutableStateOf(initial?.x1?.toString() ?: "") }
    var y by remember { mutableStateOf(initial?.y1?.toString() ?: "") }
    var duration by remember {
        mutableStateOf(
            if (initial != null && (initial.type == ActionType.DELAY || initial.type == ActionType.MOUSE_LEFT_CLICK))
                initial.durationMs.toString()
            else "100"
        )
    }
    var after by remember { mutableStateOf(initial?.delayAfterMs?.toString() ?: "0") }
    var selectedKey by remember { mutableStateOf(initial?.key) }
    var error by remember { mutableStateOf<String?>(null) }

    fun whole(value: String, label: String): Int? {
        val n = value.trim().toIntOrNull()
        if (n == null || n < 0) {
            error = "$label must be a whole number, 0 or more."
            return null
        }
        return n
    }

    fun build(): MacroStep? {
        val pause = if (kind == EditorKind.WAIT) 0 else (whole(after, "Pause") ?: return null)
        return when (kind) {
            EditorKind.WAIT -> {
                val ms = whole(duration, "Wait") ?: return null
                if (ms == 0) {
                    error = "Wait must be greater than 0."
                    return null
                }
                MacroStep(ActionType.DELAY, durationMs = ms.toLong())
            }
            EditorKind.KEY -> {
                val key = selectedKey
                if (key == null || MacroKeys.find(key) == null) {
                    error = "Choose a key."
                    return null
                }
                MacroStep(ActionType.KEY_PRESS, delayAfterMs = pause.toLong(), key = key)
            }
            EditorKind.MOUSE -> when (mouseMode) {
                MouseMode.CLICK -> {
                    val ax = whole(x, "X") ?: return null
                    val ay = whole(y, "Y") ?: return null
                    val hold = whole(duration, "Hold") ?: return null
                    if (hold == 0) {
                        error = "Hold must be greater than 0."
                        return null
                    }
                    MacroStep(
                        ActionType.MOUSE_LEFT_CLICK, ax, ay,
                        durationMs = hold.toLong(), delayAfterMs = pause.toLong()
                    )
                }
                MouseMode.DOWN -> {
                    val ax = whole(x, "X") ?: return null
                    val ay = whole(y, "Y") ?: return null
                    MacroStep(ActionType.MOUSE_LEFT_DOWN, ax, ay, delayAfterMs = pause.toLong())
                }
                MouseMode.UP -> MacroStep(ActionType.MOUSE_LEFT_UP, delayAfterMs = pause.toLong())
            }
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp),
            RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (initial == null) "Add step" else "Edit step",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                SectionLabel("ACTION")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option; error = null },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RedDark,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                when (kind) {
                    EditorKind.WAIT -> CoordinateField("Wait (ms)", duration) { duration = it }
                    EditorKind.KEY -> {
                        SectionLabel("KEY")
                        Spacer(Modifier.height(6.dp))
                        KeyPicker(selectedKey) { selectedKey = it; error = null }
                    }
                    EditorKind.MOUSE -> {
                        SectionLabel("MOUSE")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MouseMode.entries.forEach { option ->
                                FilterChip(
                                    selected = mouseMode == option,
                                    onClick = { mouseMode = option; error = null },
                                    label = { Text(option.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RedDark,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (mouseMode != MouseMode.UP) {
                            CoordinateField("X", x) { x = it }
                            CoordinateField("Y", y) { y = it }
                        }
                        if (mouseMode == MouseMode.CLICK) {
                            CoordinateField("Hold (ms)", duration) { duration = it }
                        }
                        if (mouseMode == MouseMode.UP) {
                            Text(
                                "Lifts the left button held by the most recent Hold start step.",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                    }
                }

                if (kind != EditorKind.WAIT) {
                    Spacer(Modifier.height(8.dp))
                    CoordinateField("Pause after (ms)", after) { after = it }
                }

                error?.let {
                    Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("CANCEL", color = Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { build()?.let(onSave) },
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("SAVE", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun KeyPicker(selected: String?, onSelect: (String) -> Unit) {
    var category by remember { mutableStateOf(MacroKeys.find(selected)?.category ?: KeyCategory.LETTERS) }
    var menuOpen by remember { mutableStateOf(false) }
    val keys = MacroKeys.all.filter { it.category == category }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(category.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.ExpandMore, null, tint = Muted)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            KeyCategory.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { category = option; menuOpen = false }
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxWidth().height(250.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(keys, key = { it.id }) { keyDef ->
            val isSelected = selected == keyDef.id
            Surface(
                modifier = Modifier.fillMaxWidth().height(42.dp).clickable { onSelect(keyDef.id) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) RedDark else Panel2,
                border = if (isSelected) BorderStroke(1.dp, Red) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        keyDef.label,
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
    Text(
        "Selected: ${MacroKeys.find(selected)?.label ?: "none"}",
        color = Muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp)
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
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
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
    shizuku: ShizukuState,
    keyboardSelected: Boolean,
    keyOutput: String,
    volumeOn: Boolean,
    onAccessibility: () -> Unit,
    onBattery: () -> Unit,
    onShizuku: () -> Unit,
    onKeyboard: () -> Unit,
    onKeyOutput: (String) -> Unit,
    onVolumeChange: (Boolean) -> Unit
) {
    val prefs = activity.getSharedPreferences(MacroPrefs.PREFS, 0)
    var trigger by remember { mutableStateOf(prefs.getString(MacroPrefs.TRIGGER, "VOLUME_UP") ?: "VOLUME_UP") }
    var speed by remember { mutableFloatStateOf(prefs.getFloat(MacroPrefs.SPEED, 1f)) }
    var maxRepeat by remember { mutableIntStateOf(prefs.getInt(MacroPrefs.MAX_REPEAT, 100).coerceIn(1, 1000)) }

    val shizukuDetail = when (shizuku) {
        ShizukuState.READY -> "Running and granted. Can send keys to any app."
        ShizukuState.NOT_GRANTED -> "Running. Tap OPEN to grant Macro X access."
        ShizukuState.NOT_RUNNING -> "Not running. Optional, start it to send keys without root."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Settings", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Permissions, key output and playback.", color = Muted, fontSize = 12.sp)
        }

        SectionCard("PERMISSIONS") {
            PermissionRow(
                "Accessibility service",
                "Needed for clicks and the volume trigger",
                accessibility,
                if (!accessibility) onAccessibility else null
            )
            PermissionRow(
                "Unrestricted battery",
                "Keeps Macro X running in the background",
                battery,
                if (!battery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) onBattery else null
            )
        }

        SectionCard("KEY OUTPUT") {
            KeyOutputOption(
                title = "Shizuku or root",
                detail = "Sends keys to any app. Needs Shizuku running or root.",
                selected = keyOutput == MacroPrefs.OUTPUT_SHIZUKU,
                onSelect = { onKeyOutput(MacroPrefs.OUTPUT_SHIZUKU) }
            )
            KeyOutputOption(
                title = "Macro X keyboard",
                detail = "Works only in a focused text field. No root or Shizuku.",
                selected = keyOutput == MacroPrefs.OUTPUT_KEYBOARD,
                onSelect = { onKeyOutput(MacroPrefs.OUTPUT_KEYBOARD) }
            )
            Spacer(Modifier.height(6.dp))
            PermissionRow(
                "Shizuku",
                shizukuDetail,
                shizuku == ShizukuState.READY,
                if (shizuku == ShizukuState.NOT_GRANTED) onShizuku else null
            )
            PermissionRow(
                "Macro X keyboard",
                if (keyboardSelected) "Selected as the active keyboard"
                else "Choose Macro X keyboard in input settings",
                keyboardSelected,
                if (!keyboardSelected) onKeyboard else null
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
                }) { Icon(Icons.Default.Remove, "Decrease") }
                Text("$maxRepeat", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    maxRepeat = (maxRepeat + 1).coerceAtMost(1000)
                    prefs.edit().putInt(MacroPrefs.MAX_REPEAT, maxRepeat).apply()
                }) { Icon(Icons.Default.Add, "Increase") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun KeyOutputOption(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = Red)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 11.sp)
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
        if (!granted && action != null) {
            TextButton(onClick = action) { Text("OPEN", color = Red) }
        } else if (granted) {
            Text("On", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
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
