package com.lioisme.lide.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 中英资源一致性门禁（T24，2026-08-12）。
 *
 * 守护以下契约（I-1 英文化基线 + T14-T18 的常态化回归）：
 * 1. values（英文基线）与 values-zh 的 string / plurals 键集**双向**一致
 *    （缺键会回退英文显示或资源查找失败，多键是死资源）；
 * 2. 同名键的中英文案模板参数（%1$s / %2$d）集合一致（参数错位会崩
 *    `getString` 格式化或显示错乱）；
 * 3. 复数资源英文侧必须 one/other 双档（英文复数语法必需），中文侧
 *    仅 other（中文无复数形态）；
 * 4. 语言标签常量表（[LocaleManager]）与资源/持久化语义一致。
 *
 * 注意：本测试从源码树读取资源（Gradle 单测工作目录 = 模块目录），
 * 不依赖 Android 运行时（与 EditorPaletteContrastTest 同款）。
 */
class ResourceConsistencyTest {
    private val enFile = File("src/main/res/values/strings.xml")
    private val zhFile = File("src/main/res/values-zh/strings.xml")

    private val stringRe = Regex("<string\\s+name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>")
    private val pluralsRe = Regex("<plurals\\s+name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</plurals>")
    private val itemRe = Regex("<item\\s+quantity=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</item>")
    private val formatRe = Regex("%(\\d+)\\$([a-z])")

    private data class Catalog(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        val allKeys: Set<String>
            get() = strings.keys + plurals.keys
    }

    private fun catalog(text: String): Catalog {
        val strings = stringRe.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        val plurals =
            pluralsRe
                .findAll(text)
                .associate { m ->
                    val name = m.groupValues[1]
                    val items = itemRe.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                    name to items
                }
        return Catalog(strings, plurals)
    }

    private fun formatArgs(value: String): Set<String> {
        // %% 为转义字面量，不参与参数比较。
        return formatRe.findAll(value.replace("%%", "")).map { "${it.groupValues[1]}:${it.groupValues[2]}" }.toSet()
    }

    /** 一个键的全部参数（string 单值；plurals 聚合全部档位）。 */
    private fun argsOf(
        catalog: Catalog,
        key: String,
    ): Set<String> {
        catalog.strings[key]?.let { return formatArgs(it) }
        val plurals = catalog.plurals[key] ?: return emptySet()
        return plurals.values.flatMap { formatArgs(it) }.toSet()
    }

    @Test
    fun `中英键集双向一致`() {
        val en = catalog(enFile.readText())
        val zh = catalog(zhFile.readText())
        assertEquals(
            "中文侧多出（英文基线缺失）: ${zh.allKeys - en.allKeys}",
            emptySet<String>(),
            zh.allKeys - en.allKeys,
        )
        assertEquals(
            "英文侧多出（中文缺失）: ${en.allKeys - zh.allKeys}",
            emptySet<String>(),
            en.allKeys - zh.allKeys,
        )
    }

    @Test
    fun `同名键模板参数一致`() {
        val en = catalog(enFile.readText())
        val zh = catalog(zhFile.readText())
        val mismatched =
            en.allKeys.filter { key -> argsOf(en, key) != argsOf(zh, key) }
        assertTrue("模板参数（%编号:类型）不一致的键: $mismatched", mismatched.isEmpty())
    }

    @Test
    fun `英文复数含 one 与 other 双档`() {
        val en = catalog(enFile.readText())
        assertTrue("英文侧应有复数资源", en.plurals.isNotEmpty())
        for ((name, items) in en.plurals) {
            assertTrue("$name 缺 one 档", "one" in items)
            assertTrue("$name 缺 other 档", "other" in items)
        }
    }

    @Test
    fun `中文复数仅 other 档`() {
        val zh = catalog(zhFile.readText())
        for ((name, items) in zh.plurals) {
            assertEquals("中文 $name 应仅 other 档", setOf("other"), items.keys)
        }
    }

    @Test
    fun `语言标签常量与资源语义一致`() {
        val en = catalog(enFile.readText())
        assertTrue("lang_en 缺", en.strings.containsKey("lang_en"))
        assertTrue("lang_zh 缺", en.strings.containsKey("lang_zh"))
        assertTrue("system_default 缺", en.strings.containsKey("system_default"))
        // LocaleManager 语言标签 = BCP-47，与资源限定符一致。
        assertEquals("en", LocaleManager.TAG_EN)
        assertEquals("zh", LocaleManager.TAG_ZH)
        assertEquals("system", LocaleManager.TAG_SYSTEM)
    }
}
