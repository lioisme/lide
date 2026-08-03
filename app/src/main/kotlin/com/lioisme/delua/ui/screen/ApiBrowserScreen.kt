package com.lioisme.delua.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.apibrowser.ApiCategory
import com.lioisme.delua.apibrowser.ApiIndex
import com.lioisme.delua.apibrowser.ApiIndex.ApiClassEntry
import com.lioisme.delua.apibrowser.ApiIndex.Snapshot
import com.lioisme.delua.editor.TipIconButton
import com.lioisme.delua.project.ProjectManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * API 浏览器：AppBar 内嵌 Material3 SearchBar（展开即建议列表）+ 官方单选筛选菜单。
 * 索引在 IO 线程构建，快照缓存于进程级单例 [ApiIndex]。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ApiBrowserScreen(
    onBack: () -> Unit,
    onClassClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val strCopied = stringResource(R.string.api_copied)
    val indexState = produceState<Snapshot?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val projectManager = ProjectManager(context)
            val roots = projectManager.listProjects().map { File(it.path) }
            ApiIndex.build(context, roots)
        }
    }

    var selectedCategory by rememberSaveable { mutableStateOf<ApiCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val snapshot = indexState.value
    val filtered = remember(snapshot, selectedCategory, query) {
        snapshot?.let { filterSnapshot(it, selectedCategory, query) } ?: emptyList()
    }
    val categoryCounts = snapshot?.categoryCounts ?: emptyMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            keyboard?.hide()
                            searchExpanded = false
                        },
                        active = searchExpanded,
                        onActiveChange = { searchExpanded = it },
                        placeholder = { Text(stringResource(R.string.api_search_hint)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.api_clear_search),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when {
                            snapshot == null -> {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.api_loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            filtered.isEmpty() -> {
                                Text(
                                    stringResource(R.string.api_no_result),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            else -> {
                                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    items(filtered, key = { it.name }) { entry ->
                                        ClassRow(
                                            entry = entry,
                                            onClick = { onClassClick(entry.name) },
                                            onLongClick = {
                                                copyToClipboard(context, entry.name)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("${entry.name} · $strCopied")
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    Box {
                        TipIconButton(
                            tooltip = stringResource(R.string.api_filter),
                            onClick = { filterMenuOpen = true },
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.api_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false },
                        ) {
                            FilterMenuItem(
                                label = stringResource(R.string.api_category_all),
                                count = snapshot?.classes?.size,
                                selected = selectedCategory == null,
                                onClick = {
                                    selectedCategory = null
                                    filterMenuOpen = false
                                },
                            )
                            ApiCategory.entries.forEach { category ->
                                FilterMenuItem(
                                    label = categoryLabel(category),
                                    count = categoryCounts[category],
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = category
                                        filterMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                snapshot == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.api_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                filtered.isEmpty() -> {
                    Text(
                        stringResource(R.string.api_no_result),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        items(filtered, key = { it.name }) { entry ->
                            ClassRow(
                                entry = entry,
                                onClick = { onClassClick(entry.name) },
                                onLongClick = {
                                    copyToClipboard(context, entry.name)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("${entry.name} · $strCopied")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterMenuItem(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            RadioButton(selected = selected, onClick = null)
        },
        trailingIcon = {
            if (count != null) {
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassRow(
    entry: ApiClassEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val unavailable = !ApiIndex.isLoadable(entry.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (unavailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = categoryLabel(entry.category),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (unavailable) {
            Text(
                text = stringResource(R.string.api_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (entry.fromProject) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Text(
                    text = if (entry.fromProject) {
                        stringResource(R.string.api_category_project)
                    } else {
                        categoryLabel(entry.category)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun filterSnapshot(snapshot: Snapshot, category: ApiCategory?, query: String): List<ApiClassEntry> {
    val q = query.trim()
    return snapshot.classes.asSequence()
        .filter { category == null || it.category == category }
        .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
        .toList()
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("delua-api", text))
    } catch (_: Throwable) {
    }
}

@Composable
fun categoryLabel(category: ApiCategory): String = stringResource(
    when (category) {
        ApiCategory.COMMON_WIDGETS -> R.string.api_category_common
        ApiCategory.MATERIAL -> R.string.api_category_material
        ApiCategory.ANDROIDX -> R.string.api_category_androidx
        ApiCategory.R_RESOURCES -> R.string.api_category_r
        ApiCategory.ANDROLUA -> R.string.api_category_androlua
        ApiCategory.FRAMEWORK -> R.string.api_category_framework
        ApiCategory.JAVA -> R.string.api_category_java
        ApiCategory.PROJECT -> R.string.api_category_project
        ApiCategory.OTHER -> R.string.api_category_other
    }
)
