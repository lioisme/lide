package com.lioisme.lide.androlua

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * .alp 目标名解析（WP-019 测试骨架；WP-015 同名确认的基础逻辑）。
 * （插件容器类型判定已随插件功能移除。）
 */
class AlpNamingTest {
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
