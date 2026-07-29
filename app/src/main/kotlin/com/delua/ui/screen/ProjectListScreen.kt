package com.delua.ui.screen

import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.delua.model.Project
import com.delua.project.ProjectManager
import com.scwang.smart.refresh.header.MaterialHeader
import com.scwang.smart.refresh.layout.SmartRefreshLayout
import com.scwang.smart.refresh.layout.listener.ScrollBoundaryDecider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projectManager: ProjectManager,
    onProjectClick: (Project) -> Unit
) {
    var projects by remember { mutableStateOf(projectManager.listProjects()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
        }
    }

    val refreshLayoutRef = remember { mutableStateOf<SmartRefreshLayout?>(null) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(100L)
            val result = withContext(Dispatchers.IO) { projectManager.listProjects() }
            projects = result
            isRefreshing = false
            refreshLayoutRef.value?.finishRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("delua") },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Project")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { ctx ->
                SmartRefreshLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val composeView = ComposeView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setContent {
                            if (projects.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "No projects yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Tap + to create one",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    itemsIndexed(projects) { index, project ->
                                        val iconBitmap = remember(project.icon) {
                                            project.icon?.let { path ->
                                                try {
                                                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                                                } catch (_: Exception) {
                                                    null
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(onClick = { onProjectClick(project) })
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (iconBitmap != null) {
                                                Icon(
                                                    painter = BitmapPainter(iconBitmap),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = project.name,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                if (project.packageName.isNotBlank()) {
                                                    Text(
                                                        text = project.packageName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        if (index < projects.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                    item {
                                        Spacer(Modifier.height(1.dp).fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }

                    setRefreshContent(composeView)
                    setRefreshHeader(MaterialHeader(ctx))
                    setScrollBoundaryDecider(object : ScrollBoundaryDecider {
                        override fun canRefresh(content: View): Boolean {
                            return atTop
                        }

                        override fun canLoadMore(content: View): Boolean {
                            return false
                        }
                    })
                    setOnRefreshListener { _ ->
                        isRefreshing = true
                    }

                    refreshLayoutRef.value = this
                }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Project name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            projectManager.createProject(newName)
                            projects = projectManager.listProjects()
                            showCreateDialog = false
                            newName = ""
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
