package com.delua.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delua.R
import com.delua.androlua.engine.ExecutionResult
import com.delua.androlua.engine.LuaRunner
import com.delua.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReplEntry(val code: String, val result: ExecutionResult)
private enum class RunMode { REPL, SCRIPT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    project: Project,
    fileName: String,
    luaRunner: LuaRunner,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(listOf<ReplEntry>()) }
    var running by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var historyIndex by remember { mutableStateOf(-1) }
    var mode by remember { mutableStateOf(RunMode.REPL) }
    var runKey by remember { mutableStateOf(0) }
    val outputScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        luaRunner.initResident()
    }

    DisposableEffect(Unit) {
        onDispose { luaRunner.destroy() }
    }

    LaunchedEffect(runKey) {
        if (mode == RunMode.SCRIPT) {
            running = true
            entries = emptyList()
            val res = withContext(Dispatchers.IO) {
                luaRunner.runScript(project.path, fileName)
            }
            entries = listOf(ReplEntry("", res))
            running = false
        }
    }

    fun executeCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            running = true
            val res = withContext(Dispatchers.IO) {
                luaRunner.eval(trimmed, project.path)
            }
            entries = entries + ReplEntry(trimmed, res)
            history = (history + trimmed).takeLast(50)
            historyIndex = -1
            input = TextFieldValue("")
            running = false
            scope.launch { outputScroll.animateScrollTo(outputScroll.maxValue) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (mode == RunMode.REPL) stringResource(R.string.repl_title, project.name)
                        else stringResource(R.string.run_title, project.name, fileName),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (mode == RunMode.REPL) {
                        TextButton(onClick = {
                            mode = RunMode.SCRIPT
                            runKey++
                        }) {
                            Text(stringResource(R.string.run_main), maxLines = 1)
                        }
                    } else {
                        TextButton(onClick = {
                            mode = RunMode.REPL
                            luaRunner.destroy()
                            luaRunner.initResident()
                            entries = emptyList()
                            runKey++
                        }) {
                            Text(stringResource(R.string.repl_mode), maxLines = 1)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (running && mode == RunMode.SCRIPT && entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.running), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    LaunchedEffect(entries.size) {
                        if (entries.isNotEmpty()) {
                            vScroll.animateScrollTo(vScroll.maxValue)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll),
                    ) {
                        if (entries.isEmpty()) {
                            Text(
                                text = if (mode == RunMode.REPL) stringResource(R.string.repl_welcome)
                                else stringResource(R.string.no_output),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            entries.forEach { entry ->
                                if (entry.code.isNotEmpty()) {
                                    Text(
                                        text = "> ${entry.code}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp, end = 8.dp)
                                    )
                                }
                                Surface(
                                    color = if (entry.result.success)
                                        MaterialTheme.colorScheme.surface
                                    else
                                        MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = entry.result.fullText.ifEmpty {
                                            if (entry.code.isEmpty()) stringResource(R.string.no_output) else ""
                                        },
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                        color = if (entry.result.success)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (mode == RunMode.REPL) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.repl_input_hint),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            )
                        },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        minLines = 1,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { executeCode(input.text) },
                        enabled = !running && input.text.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_run))
                    }
                }
            }
        }
    }
}