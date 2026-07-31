package com.lioisme.delua.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.androlua.AlpManager
import com.lioisme.delua.androlua.AlpManager.AlpKind
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectListScreen(
    projectManager: ProjectManager,
    onProjectClick: (Project) -> Unit,
    onSettingsClick: () -> Unit,
    pendingImportPaths: List<String> = emptyList(),
) {
    var projects by remember { mutableStateOf(listOf<Project>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingExportProject by remember { mutableStateOf<Project?>(null) }
    var pendingDeleteProject by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val strImportSuccess = stringResource(R.string.import_success)
    val strImportFailed = stringResource(R.string.import_failed)
    val strExportSuccess = stringResource(R.string.export_success)
    val strExportFailed = stringResource(R.string.export_failed)

    suspend fun loadProjects() {
        projects = withContext(Dispatchers.IO) { projectManager.listProjects() }
    }

    LaunchedEffect(Unit) { loadProjects() }

    LaunchedEffect(pendingImportPaths) {
        if (pendingImportPaths.isNotEmpty()) {
            projects = withContext(Dispatchers.IO) { projectManager.listProjects() }
        }
    }

    if (isRefreshing) {
        LaunchedEffect(Unit) {
            loadProjects()
            isRefreshing = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            snackbarHostState.showSnackbar(errorMsg!!)
            delay(100)
            errorMsg = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingExportProject ?: return@rememberLauncherForActivityResult
        pendingExportProject = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                AlpManager(context).export(project.path, uri)
            }
            if (ok) {
                Toast.makeText(context, strExportSuccess, Toast.LENGTH_SHORT).show()
            } else {
                errorMsg = strExportFailed
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var projectCount = 0
            var pluginCount = 0
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    AlpManager(context).import(uri)
                }
                when (result?.kind) {
                    AlpKind.PROJECT -> projectCount++
                    AlpKind.PLUGIN -> pluginCount++
                    null -> {}
                }
            }
            val imported = projectCount + pluginCount
            if (imported > 0) {
                val message = when {
                    pluginCount > 0 && projectCount == 0 ->
                        context.getString(R.string.import_plugin_success, pluginCount)
                    pluginCount == 0 ->
                        context.getString(R.string.import_projects_success, projectCount)
                    else ->
                        context.getString(R.string.import_mixed_success, projectCount, pluginCount)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                loadProjects()
            } else {
                errorMsg = strImportFailed
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("delua", fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.import_alp))
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_project))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_projects),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.tap_to_create),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    itemsIndexed(projects) { index, project ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onProjectClick(project) },
                                    onLongClick = { pendingDeleteProject = project },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (project.packageName.isNotBlank()) {
                                    Text(
                                        text = project.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                pendingExportProject = project
                                exportLauncher.launch("${project.name}.alp")
                            }) {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = stringResource(R.string.export_alp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                        if (index < projects.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
            TextButton(
                onClick = {
                    val name = newName.trim()
                    if (name.isNotBlank()) {
                        showCreateDialog = false
                        newName = ""
                        scope.launch {
                            val created = withContext(Dispatchers.IO) { projectManager.createProject(name) }
                            if (created != null) {
                                loadProjects()
                            } else {
                                errorMsg = context.getString(R.string.project_create_failed, name)
                            }
                        }
                    }
                }
            ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingDeleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_project_title, project.name)) },
            text = { Text(stringResource(R.string.delete_project_message, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteProject = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            projectManager.deleteProject(project)
                        }
                        if (deleted) {
                            loadProjects()
                        } else {
                            errorMsg = context.getString(R.string.project_delete_failed, project.name)
                        }
                    }
                }) { Text(stringResource(R.string.project_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
