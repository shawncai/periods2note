package com.period.local.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.period.local.PeriodViewModel
import com.period.local.domain.Period
import java.time.LocalDate
import kotlinx.coroutines.delay

val Accent = Color(0xFF1967D2)
val Ink = Color(0xFF1D1D1F)
val Secondary = Color(0xFF62666D)
val Pale = Color(0xFFF5F5F7)
val Track = Color(0xFFE8EAED)

private val AppColors = lightColorScheme(
    primary = Accent, onPrimary = Color.White, primaryContainer = Color(0xFFEAF1FC),
    onPrimaryContainer = Accent, background = Color.White, onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Pale, onSurfaceVariant = Secondary,
    outline = Secondary, outlineVariant = Track, secondaryContainer = Color(0xFFEAF1FC),
)

private class LocalOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
private class LocalCreateDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

data class EditorRequest(val original: Period?, val complete: Boolean = false, val initialStart: LocalDate? = null)

@Composable
fun PeriodApp(model: PeriodViewModel = viewModel()) {
    MaterialTheme(colorScheme = AppColors) {
        val state by model.state.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        val startupError by model.startupError.collectAsStateWithLifecycle()
        val preview by model.importPreview.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var settings by rememberSaveable { mutableStateOf(false) }
        var editorOpen by rememberSaveable { mutableStateOf(false) }
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var completing by rememberSaveable { mutableStateOf(false) }
        var draftStart by rememberSaveable { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refreshToday()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            while (true) { delay(60_000); model.refreshToday() }
        }
        val export = rememberLauncherForActivityResult(LocalCreateDocument()) { uri: Uri? -> uri?.let(model::export) }
        val import = rememberLauncherForActivityResult(LocalOpenDocument()) { uri: Uri? -> uri?.let(model::readImport) }
        fun edit(period: Period?, complete: Boolean = false) {
            editingId = period?.id?.takeIf { it.isNotBlank() }
            draftStart = period?.start?.toString()
            completing = complete
            editorOpen = true
        }
        BackHandler(settings && !editorOpen && preview == null) { settings = false }
        BackHandler(tab != 0 && !settings && !editorOpen && preview == null) { tab = 0 }
        Scaffold(
            containerColor = Color.White,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!settings && state.ready) {
                    Column {
                        HorizontalDivider(color = Track, thickness = 0.5.dp)
                        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                            val labels = listOf("今天", "日历", "趋势")
                            val icons = listOf(Icons.Outlined.Home, Icons.Outlined.CalendarMonth, Icons.Outlined.BarChart)
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index, onClick = { tab = index },
                                    icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(27.dp)) },
                                    label = { Text(label, fontSize = 14.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Accent, selectedTextColor = Accent,
                                        unselectedIconColor = Secondary, unselectedTextColor = Secondary,
                                        indicatorColor = Color.White,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                when {
                    startupError != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(startupError.orEmpty())
                        Button(onClick = model::initialize, enabled = !busy) { Text("重新加载") }
                    }
                    !state.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    settings -> SettingsScreen(
                        state.records.size, busy,
                        onBack = { settings = false },
                        onExport = { export.launch("经期备份-${state.today}.json") },
                        onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    tab == 0 -> TodayScreen(state, busy, { settings = true }, model::endToday, model::startToday)
                    tab == 1 -> CalendarScreen(state, { settings = true }, { edit(it) })
                    else -> TrendsScreen(state, { settings = true })
                }
                if (busy && state.ready) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (editorOpen) {
            val original = state.records.find { it.id == editingId }
            RecordEditor(
                request = EditorRequest(original, completing, draftStart?.let(LocalDate::parse)),
                records = state.records, today = state.today, busy = busy,
                onDismiss = { if (!busy) editorOpen = false },
                onSave = { record -> model.save(record, original) { editorOpen = false } },
            )
        }
        preview?.let { value ->
            ImportDialog(value, busy, model::dismissImport, model::confirmImport)
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
            subtitle?.let { Text(it, fontSize = 15.sp, color = Secondary, modifier = Modifier.padding(top = 5.dp)) }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "设置与备份", tint = Secondary, modifier = Modifier.size(27.dp)) }
    }
}
