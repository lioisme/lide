package com.lioisme.delua.ui.screen

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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.androlua.common.DependencyManager
import com.lioisme.delua.androlua.common.model.LibraryInfo
import com.lioisme.delua.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryManagerScreen(
    projectManager: ProjectManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var libraries by remember { mutableStateOf(listOf<LibraryInfo>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showUninstallDialog by remember { mutableStateOf<LibraryInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf<LibraryInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val depManager = remember { DependencyManager(context) }

    fun refresh() {
        scope.launch {
            libraries = withContext(Dispatchers.IO) { depManager.listLibraries() }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val inputStream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                if (inputStream == null) {
                    snackbarHostState.showSnackbar("Cannot read file")
                    return@launch
                }
                val rawName = File(uri.lastPathSegment ?: "library.jar").name
                val name = rawName.substringBeforeLast(".").substringBeforeLast("-")
                val version = try {
                    rawName.substringBeforeLast(".").substringAfterLast("-")
                } catch (_: Exception) { "1.0" }
                val tempFile = withContext(Dispatchers.IO) {
                    val f = File(context.cacheDir, "lib_${System.currentTimeMillis()}_$rawName")
                    f.parentFile?.mkdirs()
                    inputStream.use { it.copyTo(f.outputStream()) }
                    f
                }
                val result = withContext(Dispatchers.IO) {
                    depManager.installLibrary(tempFile, name, version)
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Library '$name' installed", Toast.LENGTH_SHORT).show()
                        refresh()
                    },
                    onFailure = { e ->
                        snackbarHostState.showSnackbar("Install failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            }
        }
    }

    val filteredLibraries = remember(libraries, searchQuery) {
        if (searchQuery.isBlank()) libraries
        else libraries.filter { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch("*/*")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.install_library))
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
                placeholder = { Text("Search libraries...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (filteredLibraries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.no_libraries), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.library_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredLibraries, key = { it.name }) { lib ->
                        LibraryItem(
                            library = lib,
                            onClick = { showDetailDialog = lib },
                            onUninstall = { showUninstallDialog = lib },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    showUninstallDialog?.let { lib ->
        AlertDialog(
            onDismissRequest = { showUninstallDialog = null },
            title = { Text("Uninstall ${lib.name}?") },
            text = { Text("Library \"${lib.name}\" (v${lib.version}) will be removed from common storage.") },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallDialog = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { depManager.uninstallLibrary(lib.name) }
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Library '${lib.name}' uninstalled", Toast.LENGTH_SHORT).show()
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

    showDetailDialog?.let { lib ->
        AlertDialog(
            onDismissRequest = { showDetailDialog = null },
            title = { Text(lib.name) },
            text = {
                Column {
                    Text("Version: ${lib.version}", style = MaterialTheme.typography.bodyMedium)
                    if (lib.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(lib.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lib.author.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Author: ${lib.author}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    val formattedSize = if (lib.fileSize > 1024 * 1024) "${lib.fileSize / (1024 * 1024)} MB"
                    else if (lib.fileSize > 1024) "${lib.fileSize / 1024} KB"
                    else "${lib.fileSize} bytes"
                    Text("Size: $formattedSize", style = MaterialTheme.typography.bodySmall)
                    if (lib.installTime > 0) {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lib.installTime))
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
private fun LibraryItem(
    library: LibraryInfo,
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
                    Text(library.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text("v${library.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (library.description.isNotBlank()) {
                    Text(library.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
