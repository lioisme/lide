package com.delua.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delua.androlua.engine.ExecutionResult
import com.delua.androlua.engine.LuaRunner
import com.delua.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    project: Project,
    fileName: String,
    luaRunner: LuaRunner,
    onBack: () -> Unit
) {
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var running by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project, fileName) {
        scope.launch {
            running = true
            val res = withContext(Dispatchers.IO) {
                luaRunner.runScript(project.path, fileName)
            }
            result = res
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run: ${project.name}/$fileName", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(12.dp)
            ) {
                if (running) {
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
                            Text("Running...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    result?.let { res ->
                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScroll)
                                .horizontalScroll(hScroll),
                            color = if (res.success)
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = res.fullText.ifEmpty { "(no output)" },
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (res.success)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
