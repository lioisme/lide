package com.delua.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import com.delua.androlua.common.model.LibraryInfo
import com.delua.androlua.common.model.ModuleInfo
import com.delua.androlua.common.model.ProjectDependencies
import com.delua.model.Project
import com.delua.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDependenciesScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onNavigateToLibraries: () -> Unit = {},
    onNavigateToModules: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val depManager = remember { DependencyManager(context) }

    var availableLibs by remember { mutableStateOf(listOf<LibraryInfo>()) }
    var availableModules by remember { mutableStateOf(listOf<ModuleInfo>()) }
    var selectedLibs by remember { mutableStateOf(setOf<String>()) }
    var selectedModules by remember { mutableStateOf(setOf<String>()) }
    var hasChanges by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        scope.launch {
            val libs = withContext(Dispatchers.IO) { depManager.listLibraries() }
            val mods = withContext(Dispatchers.IO) { depManager.listModules() }
            val deps = withContext(Dispatchers.IO) { depManager.getProjectDependencies(project.path) }
            availableLibs = libs
            availableModules = mods
            selectedLibs = deps.libs.toSet()
            selectedModules = deps.modules.toSet()
        }
    }

    LaunchedEffect(Unit) { loadData() }

    fun saveDependencies() {
        scope.launch {
            val deps = ProjectDependencies(
                libs = selectedLibs.toList(),
                modules = selectedModules.toList()
            )
            val result = withContext(Dispatchers.IO) {
                depManager.setProjectDependencies(project.path, deps)
            }
            result.fold(
                onSuccess = {
                    hasChanges = false
                    Toast.makeText(context, "Dependencies saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                onFailure = { e ->
                    snackbarHostState.showSnackbar("Save failed: ${e.message}")
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dependencies — ${project.name}") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) {
                            saveDependencies()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { saveDependencies() }, enabled = true) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_dependencies))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.java_libraries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (availableLibs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.LibraryBooks, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.no_dep_available), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onNavigateToLibraries) {
                                Text(stringResource(R.string.go_to_library_manager), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                items(availableLibs, key = { "lib_${it.name}" }) { lib ->
                    val checked = selectedLibs.contains(lib.name)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedLibs = selectedLibs - lib.name
                                    else selectedLibs = selectedLibs + lib.name
                                    hasChanges = true
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(lib.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(8.dp))
                                    Text("v${lib.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (lib.description.isNotBlank()) {
                                    Text(lib.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.lua_modules),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (availableModules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.no_dep_available), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onNavigateToModules) {
                                Text(stringResource(R.string.go_to_module_manager), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                items(availableModules, key = { "mod_${it.name}" }) { mod ->
                    val checked = selectedModules.contains(mod.name)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedModules = selectedModules - mod.name
                                    else selectedModules = selectedModules + mod.name
                                    hasChanges = true
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mod.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(8.dp))
                                    Text("v${mod.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (mod.description.isNotBlank()) {
                                    Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.project_local_libs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val localLibs = remember { projectManager.getLocalLibFiles(project) }
                if (localLibs.isEmpty()) {
                    Text(
                        "(none)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                } else {
                    localLibs.forEach { libName ->
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("• $libName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
