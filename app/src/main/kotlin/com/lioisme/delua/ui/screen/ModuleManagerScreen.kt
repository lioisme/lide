package com.lioisme.delua.ui.screen

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.filled.FolderOpen
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
import com.lioisme.delua.androlua.common.model.ModuleInfo
import com.lioisme.delua.project.ProjectManager
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
    var showInstallMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val depManager = remember { DependencyManager(context) }
    val strCannotReadFile = stringResource(R.string.cannot_read_file)
    val strNoLuaFiles = stringResource(R.string.module_no_lua_files)
    val strFolderEmpty = stringResource(R.string.module_folder_empty)

    fun refresh() {
        scope.launch {
            modules = withContext(Dispatchers.IO) { depManager.listModules() }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun installFromDir(sourceDir: File, name: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                depManager.installModule(sourceDir, name, "1.0")
            }
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.module_installed, name),
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                },
                onFailure = { e ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.install_failed, e.message)
                    )
                }
            )
        }
    }

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
                    if (inputStream == null) {
                        snackbarHostState.showSnackbar(strCannotReadFile)
                        tempDir.deleteRecursively()
                        return@launch
                    }
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
                    snackbarHostState.showSnackbar(strNoLuaFiles)
                    tempDir.deleteRecursively()
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    depManager.installModuleFromFiles(luaFiles, moduleName, "1.0")
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.module_installed, moduleName),
                            Toast.LENGTH_SHORT
                        ).show()
                        refresh()
                    },
                    onFailure = { e ->
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.install_failed, e.message)
                        )
                    }
                )
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_prefix, e.message)
                )
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val name = uri.lastPathSegment?.substringAfterLast(':') ?: "module"
                val tempDir = File(context.cacheDir, "module_dir_${System.currentTimeMillis()}")
                val ok = copyTreeRecursive(
                    context.contentResolver,
                    uri,
                    DocumentsContract.getTreeDocumentId(uri),
                    tempDir
                )
                if (!ok) {
                    snackbarHostState.showSnackbar(strCannotReadFile)
                    tempDir.deleteRecursively()
                    return@launch
                }
                if (tempDir.walkTopDown().none { it.isFile && it.name.endsWith(".lua", ignoreCase = true) }) {
                    snackbarHostState.showSnackbar(strFolderEmpty)
                    tempDir.deleteRecursively()
                    return@launch
                }
                installFromDir(tempDir, name)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_prefix, e.message)
                )
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
                    Box {
                        IconButton(onClick = { showInstallMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.install_module))
                        }
                        DropdownMenu(
                            expanded = showInstallMenu,
                            onDismissRequest = { showInstallMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.install_module_files)) },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    showInstallMenu = false
                                    filePickerLauncher.launch("*/*")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.install_module_folder)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = {
                                    showInstallMenu = false
                                    folderPickerLauncher.launch(null)
                                },
                            )
                        }
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
                placeholder = { Text(stringResource(R.string.search_modules)) },
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
            title = { Text(stringResource(R.string.uninstall_title, mod.name)) },
            text = {
                Text(
                    stringResource(R.string.module_uninstall_message, mod.name, mod.version)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallDialog = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { depManager.uninstallModule(mod.name) }
                        result.fold(
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.module_uninstalled, mod.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                refresh()
                            },
                            onFailure = { e ->
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.uninstall_failed, e.message)
                                )
                            }
                        )
                    }
                }) { Text(stringResource(R.string.uninstall)) }
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
                    Text(
                        stringResource(R.string.version_label, mod.version),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (mod.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (mod.author.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.author_label, mod.author),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (mod.files.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.module_files_label, mod.files.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        mod.files.forEach { file ->
                            Text("  $file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (mod.installTime > 0) {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(mod.installTime))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.installed_label, date),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

/** 把 SAF 树 URI 递归复制到本地目录（保留相对结构），供目录型模块安装。 */
private suspend fun copyTreeRecursive(
    resolver: ContentResolver,
    treeUri: Uri,
    parentDocId: String,
    outDir: File,
): Boolean = withContext(Dispatchers.IO) {
    try {
        outDir.mkdirs()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val childIds = mutableListOf<String>()
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (id != null) childIds.add(id)
            }
        }
        for (childId in childIds) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            val mime = resolver.getType(docUri)
            val displayName = resolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: "file"
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (!copyTreeRecursive(resolver, treeUri, childId, File(outDir, displayName))) {
                    return@withContext false
                }
            } else {
                val input = resolver.openInputStream(docUri) ?: return@withContext false
                input.use {
                    val dest = File(outDir, displayName)
                    dest.parentFile?.mkdirs()
                    it.copyTo(dest.outputStream())
                }
            }
        }
        true
    } catch (_: Exception) {
        false
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
                    Text(
                        stringResource(R.string.version_prefix, module.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (module.description.isNotBlank()) {
                    Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.uninstall),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
