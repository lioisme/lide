package com.delua.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delua.R
import com.delua.androlua.common.DependencyManager
import com.delua.androlua.common.model.ModuleInfo
import com.delua.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleManagerScreen(
    projectManager: ProjectManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf(listOf<ModuleInfo>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showUninstallDialog by remember { mutableStateOf<ModuleInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf<ModuleInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val depManager = remember { DependencyManager(context) }

    fun refresh() {
        scope.launch {
            modules = withContext(Dispatchers.IO) { depManager.listModules() }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val tempDir = File(context.cacheDir, "module_install_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                for (uri in uris) {
                    val inputStream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                    if (inputStream == null) continue
                    val rawName = File(uri.lastPathSegment ?: "module.lua").name
                    withContext(Dispatchers.IO) {
                        val f = File(tempDir, rawName)
                        f.parentFile?.mkdirs()
                        inputStream.use { it.copyTo(f.outputStream()) }
                    }
                }
                val moduleName = uris.firstOrNull()?.let { File(it.lastPathSegment ?: "").name.substringBeforeLast(".") } ?: "module"
                val luaFiles = tempDir.listFiles()?.filter { it.name.endsWith(".lua") }?.sortedBy { it.name } ?: emptyList()
                if (luaFiles.isEmpty()) {
                    snackbarHostState.showSnackbar("No .lua files selected")
                    tempDir.deleteRecursively()
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    depManager.installModuleFromFiles(luaFiles, moduleName, "1.0")
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Module '$moduleName' installed", Toast.LENGTH_SHORT).show()
                        refresh()
                    },
                    onFailure = { e ->
                        snackbarHostState.showSnackbar("Install failed: ${e.message}")
                    }
                )
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            }
        }
    }

    val filteredModules = remember(modules, searchQuery) {
        if (searchQuery.isBlank()) modules
        else modules.filter { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch("*/*")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.install_module))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search modules...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (filteredModules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Extension, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.no_modules), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.module_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredModules, key = { it.name }) { mod ->
                        ModuleItem(
                            module = mod,
                            onClick = { showDetailDialog = mod },
                            onUninstall = { showUninstallDialog = mod },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    showUninstallDialog?.let { mod ->
        AlertDialog(
            onDismissRequest = { showUninstallDialog = null },
            title = { Text("Uninstall ${mod.name}?") },
            text = { Text("Module \"${mod.name}\" (v${mod.version}) will be removed from common storage.") },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallDialog = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { depManager.uninstallModule(mod.name) }
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Module '${mod.name}' uninstalled", Toast.LENGTH_SHORT).show()
                                refresh()
                            },
                            onFailure = { e ->
                                snackbarHostState.showSnackbar("Uninstall failed: ${e.message}")
                            }
                        )
                    }
                }) { Text("Uninstall") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    showDetailDialog?.let { mod ->
        AlertDialog(
            onDismissRequest = { showDetailDialog = null },
            title = { Text(mod.name) },
            text = {
                Column {
                    Text("Version: ${mod.version}", style = MaterialTheme.typography.bodyMedium)
                    if (mod.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (mod.author.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Author: ${mod.author}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (mod.files.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Files (${mod.files.size}):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        mod.files.forEach { file ->
                            Text("  • $file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (mod.installTime > 0) {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(mod.installTime))
                        Spacer(Modifier.height(4.dp))
                        Text("Installed: $date", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun ModuleItem(
    module: ModuleInfo,
    onClick: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(module.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text("v${module.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (module.description.isNotBlank()) {
                    Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
