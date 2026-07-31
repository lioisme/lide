package com.lioisme.delua.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androlua.LuaApplication
import com.lioisme.delua.R
import com.lioisme.delua.androlua.PluginInfo
import com.lioisme.delua.androlua.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(
    onRun: (PluginInfo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginManager = remember { PluginManager() }
    var plugins by remember { mutableStateOf(listOf<PluginInfo>()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strCannotReadFile = stringResource(R.string.cannot_read_file)
    val strPluginInvalid = stringResource(R.string.plugin_invalid)

    fun refresh() {
        scope.launch {
            plugins = withContext(Dispatchers.IO) {
                val extDir = LuaApplication.getInstance().luaExtDir
                pluginManager.scan("$extDir/plugin")
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val alpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val inputStream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                if (inputStream == null) {
                    snackbarHostState.showSnackbar(strCannotReadFile)
                    return@launch
                }
                val rawName = File(uri.lastPathSegment ?: "plugin.alp").name
                val tempFile = withContext(Dispatchers.IO) {
                    val f = File(context.cacheDir, "alp_${System.currentTimeMillis()}_$rawName")
                    f.parentFile?.mkdirs()
                    inputStream.use { it.copyTo(f.outputStream()) }
                    f
                }
                val extDir = LuaApplication.getInstance().luaExtDir
                val info = withContext(Dispatchers.IO) {
                    pluginManager.installFromAlp(tempFile.absolutePath, "$extDir/plugin")
                }
                withContext(Dispatchers.IO) { tempFile.delete() }
                if (info != null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.plugin_installed, info.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                } else {
                    snackbarHostState.showSnackbar(strPluginInvalid)
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_prefix, e.message)
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugins)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { alpPickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.plugin_install_alp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.plugin_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.plugin_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(plugins) { _, plugin ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onRun(plugin) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    plugin.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    if (plugin.author.isNotBlank()) {
                                        context.getString(R.string.plugin_version_author, plugin.version, plugin.author)
                                    } else {
                                        context.getString(R.string.plugin_version, plugin.version)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (plugin.description.isNotBlank()) {
                                    Text(
                                        plugin.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.plugin_run),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
