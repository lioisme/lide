package com.lioisme.lide.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.TipIconButton

/*
 * 共享搜索输入组件（API 浏览器 / 日志猫共用，2026-08-15 LOGCAT_CORE B1 中立化，
 * 原 ApiBrowserComponents 内 `Api*` 前缀符号迁出，命名与职责解耦）。
 * 搜索状态机红线（rememberSearchBarState 无参 / searchActive 独立意图 /
 * LaunchedEffect 唯一动画驱动 / 收起路径三件套）由调用方宿主遵守，本组件不触碰
 * SearchBarState 动画（避免焦点回调与动画互相激发死循环）。
 */

/**
 * 搜索输入条（折叠态 SearchBar 与全屏 ExpandedFullScreenSearchBar 共用同一实现）。
 * 展开/收起意图经 [active]/[onActiveChange] 与外部独立 state 通信（官方语义）：
 * 返回按钮置于 leadingIcon、清除 + 筛选槽（[filterSlot]，含 DropdownMenu）置于
 * trailingIcon；[placeholder] 由调用方传入（API 浏览器 / 日志猫各自语义文案）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    placeholder: String,
    filterSlot: @Composable () -> Unit,
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = active,
        onExpandedChange = onActiveChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    TipIconButton(tooltip = stringResource(R.string.api_clear_search), onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.api_clear_search),
                        )
                    }
                }
                filterSlot()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 折叠态搜索条（AppBar 内）：仅单击进入 SearchView。
 *
 * 不挂载 SearchBarDefaults.InputField——其 BasicTextField 内部
 * `onFocusChanged { if (isFocused) onExpandedChange(true) }` 由"获得焦点"驱动展开，
 * 而长按（文本选择/光标）同样会请求焦点，导致长按也进入 SearchView。
 * 本组件用 [detectTapGestures] 区分单击（展开）与长按（无副作用），
 * 首尾图标按钮（返回 / 清除 / 筛选槽）各自保留单击语义与长按 tooltip。
 * 展开后的输入、焦点与键盘由 ExpandedFullScreenSearchBar 的 focusRequester 自动接管。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollapsedSearchInput(
    query: String,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onTapToExpand: () -> Unit,
    placeholder: String,
    filterSlot: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(container)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTapToExpand() },
                            onLongPress = {},
                        )
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (query.isEmpty()) placeholder else query,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (query.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (query.isNotEmpty()) {
            TipIconButton(tooltip = stringResource(R.string.api_clear_search), onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.api_clear_search),
                )
            }
        }
        filterSlot()
    }
}

/** 复制文本到剪贴板（label 语义参数化：调用方传 `lide-api` / `lide-log` 等）。 */
internal fun copyToClipboard(
    context: Context,
    text: String,
    label: String,
) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}
