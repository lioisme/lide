package com.lioisme.delua.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lioisme.delua.R

@Composable
fun SearchBar(
    query: String,
    replaceQuery: String,
    matchCount: Int,
    currentMatch: Int,
    isCaseSensitive: Boolean,
    isRegex: Boolean,
    showReplace: Boolean,
    onQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleRegex: () -> Unit,
    onToggleReplace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp))
                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search,
                    ),
                )

                if (query.isNotEmpty()) {
                    Text(
                        "$currentMatch/$matchCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matchCount > 0) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.cd_search_prev),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown,
                            stringResource(R.string.cd_search_next),
                            modifier = Modifier.size(20.dp))
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close,
                        stringResource(R.string.cd_search_close),
                        modifier = Modifier.size(20.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 4.dp),
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    placeholder = { Text(stringResource(R.string.replace_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                )

                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onReplace,
                    enabled = query.isNotEmpty() && matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(stringResource(R.string.replace), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onReplaceAll,
                    enabled = query.isNotEmpty() && matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(stringResource(R.string.replace_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = isCaseSensitive,
                    onClick = onToggleCaseSensitive,
                    label = { Text(stringResource(R.string.case_sensitive), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = isRegex,
                    onClick = onToggleRegex,
                    label = { Text(stringResource(R.string.regex), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}
