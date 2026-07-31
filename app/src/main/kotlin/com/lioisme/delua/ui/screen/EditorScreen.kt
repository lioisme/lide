package com.lioisme.delua.ui.screen

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R
import com.androlua.LuaApplication
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import com.lioisme.delua.ui.editor.EditorToolbar
import com.lioisme.delua.ui.editor.LuaEditor
import com.lioisme.delua.ui.editor.SearchBar
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.CodeEditor

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
    val files = remember { projectManager.listProjectFiles(project) }
    var currentFile by remember {
        mutableStateOf(if (files.contains(project.mainFile)) project.mainFile else (files.firstOrNull() ?: project.mainFile))
    }
    var content by remember {
        mutableStateOf(projectManager.getProjectScript(project, currentFile))
    }
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
    var matchCount by remember { mutableStateOf(0) }

    LaunchedEffect(currentFile) {
        content = projectManager.getProjectScript(project, currentFile)
    }

    val extPermissionMissing = !hasStoragePermission(context)

    val autoSave: () -> Unit = {
        if (!extPermissionMissing) {
            projectManager.saveProjectScript(project, currentFile, content)
        }
    }

    fun performSearch() {
        val editor = editorRef.value ?: return
        if (searchQuery.isBlank()) {
            editor.getSearcher().stopSearch()
            matchCount = 0
            return
        }
        val searchType = if (isRegex) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                         else EditorSearcher.SearchOptions.TYPE_NORMAL
        val options = EditorSearcher.SearchOptions(searchType, !isCaseSensitive)
        editor.getSearcher().search(searchQuery, options)
        val text = editor.text.toString()
        matchCount = if (isCaseSensitive) {
            if (isRegex) {
                try { Regex(searchQuery).findAll(text).count() } catch (_: Exception) { 0 }
            } else {
                text.split(searchQuery).size - 1
            }
        } else {
            if (isRegex) {
                try { Regex(searchQuery, RegexOption.IGNORE_CASE).findAll(text).count() } catch (_: Exception) { 0 }
            } else {
                text.split(searchQuery, ignoreCase = true).size - 1
            }
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
                        matchCount = 0
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
                    onBack = { autoSave(); onBack() },
                    onUndo = { editorRef.value?.undo() },
                    onRedo = { editorRef.value?.redo() },
                    onRun = { autoSave(); onRun(project.mainFile) },
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
                LuaEditor(
                    content = content,
                    onContentChanged = { content = it },
                    onUndoRedoStateChanged = { u, r -> canUndo = u; canRedo = r },
                    editorRef = editorRef,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isSearchVisible) {
                    SearchBar(
                        query = searchQuery,
                        replaceQuery = replaceQuery,
                        matchCount = matchCount,
                        currentMatch = 0,
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
                            searcher.replaceAll(replaceQuery) { performSearch() }
                        },
                        onToggleCaseSensitive = { isCaseSensitive = !isCaseSensitive },
                        onToggleRegex = { isRegex = !isRegex },
                        onToggleReplace = { showReplace = !showReplace },
                        onClose = {
                            isSearchVisible = false
                            getSearcher()?.stopSearch()
                            matchCount = 0
                            searchQuery = ""
                        },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth(),
                    )
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
                                    if (!extPermissionMissing) {
                                        projectManager.saveProjectScript(project, currentFile, content)
                                    }
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
