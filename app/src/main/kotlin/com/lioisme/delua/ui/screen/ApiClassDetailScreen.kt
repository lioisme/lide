package com.lioisme.delua.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lioisme.delua.R
import com.lioisme.delua.apibrowser.ApiReflection
import com.lioisme.delua.editor.TipIconButton
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
    val detailState = produceState<ApiReflection.Detail?>(initialValue = null, className) {
        value = withContext(Dispatchers.IO) { ApiReflection.reflect(className) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val detail = detailState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = className,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.api_member_filter_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
            )

            when {
                detail == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.api_no_result),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    val q = query.trim()
                    val titles = ApiGroupTitles(
                        constructors = stringResource(R.string.api_group_constructors),
                        events = stringResource(R.string.api_group_events),
                        getters = stringResource(R.string.api_group_getters),
                        setters = stringResource(R.string.api_group_setters),
                        fields = stringResource(R.string.api_group_fields),
                        methods = stringResource(R.string.api_group_methods),
                    )
                    val rows = remember(detail, q, titles) { buildRows(detail, titles, q) }
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
                                        onClick = { copy(context, row.text) },
                                        onLongClick = { copy(context, row.memberName) },
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
    data class Group(val title: String) : DetailRow
    data class Member(val text: String, val memberName: String) : DetailRow
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

/** 分组顺序与 pro 一致；query 非空时按成员名子串过滤（pro medit 语义）。 */
private fun buildRows(detail: ApiReflection.Detail, titles: ApiGroupTitles, query: String): List<DetailRow> {
    val result = ArrayList<DetailRow>()
    val q = query
    fun group(title: String, items: List<String>) {
        if (items.isEmpty()) return
        result.add(DetailRow.Group(title))
        for (item in items) {
            if (q.isEmpty() || memberNameOf(item).contains(q, ignoreCase = true)) {
                result.add(DetailRow.Member(item, memberNameOf(item)))
            }
        }
    }
    result.add(DetailRow.Member(detail.header, simpleNameOf(detail.header)))
    group(titles.constructors, detail.constructors)
    group(titles.events, detail.events)
    group(titles.getters, detail.getters)
    group(titles.setters, detail.setters)
    group(titles.fields, detail.fields)
    group(titles.methods, detail.methods)
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

private fun simpleNameOf(signature: String): String {
    val dot = signature.lastIndexOf('.')
    return if (dot >= 0) signature.substring(dot + 1) else signature
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(text: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
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

private fun copy(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("delua-api", text))
    } catch (_: Throwable) {
    }
}
