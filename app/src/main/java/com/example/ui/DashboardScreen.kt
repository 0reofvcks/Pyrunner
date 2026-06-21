package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PythonScript
import com.example.data.ScriptRun
import com.example.python.PythonManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: ScriptViewModel) {
    val context = LocalContext.current
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val installStatusText by viewModel.installStatusText.collectAsStateWithLifecycle()

    val scripts by viewModel.allScripts.collectAsStateWithLifecycle()
    val runs by viewModel.allRuns.collectAsStateWithLifecycle()
    val installedPackages by viewModel.installedPackages.collectAsStateWithLifecycle()
    val isRefreshingPackages by viewModel.isRefreshingPackages.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var activeEditorScript by remember { mutableStateOf<PythonScript?>(null) }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var activeRunForLogs by remember { mutableStateOf<ScriptRun?>(null) }

    // Check installation on load
    LaunchedEffect(Unit) {
        viewModel.checkPythonStatus(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Console Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "OREO PY",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 1 && installState == PythonManager.InstallState.INSTALLED) {
                FloatingActionButton(
                    onClick = { showNewScriptDialog = true },
                    modifier = Modifier.testTag("create_script_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Script")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Environment banner
            Surface(
                color = when (installState) {
                    PythonManager.InstallState.INSTALLED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    PythonManager.InstallState.NOT_INSTALLED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    PythonManager.InstallState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (installState) {
                            PythonManager.InstallState.INSTALLED -> Icons.Filled.CheckCircle
                            PythonManager.InstallState.NOT_INSTALLED -> Icons.Filled.Info
                            PythonManager.InstallState.ERROR -> Icons.Filled.BugReport
                            else -> Icons.Outlined.CloudDownload
                        },
                        contentDescription = "Status",
                        tint = when (installState) {
                            PythonManager.InstallState.INSTALLED -> MaterialTheme.colorScheme.primary
                            PythonManager.InstallState.NOT_INSTALLED -> MaterialTheme.colorScheme.outline
                            PythonManager.InstallState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (installState == PythonManager.InstallState.INSTALLED) "Python Environment Active" else "Python Setup Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = installStatusText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (installState != PythonManager.InstallState.INSTALLED &&
                        installState != PythonManager.InstallState.DOWNLOADING &&
                        installState != PythonManager.InstallState.EXTRACTING
                    ) {
                        Button(
                            onClick = { viewModel.startInstallation(context) },
                            modifier = Modifier.testTag("install_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Install", fontSize = 12.sp)
                        }
                    } else if (installState == PythonManager.InstallState.DOWNLOADING || 
                               installState == PythonManager.InstallState.EXTRACTING) {
                        CircularProgressIndicator(
                            progress = { installProgress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Tab navigation
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Console") },
                    text = { Text("Panel", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Scripts") },
                    text = { Text("Scripts", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Pip Store") },
                    text = { Text("Pip", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Runs") },
                    text = { Text("History", fontSize = 12.sp) }
                )
            }

            // Tabs Content
            Box(
                modifier = Modifier
                    .fillPanel()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                when (selectedTab) {
                    0 -> ControlPanelTab(
                        installState = installState,
                        installProgress = installProgress,
                        installStatusText = installStatusText,
                        runs = runs,
                        onInstall = { viewModel.startInstallation(context) },
                        onLogsRequest = { activeRunForLogs = it },
                        onKillRequest = { viewModel.terminateRun(context, it) }
                    )
                    1 -> ScriptsWorkspaceTab(
                        installState = installState,
                        scripts = scripts,
                        onRun = { viewModel.launchScript(context, it) },
                        onEdit = { activeEditorScript = it },
                        onDelete = { viewModel.deleteScript(it) }
                    )
                    2 -> PipManagerTab(
                        installState = installState,
                        installedPackages = installedPackages,
                        isRefreshing = isRefreshingPackages,
                        onInstallPackage = { viewModel.launchPipInstall(context, it) },
                        onRefresh = { viewModel.refreshInstalledPackages(context) }
                    )
                    3 -> RunHistoryTab(
                        runs = runs,
                        onLogsRequest = { activeRunForLogs = it },
                        onKillRequest = { viewModel.terminateRun(context, it) },
                        onDeleteHistoryItem = { viewModel.deleteRunHistory(it) },
                        onClearAll = { viewModel.clearAllHistory() }
                    )
                }
            }
        }
    }

    // Modal Sheet: Code Editor
    activeEditorScript?.let { script ->
        ScriptEditorDialog(
            script = script,
            onDismiss = { activeEditorScript = null },
            onSave = { name, content ->
                viewModel.saveScript(name, content, script.id)
                activeEditorScript = null
            },
            onRun = { name, content ->
                viewModel.saveScript(name, content, script.id)
                viewModel.launchScript(context, script.copy(name = name, content = content))
                activeEditorScript = null
                selectedTab = 3 // Jump to history tab
            }
        )
    }

    // Modal Sheet: Create New Script
    if (showNewScriptDialog) {
        ScriptEditorDialog(
            script = PythonScript(name = "", content = ""),
            onDismiss = { showNewScriptDialog = false },
            onSave = { name, content ->
                viewModel.saveScript(name, content)
                showNewScriptDialog = false
            },
            onRun = { name, content ->
                val id = viewModel.allScripts.value.size + 1
                val script = PythonScript(id = id, name = name, content = content)
                viewModel.saveScript(name, content)
                viewModel.launchScript(context, script)
                showNewScriptDialog = false
                selectedTab = 3 // Jump to runs log
            }
        )
    }

    // Modal Card: Unix Real-time Terminal Logs
    activeRunForLogs?.let { run ->
        // Query the live object to fetch running updates from DB Flow
        val liveRun = runs.firstOrNull { it.id == run.id } ?: run
        LogConsoleDialog(
            run = liveRun,
            onDismiss = { activeRunForLogs = null },
            onKill = { viewModel.terminateRun(context, liveRun) }
        )
    }
}

// Control Panel tab
@Composable
fun ControlPanelTab(
    installState: PythonManager.InstallState,
    installProgress: Float,
    installStatusText: String,
    runs: List<ScriptRun>,
    onInstall: () -> Unit,
    onLogsRequest: (ScriptRun) -> Unit,
    onKillRequest: (ScriptRun) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Python Core Subsystem",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This app launches native Python 3 files as independent background OS threads. It compiles standard PyPI modules using an optimized SSL bypass configuration.",
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (installState != PythonManager.InstallState.INSTALLED) {
                        LinearProgressIndicator(
                            progress = { if (installState == PythonManager.InstallState.NOT_INSTALLED) 0f else installProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ${installState.name.replace("_", " ")}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = when (installState) {
                                PythonManager.InstallState.INSTALLED -> Color(0xFF4CAF50)
                                PythonManager.InstallState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                        if (installState != PythonManager.InstallState.INSTALLED &&
                            installState != PythonManager.InstallState.DOWNLOADING &&
                            installState != PythonManager.InstallState.EXTRACTING
                        ) {
                            Button(onClick = onInstall) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download Subsystem")
                            }
                        }
                    }
                }
            }
        }

        val activeRuns = runs.filter { it.status == "RUNNING" || it.status == "PENDING" }
        if (activeRuns.isNotEmpty()) {
            item {
                Text(
                    text = "Running Background Threads (${activeRuns.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(activeRuns) { run ->
                ActiveRunCard(
                    run = run,
                    onLogsRequest = onLogsRequest,
                    onKillRequest = onKillRequest
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No active python background tasks",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// Active run items
@Composable
fun ActiveRunCard(
    run: ScriptRun,
    onLogsRequest: (ScriptRun) -> Unit,
    onKillRequest: (ScriptRun) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLogsRequest(run) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFFFB300)) // Amber blinking indicator
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.scriptName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Started at: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(run.startTime))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onLogsRequest(run) }) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Show Terminal Logs",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onKillRequest(run) }) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Script",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Scripts workspace
@Composable
fun ScriptsWorkspaceTab(
    installState: PythonManager.InstallState,
    scripts: List<PythonScript>,
    onRun: (PythonScript) -> Unit,
    onEdit: (PythonScript) -> Unit,
    onDelete: (PythonScript) -> Unit
) {
    if (scripts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(64.dp)
                )
                Text("No python scripts saved yet.")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scripts) { script ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(script) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Python script",
                            tint = MaterialTheme.colorScheme.secondary
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = script.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Code size: ${script.content.length} characters",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row {
                            IconButton(
                                onClick = { onRun(script) },
                                enabled = installState == PythonManager.InstallState.INSTALLED
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Run script",
                                    tint = if (installState == PythonManager.InstallState.INSTALLED) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            IconButton(onClick = { onEdit(script) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit script",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDelete(script) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete script",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Pip Management Tab
@Composable
fun PipManagerTab(
    installState: PythonManager.InstallState,
    installedPackages: List<PipPackageInfo>,
    isRefreshing: Boolean,
    onInstallPackage: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var queryPackageName by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("") }

    if (installState != PythonManager.InstallState.INSTALLED) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Pip package manager unavailable until Python environment installation is completed.",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pip install box
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Install from PyPI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = queryPackageName,
                        onValueChange = { queryPackageName = it },
                        placeholder = { Text("e.g. requests, bs4, numpy") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (queryPackageName.isNotBlank()) {
                                onInstallPackage(queryPackageName)
                                queryPackageName = ""
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pip_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Button(
                        onClick = {
                            if (queryPackageName.isNotBlank()) {
                                onInstallPackage(queryPackageName)
                                queryPackageName = ""
                            }
                        },
                        modifier = Modifier.testTag("pip_install_btn")
                    ) {
                        Text("Install")
                    }
                }
            }
        }

        // Active listing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Installed Packages (${installedPackages.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh list")
                }
            }
        }

        // Search bar
        TextField(
            value = searchFilter,
            onValueChange = { searchFilter = it },
            placeholder = { Text("Search installed packages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val filteredPackages = installedPackages.filter {
            it.name.contains(searchFilter, ignoreCase = true)
        }

        if (filteredPackages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchFilter.isEmpty()) "No non-standard modules detected" else "No matching packages found",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredPackages) { pkg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pkg.name, fontWeight = FontWeight.Medium)
                            Text(
                                "v${pkg.version}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

// Run history tab
@Composable
fun RunHistoryTab(
    runs: List<ScriptRun>,
    onLogsRequest: (ScriptRun) -> Unit,
    onKillRequest: (ScriptRun) -> Unit,
    onDeleteHistoryItem: (ScriptRun) -> Unit,
    onClearAll: () -> Unit
) {
    if (runs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HistoryToggleOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(64.dp)
                )
                Text("Zero execution records found")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Execution logs history", fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearAll) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History", color = MaterialTheme.colorScheme.error)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(runs) { run ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogsRequest(run) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = run.scriptName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Status badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (run.status) {
                                    "COMPLETED" -> Color(0xFFE8F5E9)
                                    "FAILED" -> Color(0xFFFFEBEE)
                                    "KILLED" -> Color(0xFFECEFF1)
                                    else -> Color(0xFFFFF8E1)
                                }
                            ) {
                                Text(
                                    text = run.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (run.status) {
                                        "COMPLETED" -> Color(0xFF2E7D32)
                                        "FAILED" -> Color(0xFFC62828)
                                        "KILLED" -> Color(0xFF37474F)
                                        else -> Color(0xFFF57F17)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                                Text(
                                    text = "Start: ${sdf.format(Date(run.startTime))}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Duration: ${run.duration / 1000f}s" + (run.exitCode?.let { " | Exit: $it" } ?: ""),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Row {
                                IconButton(onClick = { onLogsRequest(run) }) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = "Show Logs",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (run.status == "RUNNING" || run.status == "PENDING") {
                                    IconButton(onClick = { onKillRequest(run) }) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Kill Process",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { onDeleteHistoryItem(run) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete run",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fullscreen Script Editor Dialog
@Composable
fun ScriptEditorDialog(
    script: PythonScript,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRun: (String, String) -> Unit
) {
    var scriptName by remember { mutableStateOf(script.name) }
    var scriptContent by remember { mutableStateOf(script.content) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Editor header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }

                    Text(
                        text = if (script.id == 0) "New Script" else "Edit Script",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Row {
                        Button(
                            onClick = {
                                if (scriptName.isNotBlank() && scriptContent.isNotBlank()) {
                                    onSave(scriptName, scriptContent)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = scriptName.isNotBlank() && scriptContent.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Save", fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (scriptName.isNotBlank() && scriptContent.isNotBlank()) {
                                    onRun(scriptName, scriptContent)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = scriptName.isNotBlank() && scriptContent.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run", fontSize = 13.sp)
                        }
                    }
                }

                // Name TextField
                TextField(
                    value = scriptName,
                    onValueChange = { scriptName = it },
                    placeholder = { Text("ScriptName.py") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .testTag("editor_filename"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // Large Monospace Editor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Line numbers column
                    val lines = scriptContent.split("\n")
                    val lineCount = lines.size.coerceAtLeast(1)

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(top = 16.dp, end = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = i.toString(),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.height(18.dp) // align with edit lines
                            )
                        }
                    }

                    // Python content field
                    TextField(
                        value = scriptContent,
                        onValueChange = { scriptContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("editor_textarea"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        placeholder = { Text("Write your python code here...\n\n# e.g.:\nprint('Hello world!')\n") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

// Log Console dialog
@Composable
fun LogConsoleDialog(
    run: ScriptRun,
    onDismiss: () -> Unit,
    onKill: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var queryStr by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E1E1E) // Premium JetBlack shell background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Shell titlebar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D2D2D))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(run.scriptName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text("Status: ${run.status}", fontSize = 11.sp, color = when (run.status) {
                                "RUNNING" -> Color(0xFFFFB300)
                                "COMPLETED" -> Color(0xFF4CAF50)
                                else -> Color(0xFFEF5350)
                            })
                        }
                    }

                    Row {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(run.logs))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = Color.White)
                        }

                        if (run.status == "RUNNING" || run.status == "PENDING") {
                            IconButton(onClick = onKill) {
                                Icon(Icons.Default.StopCircle, contentDescription = "Kill Process", tint = Color(0xFFEF5350))
                            }
                        }
                    }
                }

                // Shell Search Filtering
                TextField(
                    value = queryStr,
                    onValueChange = { queryStr = it },
                    placeholder = { Text("Filter console logs...", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Console logging screen
                val filteredLogs = if (queryStr.isEmpty()) {
                    run.logs
                } else {
                    run.logs.split("\n").filter {
                        it.contains(queryStr, ignoreCase = true)
                    }.joinToString("\n")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = if (queryStr.isEmpty()) "Console session initialized. Logs pending..." else "No log lines matching query",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = filteredLogs,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFF00FF00), // Matrix Terminal Green
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Workaround for some build-in size constraints in Jetpack Compose Scaffold
fun Modifier.fillPanel(): Modifier = this.fillMaxWidth()
