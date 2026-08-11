package com.lioisme.lide.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LocaleManager 语言解析纯逻辑门禁（T24，2026-08-12）。
 *
 * [LocaleManager.resolveTag] 是启动态语言标签解析的单一事实入口：
 * - 系统 per-app locale 状态优先（用户在系统「语言偏好」面板设置过）；
 * - 否则 App 内 preference（[LocaleManager.TAG_SYSTEM] = 未选择，跟随系统）。
 */
class LocaleManagerTest {

    @Test
    fun `系统状态优先于 App preference`() {
        assertEquals("en", LocaleManager.resolveTag(prefTag = "zh", systemTag = "en"))
        assertEquals("zh", LocaleManager.resolveTag(prefTag = "en", systemTag = "zh"))
    }

    @Test
    fun `系统未设置时回落 App preference`() {
        assertEquals("zh", LocaleManager.resolveTag(prefTag = "zh", systemTag = null))
        assertEquals("en", LocaleManager.resolveTag(prefTag = "en", systemTag = null))
    }

    @Test
    fun `双空时跟随系统`() {
        assertEquals("system", LocaleManager.resolveTag(prefTag = "system", systemTag = null))
    }

    @Test
    fun `系统设置与 App 未选择不冲突`() {
        assertEquals("en", LocaleManager.resolveTag(prefTag = "system", systemTag = "en"))
    }

    @Test
    fun `语言标签常量不得回退为魔法字符串`() {
        assertEquals("system", LocaleManager.TAG_SYSTEM)
        assertEquals("en", LocaleManager.TAG_EN)
        assertEquals("zh", LocaleManager.TAG_ZH)
    }
}