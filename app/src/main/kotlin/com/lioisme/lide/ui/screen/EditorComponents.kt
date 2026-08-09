package com.lioisme.lide.ui.screen

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorConfig
import com.lioisme.lide.editor.EditorTab
import com.lioisme.lide.editor.EditorTabs
import com.lioisme.lide.editor.LuaEditor
import com.lioisme.lide.editor.pressAndHoldHapticFeedback
import com.lioisme.lide.session.EditorSession
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.concurrent.atomic.AtomicReference

/** 宿主存储权限门：Android 11+ 需要「所有文件访问」授权。 */
internal fun hasStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }
    return true
}

@Composable
internal fun StoragePermissionBanner(
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
            TextButton(
                onClick = onGrant,
                modifier = Modifier.pressAndHoldHapticFeedback(),
            ) {
                Text(stringResource(R.string.editor_storage_permission_grant))
            }
        }
    }
}

/**
 * 编辑器主体（标签行 + 编辑器 + 权限横幅）。与文件目录对话框解耦：
 * 状态全部经参数注入，仅依赖会话回调上报宿主。
 */
@Composable
internal fun EditorBody(
    session: EditorSession,
    contentPadding: PaddingValues,
    tabs: List<EditorTab>,
    selectedFile: String?,
    content: Content?,
    isDarkTheme: Boolean,
    editorConfig: EditorConfig,
    editorRef: AtomicReference<CodeEditor?>,
    onEditorBound: () -> Unit,
    storagePermissionMissing: Boolean,
    onTabClose: (String) -> Unit,
    onTabMoved: (from: Int, to: Int) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    onGrantStorage: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            // 输入法遮挡处理：IME 弹出时收缩编辑区（M3 Scaffold 已消费 contentWindowInsets，
            // imePadding = ime.union(systemBars) − consumed，键盘收起时不产生额外间距）。
            .imePadding()
            .padding(contentPadding),
    ) {
        if (tabs.isNotEmpty()) {
            EditorTabs(
                tabs = tabs,
                selectedFile = selectedFile,
                onTabClick = { session.select(it) },
                onTabClose = onTabClose,
                onTabMoved = onTabMoved,
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val currentContent = content
            if (currentContent != null && selectedFile != null) {
                LuaEditor(
                    content = currentContent,
                    isDarkTheme = isDarkTheme,
                    config = editorConfig,
                    fileName = selectedFile,
                    onContentModified = { session.onContentModified() },
                    onUndoRedoStateChanged = { u, r -> session.onUndoRedoChanged(u, r) },
                    onRelease = {
                        // 兜底保存：编辑内容由会话缓存持有，宿主经会话异步落盘。
                        session.flushSelectedDirtyAsync()
                    },
                    editorRef = editorRef,
                    onEditorBound = onEditorBound,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedFile == null) {
                        Text(
                            stringResource(R.string.editor_no_file),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            Column(Modifier.align(Alignment.TopCenter)) {
                if (storagePermissionMissing) {
                    StoragePermissionBanner(
                        onGrant = onGrantStorage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}