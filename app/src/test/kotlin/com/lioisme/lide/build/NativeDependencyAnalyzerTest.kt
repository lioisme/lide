package com.lioisme.lide.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 打包依赖分析（传递闭包 + .so 推导 + 缺失警告）。 */
class NativeDependencyAnalyzerTest {
    private val hostLua =
        mapOf(
            "import.lua" to "local luacontext = activity or service",
            "loadlayout.lua" to "-- loadlayout",
            "loadlayout2.lua" to "-- loadlayout2",
            "loadlayout3.lua" to "-- loadlayout3",
            "loadbitmap.lua" to "-- loadbitmap",
            "loadmenu.lua" to "-- loadmenu",
            "permission.lua" to "-- permission",
            "http.lua" to
                """
                local socket = require "socket"
                local url = require "socket.url"
                local ltn12 = require "ltn12"
                local mime = require "mime"
                local headers = require "socket.headers"
                """.trimIndent(),
            "socket.lua" to "require \"socket.core\"",
            "socket/headers.lua" to "-- headers",
            "socket/url.lua" to "-- url",
            "ltn12.lua" to "-- ltn12",
            "mime.lua" to "require \"mime.core\"",
            "json.lua" to "-- host json",
            "console.lua" to "-- console",
        )

    private fun analyze(
        sources: List<String>,
        host: Map<String, String> = hostLua,
        projectOwned: Set<String> = emptySet(),
        hostNativeLibs: Set<String> = emptySet(),
    ): DependencyAnalysis = NativeDependencyAnalyzer(host, projectOwned, hostNativeLibs).analyze(sources)

    @Test
    fun `transitive closure through host libs`() {
        // 工程只 require http；闭包应把 http 依赖的 socket/socket.url/ltn12/mime/socket.headers
        // 以及 socket → socket.core 依赖全量纳入（pro checklib 对 require 不递归，会断裂）。
        val r =
            analyze(
                listOf("import \"http\"\n"),
                hostNativeLibs = setOf("libsocket.so", "libmime.so"),
            )

        assertTrue(r.luaLibs.containsAll(listOf("http.lua", "socket.lua", "socket/url.lua", "ltn12.lua", "mime.lua", "socket/headers.lua")))
        assertTrue("http 依赖链完整", r.luaLibs.contains("socket.lua"))
        assertFalse("socket.core 是原生模块，非 lua 库", r.luaLibs.contains("socket/core.lua"))
        assertTrue("socket.core 由 libsocket.so 提供，不算缺失", r.missingModules.isEmpty())
    }

    @Test
    fun `native libs derived from first segment`() {
        val r = analyze(listOf("require \"socket.core\"\nimport \"cjson\"\nrequire \"mime.core\"\n"))

        assertTrue(r.nativeLibs.contains("libsocket.so"))
        assertTrue(r.nativeLibs.contains("libcjson.so"))
        assertTrue(r.nativeLibs.contains("libmime.so"))
        // 原生模块路径不进 luaLibs（宿主也没有这些路径）。
        assertFalse(r.luaLibs.contains("socket/core.lua"))
        assertFalse(r.luaLibs.contains("cjson.lua"))
    }

    @Test
    fun `closure native libs include host lib transitives`() {
        val r = analyze(listOf("import \"http\"\n"))
        // http → socket → socket.core / http → mime → mime.core
        assertTrue(r.nativeLibs.contains("libsocket.so"))
        assertTrue(r.nativeLibs.contains("libmime.so"))
    }

    @Test
    fun `project owned files override host`() {
        val r =
            analyze(
                listOf("require \"json\"\n"),
                projectOwned = setOf("json.lua"),
            )
        // 工程自带 json.lua → 不进宿主引擎库，也不推导 .so（工程文件即服务）。
        assertFalse(r.luaLibs.contains("json.lua"))
        assertFalse("工程自有模块不推导 .so", r.nativeLibs.contains("libjson.so"))
        assertTrue("工程自有模块不告警", r.missingModules.isEmpty())
    }

    @Test
    fun `missing modules reported`() {
        val r = analyze(listOf("require \"nonexistent\"\nimport \"gone.sub\"\n"))

        assertTrue(r.missingModules.contains("nonexistent.lua"))
        assertTrue(r.missingModules.contains("gone/sub.lua"))
        // 缺失模块只进警告，不进 luaLibs（白名单除外）。
        assertFalse(r.luaLibs.contains("nonexistent.lua"))
        assertEquals(NativeDependencyAnalyzer.ENGINE_LUA_WHITELIST, r.luaLibs)
    }

    @Test
    fun `whitelist always kept when host has it`() {
        val r = analyze(emptyList())
        assertEquals(
            NativeDependencyAnalyzer.ENGINE_LUA_WHITELIST,
            r.luaLibs,
        )
    }

    @Test
    fun `host self reference does not loop`() {
        // 宿主库自引用/循环引用（socket.lua ↔ a.lua）不得死循环。
        val host =
            hostLua +
                mapOf(
                    "a.lua" to "require \"b\"",
                    "b.lua" to "require \"a\"\nrequire \"socket\"",
                )
        val r =
            analyze(
                listOf("require \"a\"\n"),
                host = host,
                hostNativeLibs = setOf("libsocket.so"),
            )

        assertTrue(r.luaLibs.containsAll(listOf("a.lua", "b.lua", "socket.lua")))
        assertTrue(r.missingModules.isEmpty())
    }

    @Test
    fun `lua stdlib requires skipped`() {
        // 宿主库内部 require "table"（Lua 标准库）：不推导 .so、不告警缺失。
        val host =
            hostLua +
                mapOf("mylib.lua" to "require \"table\"\nrequire \"string\"\n")
        val r =
            analyze(
                listOf("require \"mylib\"\n"),
                host = host,
                hostNativeLibs = setOf("libsocket.so"),
            )

        assertTrue(r.luaLibs.contains("mylib.lua"))
        assertFalse(r.nativeLibs.any { it == "libtable.so" || it == "libstring.so" })
        assertFalse(r.missingModules.contains("table.lua"))
    }

    @Test
    fun `module name shapes`() {
        assertEquals("import.lua", NativeDependencyAnalyzer.modulePath("import", ""))
        assertEquals("socket/http.lua", NativeDependencyAnalyzer.modulePath("socket", "http"))
        assertEquals("libsocket.so", NativeDependencyAnalyzer.nativeLibFor("socket"))
        assertEquals("libzip.so", NativeDependencyAnalyzer.nativeLibFor("zip"))
        assertEquals("libgl.so", NativeDependencyAnalyzer.nativeLibFor("gl"))
    }
}
