package com.delua.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delua.R
import com.delua.androlua.engine.LuaRunner
import com.delua.model.Project
import com.delua.project.ProjectManager
import com.delua.ui.editor.EditorToolbar
import com.delua.ui.editor.LuaEditor
import com.delua.ui.editor.updateDiagnostics
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onRun: (fileName: String) -> Unit,
) {
    val context = LocalContext.current
    val luaRunner = remember { LuaRunner(context) }
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
    val diagJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentFile) {
        content = projectManager.getProjectScript(project, currentFile)
    }

    val autoSave: () -> Unit = {
        projectManager.saveProjectScript(project, currentFile, content)
    }

    LaunchedEffect(content) {
        diagJob.value?.cancel()
        diagJob.value = scope.launch {
            delay(500)
            editorRef.value?.let { updateDiagnostics(it, content, luaRunner) }
        }
    }

    Scaffold(
        topBar = {
            EditorToolbar(
                fileName = currentFile,
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = { autoSave(); onBack() },
                onUndo = { editorRef.value?.undo() },
                onRedo = { editorRef.value?.redo() },
                onRun = { autoSave(); onRun(currentFile) },
            )
        }
    ) { padding ->
        LuaEditor(
            content = content,
            onContentChanged = { content = it },
            onUndoRedoStateChanged = { u, r -> canUndo = u; canRedo = r },
            editorRef = editorRef,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
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
                                projectManager.saveProjectScript(project, currentFile, content)
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
