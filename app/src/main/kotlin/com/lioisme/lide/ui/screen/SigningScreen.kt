package com.lioisme.lide.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.build.AliasInfo
import com.lioisme.lide.build.CertSubject
import com.lioisme.lide.build.SigningCrypto
import com.lioisme.lide.build.SigningManager
import com.lioisme.lide.build.SigningProfile
import com.lioisme.lide.ui.theme.LideTopBar
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("SigningScreen")

/** 指纹展示截断长度（Q16 L14 魔数收口）：SHA-256 十六进制 64 字符，界面行内展示截断 + 省略号。 */
private const val FINGERPRINT_DISPLAY_CHARS = 31

/**
 * 签名管理页：仅签名档案列表（RadioButton 单选默认，导入档案可导出 / 删除）。
 * 生成 / 导入功能收于顶栏溢出菜单；全部走官方 material3 组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningScreen(
    onBack: () -> Unit,
    onProfileChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // T5A（2026-08-12）：档案目录扫描移出组合期（首帧）与回调主线程——目录列举 +
    // 证书指纹解析合计秒级（RSA 证书 per-file），主线程执行会卡帧/ANR。
    var profiles by remember { mutableStateOf(listOf<SigningProfile>()) }
    var defaultId by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var importSource by remember { mutableStateOf<ByteArray?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<SigningProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<SigningProfile?>(null) }

    // 首帧加载：IO 读取档案列表与默认项，加载完成前列表为空（加载后一次性弹出，无感知）。
    LaunchedEffect(Unit) {
        val (list, def) =
            withContext(Dispatchers.IO) {
                SigningManager.profiles() to SigningManager.defaultId()
            }
        profiles = list
        defaultId = def
    }
    // 默认档案详情（指纹等）：IO 计算，仅默认项变化时重取。
    val defaultProfile by produceState<SigningProfile?>(null, defaultId) {
        value = withContext(Dispatchers.IO) { SigningManager.profile(defaultId.ifBlank { null }) }
    }

    // 签名档案导入：系统文件选择器（SAF 单文件），经 contentResolver 读字节
    // 后置入导入状态（无落盘副本，不存在临时文件清理）；读盘移 IO（T5A）。
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val bytes =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                    }
                if (bytes != null && bytes.isNotEmpty()) {
                    importSource = bytes
                } else {
                    Toast.makeText(context, R.string.signing_read_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            // 注意：exportTarget 只能在回调内清空；onConfirm 里提前置空会导致回调早退、SAF 空文件。
            val target = exportTarget
            exportTarget = null
            if (uri == null || target == null) return@rememberLauncherForActivityResult
            scope.launch {
                // 读密钥库字节 + SAF 流写盘移 IO（T5A；密钥库可达 MB 级）。
                val ok =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = SigningManager.exportBytes(target.id) ?: error("keystore missing")
                            val os = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
                            os.use { it.write(bytes) }
                        }.isSuccess
                    }
                Toast
                    .makeText(
                        context,
                        if (ok) R.string.signing_exported else R.string.signing_export_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    fun refresh() {
        scope.launch {
            val (list, def) =
                withContext(Dispatchers.IO) {
                    SigningManager.profiles() to SigningManager.defaultId()
                }
            profiles = list
            defaultId = def
            onProfileChanged()
        }
    }

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.signing_manage),
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.signing_generate)) },
                                onClick = {
                                    menuOpen = false
                                    showGenerate = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.signing_import)) },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("application/octet-stream"))
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            profiles.forEach { profile ->
                SigningRow(
                    profile = profile,
                    selected = profile.id == defaultProfile?.id,
                    onSelect = {
                        scope.launch {
                            // 默认标记持久化移 IO（T5A，目录/偏好写盘）。
                            withContext(Dispatchers.IO) { SigningManager.setDefault(profile.id) }
                            defaultId = profile.id
                            // 与打包通用选项的默认签名同步，保证打包页初始值一致。
                            SettingsManager.updateBuild(
                                SettingsManager.buildOpts.value.copy(signingProfileId = profile.id),
                                context,
                            )
                            refresh()
                        }
                    },
                    onExport = { if (!profile.isBuiltin) exportTarget = profile },
                    onDelete = {
                        if (!profile.isBuiltin) deleteTarget = profile
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    importSource?.let { bytes ->
        ImportDialog(
            bytes = bytes,
            onDone = {
                importSource = null
                refresh()
            },
            onDismiss = { importSource = null },
        )
    }
    if (showGenerate) {
        GenerateDialog(
            onConfirm = { name, alias, password, subject, validYears ->
                showGenerate = false
                scope.launch {
                    // RSA 密钥对 + PBKDF + X.509 自签生成移 IO（T5A，秒级阻塞）。
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching { SigningManager.generate(name, alias, password, subject, validYears) }
                        }
                    if (ok.isSuccess) {
                        Toast.makeText(context, R.string.signing_generated, Toast.LENGTH_SHORT).show()
                    } else {
                        // T5E：失败文案资源化（异常细节仅日志，不直达 UI）。
                        logger.warn(ok.exceptionOrNull()) { "generate failed" }
                        Toast.makeText(context, R.string.signing_generate_failed, Toast.LENGTH_LONG).show()
                    }
                    refresh()
                }
            },
            onDismiss = { showGenerate = false },
        )
    }
    exportTarget?.let { target ->
        ExportDialog(
            profile = target,
            onConfirm = { exportLauncher.launch(exportFileName(target)) },
            onDismiss = { exportTarget = null },
        )
    }
    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            profile = target,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    // 密钥库文件删除移 IO（T5A）。文件删除先于默认标记检查执行（原语义）。
                    withContext(Dispatchers.IO) { SigningManager.delete(target.id) }
                    if (SettingsManager.buildOpts.value.signingProfileId == target.id) {
                        SettingsManager.updateBuild(
                            SettingsManager.buildOpts.value.copy(signingProfileId = ""),
                            context,
                        )
                    }
                    refresh()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 档案行：RadioButton 表示默认；导入档案带导出 / 删除操作。 */
