package com.lioisme.lide.androlua

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * .alp 类型 / 目标名解析（WP-019 测试骨架；WP-015 同名确认的基础逻辑）。
 */
class AlpNamingTest {
    @Test
    fun `mode plugin 声明判定为插件`() {
        assertEquals(
            AlpManager.AlpKind.PLUGIN,
            AlpNaming.resolveKind("demo.alp", "\nappname = \"demo\"\nmode = \"plugin\"\n"),
        )
    }

    @Test
    fun `文件名 _plugin 后缀判定为插件`() {
        assertEquals(
            AlpManager.AlpKind.PLUGIN,
            AlpNaming.resolveKind("MyPlugin_plugin.alp", null),
        )
    }

    @Test
    fun `无插件标记判定为工程`() {
        assertEquals(
            AlpManager.AlpKind.PROJECT,
            AlpNaming.resolveKind("demo.alp", "\nappname = \"demo\"\n"),
        )
        assertEquals(
            AlpManager.AlpKind.PROJECT,
            AlpNaming.resolveKind("demo.alp", null),
        )
    }

    @Test
    fun `appname 优先于文件名`() {
        assertEquals("myapp", AlpNaming.resolveName("demo_plugin.alp", "appname = \"myapp\"\n"))
    }

    @Test
    fun `无 init 时按文件名截断`() {
        assertEquals("demo", AlpNaming.resolveName("demo.alp", null))
        assertEquals("foo", AlpNaming.resolveName("foo_bar.baz.alp", null))
        assertEquals("imported", AlpNaming.resolveName("..alp", null))
    }

    @Test
    fun `同名去重追加递增后缀`() {
        val exists = { name: String -> name in setOf("demo", "demo-1") }
        assertEquals("demo-2", AlpNaming.dedupeName("demo", exists))
        assertEquals("fresh", AlpNaming.dedupeName("fresh") { false })
    }
}
