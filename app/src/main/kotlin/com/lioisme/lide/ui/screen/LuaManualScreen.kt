package com.lioisme.lide.ui.screen

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manualMissing = stringResource(R.string.lua_manual_missing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.lua_manual))
                    }
                },
                navigationIcon = {
                    TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
    ) { padding ->
        val manualFile = File(context.filesDir, "luadoc/manual.html")
        AndroidView(
            factory = { ctx ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = false
                    if (manualFile.exists()) {
                        loadUrl("file://${manualFile.absolutePath}")
                    } else {
                        loadData(
                            "<html><body style='font-family:sans-serif;padding:16px'>" +
                                manualMissing + "</body></html>",
                            "text/html",
                            "utf-8",
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}
