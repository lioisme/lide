package com.lioisme.lide.ui.theme

import android.content.Context
import com.lioisme.lide.build.AfterBuild
import com.lioisme.lide.build.BuildOpts
import com.lioisme.lide.build.SigningScheme
import com.lioisme.lide.editor.EditorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREF_NAME = "lide_theme"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_USE_WALLPAPER = "use_wallpaper"
    private const val KEY_CUSTOM_SEED = "custom_seed"

    private const val EDITOR_PREF = "lide_editor"

    private const val BUILD_PREF = "lide_build"
    private const val BUILD_STRIP_NATIVE = "strip_native"
    private const val BUILD_COMPILE = "compile_lua"
    private const val BUILD_ENCRYPT = "encrypt_lua"
    private const val BUILD_SIGNING = "signing"
    private const val BUILD_SIGNING_SCHEME = "signing_scheme"
    private const val BUILD_ABI = "abi"
    private const val BUILD_AFTER_BUILD = "after_build"
    private const val BUILD_OUTPUT_DIR = "output_dir"
    private const val BUILD_OUTPUT_NAMING = "output_naming"

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    private val _editorConfig = MutableStateFlow(EditorConfig())
    val editorConfig: StateFlow<EditorConfig> = _editorConfig.asStateFlow()

    private val _buildOpts = MutableStateFlow(BuildOpts())
    val buildOpts: StateFlow<BuildOpts> = _buildOpts.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _themeConfig.value =
            ThemeConfig(
                appearanceMode =
                    try {
                        AppearanceMode.valueOf(
                            prefs.getString(KEY_APPEARANCE, AppearanceMode.SYSTEM.name) ?: AppearanceMode.SYSTEM.name,
                        )
                    } catch (_: IllegalArgumentException) {
                        AppearanceMode.SYSTEM
                    },
                useWallpaper = prefs.getBoolean(KEY_USE_WALLPAPER, true),
                customSeedColor = prefs.getLong(KEY_CUSTOM_SEED, 0xFF1976D2),
            )
        initEditor(context)
        initBuild(context)
    }

    fun update(
        config: ThemeConfig,
        context: Context,
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString(KEY_APPEARANCE, config.appearanceMode.name)
            .putBoolean(KEY_USE_WALLPAPER, config.useWallpaper)
            .putLong(KEY_CUSTOM_SEED, config.customSeedColor)
            .apply()
        _themeConfig.value = config
    }

    /** 编辑器配置按 [SettingsRegistry] 声明式键表驱动读写（B-4）。 */
    fun initEditor(context: Context) {
        val prefs = context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
        _editorConfig.value = SettingsRegistry.load(prefs)
    }

    fun updateEditor(
        config: EditorConfig,
        context: Context,
    ) {
        context
            .getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
            .edit()
            .apply { SettingsRegistry.applyTo(this, config) }
            .apply()
        _editorConfig.value = config
    }

    /** 恢复编辑器设置为默认值：清空编辑器键表并回落 [EditorConfig] 默认值。 */
    fun resetEditor(context: Context) {
        val prefs = context.getSharedPreferences(EDITOR_PREF, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _editorConfig.value = SettingsRegistry.load(prefs)
    }

    /** 打包通用选项（签名默认强制内置，防非法档案名残留）。 */
    fun initBuild(context: Context) {
        val prefs = context.getSharedPreferences(BUILD_PREF, Context.MODE_PRIVATE)
        _buildOpts.value =
            BuildOpts(
                stripNativeLibs = prefs.getBoolean(BUILD_STRIP_NATIVE, true),
                compileLua = prefs.getBoolean(BUILD_COMPILE, true),
                encryptBytecode = prefs.getBoolean(BUILD_ENCRYPT, true),
                signingProfileId = prefs.getString(BUILD_SIGNING, "") ?: "",
                signingScheme = SigningScheme.parse(prefs.getString(BUILD_SIGNING_SCHEME, "")),
                preferAbi = prefs.getString(BUILD_ABI, "") ?: "",
                afterBuild = AfterBuild.parse(prefs.getString(BUILD_AFTER_BUILD, "")),
                outputDir = prefs.getString(BUILD_OUTPUT_DIR, "") ?: "",
                outputNaming = prefs.getString(BUILD_OUTPUT_NAMING, "") ?: "",
            )
    }

    /** 更新打包通用选项并持久化。 */
    fun updateBuild(
        opts: BuildOpts,
        context: Context,
    ) {
        val prefs = context.getSharedPreferences(BUILD_PREF, Context.MODE_PRIVATE)
        prefs
            .edit()
            .putBoolean(BUILD_STRIP_NATIVE, opts.stripNativeLibs)
            .putBoolean(BUILD_COMPILE, opts.compileLua)
            .putBoolean(BUILD_ENCRYPT, opts.encryptBytecode)
            .putString(BUILD_SIGNING, opts.signingProfileId)
            .putString(BUILD_SIGNING_SCHEME, opts.signingScheme.name)
            .putString(BUILD_ABI, opts.preferAbi)
            .putString(BUILD_AFTER_BUILD, opts.afterBuild.name)
            .putString(BUILD_OUTPUT_DIR, opts.outputDir)
            .putString(BUILD_OUTPUT_NAMING, opts.outputNaming)
            .apply()
        _buildOpts.value = opts
    }
}
