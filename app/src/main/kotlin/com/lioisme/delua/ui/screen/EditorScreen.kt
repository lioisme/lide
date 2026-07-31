package com.lioisme.delua.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.lioisme.delua.editor.EditorToolbar
import com.lioisme.delua.editor.LuaEditor
import com.lioisme.delua.editor.SearchBar
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import com.lioisme.delua.ui.theme.LocalIsDarkTheme
import com.lioisme.delua.ui.theme.SettingsManager
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SearchResultState(
    val matchCount: Int = 0,
    val currentMatch: Int = 0,
)

private const val AUTO_SAVE_DELAY_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onRun: (fileName: String) -> Unit,
    onDependencies: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = LocalIsDarkTheme.current
    val appEditorConfig by SettingsManager.editorConfig.collectAsState()

    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentFile by remember { mutableStateOf(project.mainFile) }
    var loadedText by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var editVersion by remember { mutableStateOf(0) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var showFileMenu by remember { mutableStateOf(false) }
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    val searchResult = remember { MutableStateFlow(SearchResultState()) }
    val searchResultState by searchResult.collectAsState()

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { projectManager.listProjectFiles(project) }
        files = loaded
        if (loaded.isNotEmpty() && !loaded.contains(currentFile) && !dirty) {
            currentFile = loaded.first()
        }
    }

    LaunchedEffect(currentFile) {
        loadedText = withContext(Dispatchers.IO) {
            projectManager.getProjectScript(project, currentFile)
        }
    }

    val extPermissionMissing = remember {
        mutableStateOf(!hasStoragePermission(context))
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        extPermissionMissing.value = !hasStoragePermission(context)
    }

    val saveNow: () -> Unit = {
        val editor = editorRef.value
        if (editor != null && dirty && !extPermissionMissing.value) {
            val file = currentFile
            dirty = false
            scope.launch(Dispatchers.IO) {
                // Content.toString 内部持有 ReadWriteLock，快照可安全在后台线程获取。
                projectManager.saveProjectScript(project, file, editor.text.toString())
            }
        }
    }

    LaunchedEffect(editVersion) {
        if (editVersion == 0) return@LaunchedEffect
        delay(AUTO_SAVE_DELAY_MS)
        val editor = editorRef.value ?: return@LaunchedEffect
        if (dirty && !extPermissionMissing.value) {
            val file = currentFile
            dirty = false
            withContext(Dispatchers.IO) {
                projectManager.saveProjectScript(project, file, editor.text.toString())
            }
        }
    }

    DisposableEffect(editorRef.value) {
        val editor = editorRef.value
        if (editor == null) {
            onDispose { }
        } else {
            val receipt = editor.subscribeAlways(PublishSearchResultEvent::class.java) { _ ->
                try {
                    val searcher = editor.getSearcher()
                    if (searcher.hasQuery()) {
                        val index = searcher.getCurrentMatchedPositionIndex()
                        searchResult.value = SearchResultState(
                            matchCount = searcher.getMatchedPositionCount(),
                            currentMatch = if (index >= 0) index + 1 else 0,
                        )
                    } else {
                        searchResult.value = SearchResultState()
                    }
                } catch (t: Throwable) {
                    searchResult.value = SearchResultState()
                }
            }
            onDispose { receipt.unsubscribe() }
        }
    }

    fun performSearch() {
        val editor = editorRef.value ?: return
        val searcher = editor.getSearcher()
        if (searchQuery.isBlank()) {
            if (searcher.hasQuery()) {
                searcher.stopSearch()
            }
            searchResult.value = SearchResultState()
            return
        }
        val searchType = if (isRegex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                         else EditorSearcher.SearchOptions.TYPE_NORMAL
        val options = EditorSearcher.SearchOptions(searchType, !isCaseSensitive)
        try {
            searcher.search(searchQuery, options)
        } catch (_: Exception) {
            searchResult.value = SearchResultState()
        }
    }

    LaunchedEffect(searchQuery, isCaseSensitive, isRegex) {
        performSearch()
    }

    fun getSearcher(): EditorSearcher? = editorRef.value?.getSearcher()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        getSearcher()?.stopSearch()
                        searchQuery = ""
                        searchResult.value = SearchResultState()
                    }
                    true
                } else false
            }
    ) {
        Scaffold(
            topBar = {
                EditorToolbar(
                    fileName = currentFile,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onBack = { saveNow(); onBack() },
                    onUndo = { editorRef.value?.undo() },
                    onRedo = { editorRef.value?.redo() },
                    onRun = { saveNow(); onRun(project.mainFile) },
                    onSearch = { isSearchVisible = true },
                    onDependencies = onDependencies,
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val text = loadedText
                if (text != null) {
                    LuaEditor(
                        text = text,
                        isDarkTheme = isDarkTheme,
                        config = appEditorConfig,
                        onContentModified = {
                            dirty = true
                            editVersion++
                        },
                        onUndoRedoStateChanged = { u, r -> canUndo = u; canRedo = r },
                        onRelease = { content ->
                            if (dirty && !extPermissionMissing.value) {
                                val file = currentFile
                                dirty = false
                                scope.launch(Dispatchers.IO) {
                                    projectManager.saveProjectScript(project, file, content)
                                }
                            }
                        },
                        editorRef = editorRef,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                Column(Modifier.align(Alignment.TopCenter)) {
                    if (extPermissionMissing.value) {
                        StoragePermissionBanner(
                            onGrant = {
                                manageStorageLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (isSearchVisible) {
                        SearchBar(
                            query = searchQuery,
                            replaceQuery = replaceQuery,
                            matchCount = searchResultState.matchCount,
                            currentMatch = searchResultState.currentMatch,
                            isCaseSensitive = isCaseSensitive,
                            isRegex = isRegex,
                            showReplace = showReplace,
                            onQueryChange = { searchQuery = it },
                            onReplaceQueryChange = { replaceQuery = it },
                            onNext = { getSearcher()?.gotoNext() },
                            onPrev = { getSearcher()?.gotoPrevious() },
                            onReplace = {
                                val searcher = getSearcher() ?: return@SearchBar
                                searcher.replaceCurrentMatch(replaceQuery)
                            },
                            onReplaceAll = {
                                val searcher = getSearcher() ?: return@SearchBar
                                searcher.replaceAll(replaceQuery)
                            },
                            onToggleCaseSensitive = { isCaseSensitive = !isCaseSensitive },
                            onToggleRegex = { isRegex = !isRegex },
                            onToggleReplace = { showReplace = !showReplace },
                            onClose = {
                                isSearchVisible = false
                                getSearcher()?.stopSearch()
                                searchQuery = ""
                                searchResult.value = SearchResultState()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (showFileMenu && files.size > 1) {
            AlertDialog(
                onDismissRequest = { showFileMenu = false },
                title = { Text(stringResource(R.string.select_file)) },
                text = {
                    Column {
                        files.forEach { file ->
                            TextButton(
                                onClick = {
                                    saveNow()
                                    currentFile = file
                                    showFileMenu = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    file,
                                    fontWeight = if (file == currentFile) FontWeight.Bold else FontWeight.Normal,
                                    color = if (file == currentFile)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFileMenu = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

private fun hasStoragePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return true
}

@Composable
private fun StoragePermissionBanner(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.editor_storage_permission_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.editor_storage_permission_grant))
            }
        }
    }
}
