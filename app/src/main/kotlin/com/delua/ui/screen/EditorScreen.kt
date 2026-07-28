package com.delua.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delua.model.Project
import com.delua.project.ProjectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    projectManager: ProjectManager,
    onBack: () -> Unit,
    onRun: (fileName: String) -> Unit
) {
    val files = remember { projectManager.listProjectFiles(project) }
    var currentFile by remember { mutableStateOf(files.firstOrNull() ?: project.mainFile) }
    var code by remember {
        mutableStateOf(TextFieldValue(projectManager.getProjectScript(project, currentFile)))
    }
    var statusText by remember {
        mutableStateOf("Line 1, Col 1 | Lua 5.2 | UTF-8 | ${project.name}")
    }
    var showFileMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { showFileMenu = true }) {
                        Text(
                            currentFile,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        )
                        if (files.size > 1) {
                            Text("  \u25BE", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        projectManager.saveProjectScript(project, currentFile, code.text)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        projectManager.saveProjectScript(project, currentFile, code.text)
                        onRun(currentFile)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                BasicTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        val text = it.text
                        val cursor = it.selection.start
                        val line = text.substring(0, cursor).count { c -> c == '\n' } + 1
                        val col = cursor - (text.substring(0, cursor).lastIndexOf('\n') + 1)
                        statusText = "Line $line, Col ${col + 1} | Lua 5.2 | UTF-8 | ${project.name}"
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll),
                    decorationBox = { innerTextField ->
                        if (code.text.isEmpty()) {
                            Text(
                                "-- enter Lua code here",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (showFileMenu && files.size > 1) {
        AlertDialog(
            onDismissRequest = { showFileMenu = false },
            title = { Text("Select File") },
            text = {
                Column {
                    files.forEach { file ->
                        TextButton(
                            onClick = {
                                projectManager.saveProjectScript(project, currentFile, code.text)
                                currentFile = file
                                code = TextFieldValue(projectManager.getProjectScript(project, file))
                                showFileMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                file,
                                fontWeight = if (file == currentFile) FontWeight.Bold else FontWeight.Normal,
                                color = if (file == currentFile)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileMenu = false }) { Text("Cancel") }
            }
        )
    }
}
