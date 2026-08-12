package com.lioisme.lide.editor

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.reader.LanguageDefinitionReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * 语言目录单一事实源一致性校验（架构审计 A-2，2026-08-06）。
 *
 * [editor/src/main/assets/textmate/languages.json] 是唯一语言定义源：
 * - 运行时经 sora 原生 [LanguageDefinitionReader] 读取，天然保留 `embeddedLanguages`
 *   嵌入语言映射（不再有 Kotlin 镜像表；
 * - 本测试守护三件事：
 * 1. JSON 可经生产读取路径解析（40 种、name/scope 唯一、嵌入语言映射保留，
 *    含纯文本兜底 scope `text.plain`）；
 * 2. JSON 引用的文法 / language-configuration 资产真实存在于源码 assets 树；
 * 3. 下游静态表（[LanguageDetector] / [LanguageKeywordRegistry]）的全部 scope
 *    均收录于 JSON（防止下游新增 scope 未注册文法 → 打开文件无高亮/被 Lua 诊断误伤）；
 * 4. [LanguageDetector] 对未知扩展名返回 `text.plain`（不再以 Lua 兜底，P0-3）。
 *
 * 注意：本测试从源码树读取资产（Gradle 单测工作目录 = 模块目录），不依赖 Android 运行时。
 */
class LanguageCatalogConsistencyTest {
    private lateinit var assetsRoot: File
    private val testResolver =
        io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver { path ->
            val file = File(assetsRoot, path)
            if (file.isFile) FileInputStream(file) else null
        }

    @Before
    fun setUp() {
        assetsRoot = resolveAssetsRoot()
        FileProviderRegistry.getInstance().addFileProvider(testResolver)
    }

    @After
    fun tearDown() {
        FileProviderRegistry.getInstance().removeFileProvider(testResolver)
    }

    private fun resolveAssetsRoot(): File {
        val candidates =
            listOf(
                File("src/main/assets"),
                File("editor/src/main/assets"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("assets root not found (cwd=${File("").absolutePath})")
    }

    private fun readDefinitions(): List<GrammarDefinition> = LanguageDefinitionReader.read("textmate/languages.json")

    private fun definitionsOf(defs: List<GrammarDefinition>): Map<String, GrammarDefinition> = defs.associateBy { it.name }

    @Test
    fun `语言目录经 sora 原生读取且嵌入语言映射保留`() {
        val defs = readDefinitions()
        assertEquals(40, defs.size)

        val names = defs.map { it.name }
        assertEquals("语言名必须唯一", names.size, names.distinct().size)

        val scopes = defs.map { it.scopeName }
        assertTrue("scopeName 禁止为 null", scopes.all { it != null })
        assertEquals("scopeName 必须唯一", scopes.size, scopes.distinct().size)

        val byName = definitionsOf(defs)
        assertEquals(4, byName.getValue("html").embeddedLanguages.size)
        assertEquals(5, byName.getValue("julia").embeddedLanguages.size)
        assertEquals(7, byName.getValue("php").embeddedLanguages.size)
        assertTrue("markdown 嵌入语言映射应最全", byName.getValue("markdown").embeddedLanguages.size >= 40)
    }

    @Test
    fun `语言目录所有引用的资产文件真实存在`() {
        val defs = readDefinitions()
        for (def in defs) {
            val grammarFile = def.grammar.filePath
            assertTrue("grammar 资产缺失: $grammarFile", File(assetsRoot, grammarFile).isFile)
            def.languageConfiguration?.let { configPath ->
                assertTrue("language-configuration 资产缺失: $configPath", File(assetsRoot, configPath).isFile)
            }
        }
    }

    @Test
    fun `LanguageDetector 的所有 scope 均登记于语言目录`() {
        val catalogScopes = readDefinitions().mapNotNull { it.scopeName }.toSet()
        val detectorInstance = LanguageDetector::class.java.getField("INSTANCE").get(null)
        val field = detectorInstance.javaClass.getDeclaredField("associations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val associations = field.get(detectorInstance) as Map<String, String>
        val uncovered = associations.values.filterNot { it in catalogScopes }.toSet()
        assertEquals("LanguageDetector 引用了未登记于 languages.json 的 scope: $uncovered", emptySet<String>(), uncovered)
    }

    @Test
    fun `未知扩展名文件回落纯文本 scope 而非 Lua`() {
        assertEquals("source.lua", LanguageDetector.detect("main.lua"))
        assertEquals("source.python", LanguageDetector.detect("test.py"))
        assertEquals("text.plain", LanguageDetector.detect("data.csv"))
        assertEquals("text.plain", LanguageDetector.detect("app.log"))
        assertEquals("text.plain", LanguageDetector.detect("script.ps1"))
        assertEquals("text.plain", LanguageDetector.detect("README"))
        assertEquals("text.plain", LanguageDetector.detect(""))
        val catalogScopes = readDefinitions().mapNotNull { it.scopeName }.toSet()
        assertTrue("text.plain 必须登记于语言目录", "text.plain" in catalogScopes)
    }

    @Test
    fun `LanguageKeywordRegistry 的所有 scope 均登记于语言目录`() {
        val catalogScopes = readDefinitions().mapNotNull { it.scopeName }.toSet()
        val registryInstance = LanguageKeywordRegistry::class.java.getField("INSTANCE").get(null)
        val field = registryInstance.javaClass.getDeclaredField("registry")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val registry = field.get(registryInstance) as Map<String, *>
        val uncovered = registry.keys.filterNot { it in catalogScopes }.toSet()
        assertEquals("LanguageKeywordRegistry 引用了未收录于 languages.json 的 scope: $uncovered", emptySet<String>(), uncovered)
    }
}
