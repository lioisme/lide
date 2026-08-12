package com.lioisme.lide.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ArscLookup 单测：合成最小真实布局 arsc（TestArscFactory，紧凑 entry 同构
 * aapt2 36 宿主产物）验证图标资源 ID / 文件路径解析（2026-08-09 P11）：
 * - UTF-8/UTF-16 双字符串池；
 * - 混淆 key "icon"（aapt2 归一化，真实产物实证）与历史 "icon.png"；
 * - 资源混淆路径（res/9T.png）；
 * - 垃圾输入安全降级 null。
 */
class ArscLookupTest {
    @Test
    fun `utf8 pool resolves icon id and path`() {
        val arsc = TestArscFactory.syntheticArsc(pkgId = 0x7f, iconPath = "res/drawable/icon.png")
        assertEquals(0x7f010000, ArscLookup.drawableIconId(arsc))
        assertEquals("res/drawable/icon.png", ArscLookup.drawableIconPath(arsc))
    }

    @Test
    fun `utf16 pool resolves the same`() {
        val arsc = TestArscFactory.syntheticArsc(utf8 = false)
        assertEquals(0x7f010000, ArscLookup.drawableIconId(arsc))
        assertEquals("res/drawable/icon.png", ArscLookup.drawableIconPath(arsc))
    }

    @Test
    fun `obfuscated host resolves short path with icon key`() {
        // release R8 资源混淆：key 归一为 "icon"，文件路径为短名（真实宿主实测）。
        val arsc = TestArscFactory.syntheticArsc(iconKey = "icon", iconPath = "res/9T.png")
        assertEquals(0x7f010000, ArscLookup.drawableIconId(arsc))
        assertEquals("res/9T.png", ArscLookup.drawableIconPath(arsc))
    }

    @Test
    fun `different package id and entry index computed from type and position`() {
        // pkgId 影响高 8 位；typeId = typeNames indexOf("drawable")+1 = 1（合成仅单 type）。
        val arsc = TestArscFactory.syntheticArsc(pkgId = 0x01)
        assertEquals(0x01010000, ArscLookup.drawableIconId(arsc))
    }

    @Test
    fun `no icon key returns null`() {
        val arsc = TestArscFactory.syntheticArsc(iconKey = "missing.png")
        assertNull(ArscLookup.drawableIconId(arsc))
        assertNull(ArscLookup.drawableIconPath(arsc))
    }

    @Test
    fun `garbage input safely degrades to null`() {
        assertNull(ArscLookup.drawableIconId(null))
        assertNull(ArscLookup.drawableIconId(ByteArray(0)))
        assertNull(ArscLookup.drawableIconId(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(ArscLookup.drawableIconId("not an arsc at all".toByteArray()))
        // 结构合法但首 chunk 越界
        assertNull(ArscLookup.drawableIconId(ByteArray(64)))
    }
}
