package com.lioisme.lide.ui.screen

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.apibrowser.ApiReflection
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.ui.components.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 类详情：对齐 pro javaapi/main.lua 分组顺序（类头/构建方法/公有事件/
 * 公有 getter/公有 setter/公有字段/公有方法），成员名子串过滤，长按复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiClassDetailScreen(
    className: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 详情三态（性能审计 R5d）：Loading / Loaded / Failed —— 反射失败显示明确错误 + 重试，
    // 不再误用「无匹配结果」文案并无限转圈。retryTick 递增触发 produceState 重启重试。
    var retryTick by remember { mutableStateOf(0) }
    val detailState =
        produceState<DetailUiState>(
            initialValue = DetailUiState.Loading,
            className,
            retryTick,
        ) {
            value =
                try {
                    withContext(Dispatchers.IO) { ApiReflection.reflect(className) }
                        ?.let { DetailUiState.Loaded(it) }
                        ?: DetailUiState.Failed
                } catch (_: Throwable) {
                    DetailUiState.Failed
                }
        }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // 顶栏显示 API 简单名（Button），而非完整类名（android.widget.Button）。
                        text = simpleNameOf(className),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.api_member_filter_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                // E1（2026-08-12）：硬编码圆角 → MaterialTheme.shapes（medium = 12dp）。
                shape = MaterialTheme.shapes.medium,
            )

            when (val state = detailState.value) {
                DetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.api_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                DetailUiState.Failed -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.api_detail_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { retryTick++ }) {
                                Text(stringResource(R.string.api_retry))
                            }
                        }
                    }
                }

                is DetailUiState.Loaded -> {
                    val detail = state.detail
                    val q = query.trim()
                    val titles =
                        ApiGroupTitles(
                            constructors = stringResource(R.string.api_group_constructors),
                            events = stringResource(R.string.api_group_events),
                            getters = stringResource(R.string.api_group_getters),
                            setters = stringResource(R.string.api_group_setters),
                            fields = stringResource(R.string.api_group_fields),
                            methods = stringResource(R.string.api_group_methods),
                        )
                    // 成员元数据仅随 detail/titles 变化解析一次（memberNameOf 每成员一次 + 预小写）；
                    // 击键过滤只做一趟 nameLower.contains，零字符串解析零逐成员转换（性能审计 R4）。
                    val memberGroups = remember(detail, titles) { buildMemberGroups(detail, titles) }
                    val rows = remember(detail, q, titles, memberGroups) { buildRows(detail, memberGroups, q) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        items(rows.size) { index ->
                            val row = rows[index]
                            when (row) {
                                is DetailRow.Group -> {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = row.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }

                                is DetailRow.Member -> {
                                    MemberRow(
                                        text = row.text,
                                        onClick = { copyToClipboard(context, row.text, "lide-api") },
                                        onLongClick = { copyToClipboard(context, row.memberName, "lide-api") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DetailRow {
    data class Group(
        val title: String,
    ) : DetailRow

    data class Member(
        val text: String,
        val memberName: String,
    ) : DetailRow
}

/** 详情反射三态（性能审计 R5d）：Loading 转圈 / Loaded 内容 / Failed 错误 + 重试。 */
private sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Loaded(
        val detail: ApiReflection.Detail,
    ) : DetailUiState

    data object Failed : DetailUiState
}

/** 分组标题（Composable 层获取，保持可翻译）。 */
private data class ApiGroupTitles(
    val constructors: String,
    val events: String,
    val getters: String,
    val setters: String,
    val fields: String,
    val methods: String,
)

/** 成员元数据（一次解析，击键过滤复用）：显示文本 + 复制用成员名 + 预小写过滤键。 */
private data class MemberMeta(
    val text: String,
    val name: String,
    val nameLower: String,
)

/** 成员元数据预索引：memberNameOf 每成员仅解析一次（性能审计 R4）。分组顺序与 pro 一致。 */
private fun buildMemberGroups(
    detail: ApiReflection.Detail,
    titles: ApiGroupTitles,
): List<Pair<String, List<MemberMeta>>> {
    fun group(
        title: String,
        items: List<String>,
    ): Pair<String, List<MemberMeta>> =
        title to
            items.map { item ->
                val name = memberNameOf(item)
                MemberMeta(item, name, name.lowercase())
            }
    return listOf(
        group(titles.constructors, detail.constructors),
        group(titles.events, detail.events),
        group(titles.getters, detail.getters),
        group(titles.setters, detail.setters),
        group(titles.fields, detail.fields),
        group(titles.methods, detail.methods),
    )
}

/** 过滤行构建：头部恒显；query 非空时按预小写成员名子串过滤（pro medit 语义，保留 ignoreCase 行为）。 */
private fun buildRows(
    detail: ApiReflection.Detail,
    groups: List<Pair<String, List<MemberMeta>>>,
    query: String,
): List<DetailRow> {
    val result = ArrayList<DetailRow>()
    result.add(DetailRow.Member(detail.header, simpleNameOf(detail.header)))
    val q = query.trim().lowercase()
    for ((title, members) in groups) {
        if (members.isEmpty()) continue
        result.add(DetailRow.Group(title))
        for (member in members) {
            if (q.isEmpty() || member.nameLower.contains(q)) {
                result.add(DetailRow.Member(member.text, member.name))
            }
        }
    }
    return result
}

/** 成员名提取：pro 长按复制语义 —— 方法名( 前 token / 末尾 token。 */
private fun memberNameOf(signature: String): String {
    val paren = signature.indexOf('(')
    if (paren > 0) {
        val head = signature.substring(0, paren).trim()
        val dot = head.lastIndexOf('.')
        val candidate = if (dot >= 0) head.substring(dot + 1) else head
        if (candidate.isNotBlank()) return candidate
    }
    val parts = signature.trim().split(' ')
    return parts.lastOrNull() ?: signature
}

// simpleNameOf 复用 ApiBrowserComponents.kt 的 internal 实现（同包，列表行语义一致）。

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