@Composable
private fun SigningRow(
    profile: SigningProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .clickable(onClick = onSelect),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Key,
                    null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                ) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (profile.isBuiltin) {
                            stringResource(R.string.signing_builtin)
                        } else {
                            "SHA-256 ${profile.certFingerprint.take(FINGERPRINT_DISPLAY_CHARS)}…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (profile.isBuiltin) FontFamily.Default else FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = onSelect)
                if (!profile.isBuiltin) {
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Default.FileDownload,
                            stringResource(R.string.signing_export),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 生成对话框草稿（证书内容全字段可自定义，见 [CertSubject]）。 */
private data class GenerateDraft(
    val name: String = "",
    val alias: String = "lidekey",
    val password: String = "",
    val validity: String = "100",
    val countryCode: String = "",
    val state: String = "",
    val locality: String = "",
    val organization: String = "",
    val organizationUnit: String = "",
    val commonName: String = "",
    val email: String = "",
    val street: String = "",
    val postalCode: String = "",
)

/** 生成新签名对话框：有效期 + 证书 subject DN 全字段（标准顺序，全部可选）。 */
@Composable
private fun GenerateDialog(
    onConfirm: (name: String, alias: String, password: String?, subject: CertSubject, validYears: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(GenerateDraft()) }
    var showPassword by remember { mutableStateOf(false) }

    val validity = draft.validity.trim().toIntOrNull()
    val validityError =
        validity == null || validity !in 1..SigningCrypto.MAX_VALID_YEARS
    val country = draft.countryCode.trim()
    val countryError = country.isNotEmpty() && !COUNTRY_CODE.matches(country)
    val email = draft.email.trim()
    val emailError = email.isNotEmpty() && !email.contains("@")
    val formValid = draft.name.isNotBlank() && !validityError && !countryError && !emailError

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.signing_generate)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.signing_generate_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.signing_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.alias,
                    onValueChange = { draft = draft.copy(alias = it) },
                    label = { Text(stringResource(R.string.signing_alias)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    label = stringResource(R.string.signing_gen_password),
                    supporting = stringResource(R.string.signing_gen_password_hint),
                    show = showPassword,
                    onToggleShow = { showPassword = !showPassword },
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.signing_gen_subject),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                CertTextField(
                    value = draft.validity,
                    onValueChange = { draft = draft.copy(validity = it) },
                    label = stringResource(R.string.signing_gen_validity),
                    supporting = stringResource(R.string.signing_gen_validity_hint),
                    isError = validityError,
                    keyboardType = KeyboardType.Number,
                    maxLength = 4,
                )
                CertTextField(
                    value = draft.countryCode,
                    onValueChange = { draft = draft.copy(countryCode = it) },
                    label = stringResource(R.string.signing_gen_c),
                    supporting =
                        if (countryError) {
                            stringResource(R.string.signing_gen_c_invalid)
                        } else {
                            stringResource(R.string.signing_gen_c_hint)
                        },
                    isError = countryError,
                    maxLength = 2,
                )
                CertTextField(
                    value = draft.state,
                    onValueChange = { draft = draft.copy(state = it) },
                    label = stringResource(R.string.signing_gen_st),
                )
                CertTextField(
                    value = draft.locality,
                    onValueChange = { draft = draft.copy(locality = it) },
                    label = stringResource(R.string.signing_gen_l),
                )
                CertTextField(
                    value = draft.organization,
                    onValueChange = { draft = draft.copy(organization = it) },
                    label = stringResource(R.string.signing_gen_o),
                )
                CertTextField(
                    value = draft.organizationUnit,
                    onValueChange = { draft = draft.copy(organizationUnit = it) },
                    label = stringResource(R.string.signing_gen_ou),
                )
                CertTextField(
                    value = draft.commonName,
                    onValueChange = { draft = draft.copy(commonName = it) },
                    label = stringResource(R.string.signing_gen_cn),
                    supporting = stringResource(R.string.signing_gen_cn_hint),
                )
                CertTextField(
                    value = draft.email,
                    onValueChange = { draft = draft.copy(email = it) },
                    label = stringResource(R.string.signing_gen_email),
                    supporting =
                        if (emailError) {
                            stringResource(R.string.signing_gen_email_invalid)
                        } else {
                            null
                        },
                    isError = emailError,
                )
                CertTextField(
                    value = draft.street,
                    onValueChange = { draft = draft.copy(street = it) },
                    label = stringResource(R.string.signing_gen_street),
                )
                CertTextField(
                    value = draft.postalCode,
                    onValueChange = { draft = draft.copy(postalCode = it) },
                    label = stringResource(R.string.signing_gen_postal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        draft.name.trim(),
                        draft.alias.ifBlank { "lidekey" },
                        draft.password.ifBlank { null },
                        CertSubject(
                            countryCode = country.uppercase(),
                            state = draft.state.trim(),
                            locality = draft.locality.trim(),
                            organization = draft.organization.trim(),
                            organizationUnit = draft.organizationUnit.trim(),
                            commonName = draft.commonName.trim(),
                            email = email,
                            street = draft.street.trim(),
                            postalCode = draft.postalCode.trim(),
                        ),
                        validity ?: SigningCrypto.DEFAULT_VALID_YEARS,
                    )
                },
                enabled = formValid,
            ) {
                Text(stringResource(R.string.signing_generate_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 国家代码校验：非空时须恰为 2 个 ASCII 字母（ISO 3166-1 alpha-2）。 */
private val COUNTRY_CODE = Regex("[A-Za-z]{2}")

/** 证书内容输入框：可选支持文本 / 错误态（支持文本在错误态下以 error 色展示）。 */
@Composable
private fun CertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = 128,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        label = { Text(label) },
        supportingText =
            supporting?.let {
                {
                    Text(
                        it,
                        color =
                            if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 导入对话框：阶段一（名称 + 密钥库密码）→ 列别名 → 阶段二（单选别名导入）。 */
@Composable
private fun ImportDialog(
    bytes: ByteArray,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storeType = remember(bytes) { SigningCrypto.detectStoreType(bytes) }
    val emptyMessage = stringResource(R.string.signing_empty)
    val readFailedMessage = stringResource(R.string.signing_read_failed)
    var name by remember(bytes) { mutableStateOf("") }
    var storePassword by remember(bytes) { mutableStateOf("") }
    var showPassword by remember(bytes) { mutableStateOf(false) }
    var aliases by remember(bytes) { mutableStateOf<List<AliasInfo>?>(null) }
    var selectedAlias by remember(bytes) { mutableStateOf<String?>(null) }
    var loading by remember(bytes) { mutableStateOf(false) }
    var error by remember(bytes) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        icon = { Icon(Icons.Default.FileOpen, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.signing_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val aliasList = aliases
                if (aliasList == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.signing_name)) },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PasswordField(
                        value = storePassword,
                        onValueChange = { storePassword = it },
                        label = stringResource(R.string.signing_store_password),
                        show = showPassword,
                        onToggleShow = { showPassword = !showPassword },
                        enabled = !loading,
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                } else if (aliasList.isEmpty()) {
                    Text(stringResource(R.string.signing_empty), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        stringResource(R.string.signing_alias_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        aliasList.forEach { alias ->
                            val enabled = alias.hasPrivateKey
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { selectedAlias = alias.alias }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedAlias == alias.alias,
                                    onClick = { selectedAlias = alias.alias },
                                    enabled = enabled,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(alias.alias, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        // Q14：指纹行整句进资源（signing_cert_detail）。
                                        stringResource(
                                            R.string.signing_cert_detail,
                                            alias.certFingerprint.take(FINGERPRINT_DISPLAY_CHARS),
                                            alias.algorithm,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!enabled) {
                                    Text(
                                        stringResource(R.string.signing_no_key),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val aliasList = aliases
            if (aliasList == null) {
                TextButton(
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            // PBKDF 口令派生 + 密钥库解析移 IO（T5A，毫秒~秒级）。
                            val result =
                                withContext(Dispatchers.IO) {
                                    runCatching { SigningCrypto.listAliases(bytes, storePassword.toCharArray(), storeType) }
                                }
                            loading = false
                            result
                                .onSuccess {
                                    aliases = it
                                    if (it.isEmpty()) error = emptyMessage
                                }.onFailure { e ->
                                    // T17：读取失败展示本地化文案（异常细节仅日志）。
                                    logger.warn(e) { "listAliases failed" }
                                    error = readFailedMessage
                                }
                        }
                    },
                    enabled = !loading && name.isNotBlank() && storePassword.isNotBlank(),
                ) {
                    Text(stringResource(R.string.signing_import_next))
                }
            } else {
                TextButton(
                    onClick = {
                        val alias = selectedAlias ?: return@TextButton
                        val info = aliasList.firstOrNull { it.alias == alias } ?: return@TextButton
                        scope.launch {
                            // 密钥库文件落盘移 IO（T5A）。
                            val ok =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        SigningManager.import(
                                            name = name.ifBlank { alias },
                                            storeType = storeType,
                                            storePassword = storePassword,
                                            keyAlias = alias,
                                            algorithm = info.algorithm,
                                            certFingerprint = info.certFingerprint,
                                            bytes = bytes,
                                        )
                                    }
                                }
                            if (ok.isSuccess) {
                                Toast.makeText(context, R.string.signing_imported, Toast.LENGTH_SHORT).show()
                                onDone()
                            } else {
                                // T5E：失败文案资源化（异常细节仅日志）。
                                logger.warn(ok.exceptionOrNull()) { "import failed" }
                                Toast.makeText(context, R.string.signing_import_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = selectedAlias != null,
                ) {
                    Text(stringResource(R.string.signing_import))
                }
            }
        },
        dismissButton = {
            if (aliases == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            } else {
                Row {
                    TextButton(
                        onClick = {
                            aliases = null
                            selectedAlias = null
                            error = null
                        },
                    ) {
                        Text(stringResource(R.string.signing_import_back))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        },
    )
}

/** 导出确认：展示别名与密钥库密码（自动生成的密码随档案保存，此处回显），确认后走 SAF 写盘。 */
@Composable
private fun ExportDialog(
    profile: SigningProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.signing_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    // Q14：档案元信息行整句进资源（signing_meta_line）。
                    stringResource(
                        R.string.signing_meta_line,
                        stringResource(R.string.signing_alias),
                        storeTypeLabel(profile),
                        profile.algorithm,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.signing_keystore_password),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    profile.storePassword,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    stringResource(R.string.signing_export_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.signing_export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 删除确认（破坏性操作：密钥库文件被永久删除）。 */
@Composable
private fun DeleteConfirmDialog(
    profile: SigningProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.signing_delete_confirm, profile.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 密码输入框：显隐切换 + 可选辅助文本。 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(if (show) R.string.signing_hide_password else R.string.signing_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun storeTypeLabel(profile: SigningProfile): String =
    when (profile.storeType) {
        SigningCrypto.JKS_STORE_TYPE -> stringResource(R.string.signing_type_jks)
        else -> stringResource(R.string.signing_type_pkcs12)
    }

/** SAF 建议文件名：档案名（清洗非法字符）+ .p12 / .jks。 */
private fun exportFileName(profile: SigningProfile): String {
    val base = profile.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "keystore" }
    val ext = if (profile.storeType == SigningCrypto.JKS_STORE_TYPE) "jks" else "p12"
    return "$base.$ext"
}
