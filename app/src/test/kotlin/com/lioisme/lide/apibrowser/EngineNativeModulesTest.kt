package com.lioisme.lide.apibrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 引擎内置原生模块清单一致性校验（技术债审计 R1 收敛，2026-08-09）。
 *
 * [ApiIndex.builtinNativeModules]（API 浏览器「原生模块」展示）必须与引擎真实构建产物一致：
 * 单一事实源 = app/src/main/cpp 下各模块 Android.mk 的 LOCAL_MODULE。
 * 剔除规则（非可 require 模块）：
 * - `lua`：Lua 核心库（liblua.so，非模块）；
 * - `live2d`：独立演示模块（源码无 luaopen，非 Lua require 模块）。
 *
 * 双向校验（防止历史上两种漂移再发生）：
 * - 引擎每个可 require 模块必须登记于清单（防漏登：如 luajava / lfs / lsqlite3）；
 * - 清单每个条目必须存在于引擎源码（防幽灵登记：如 ftp / http / import / mime / smtp）。
 */
class EngineNativeModulesTest {

    private val cppDir = File("src/main/cpp")

    private fun engineModules(): Set<String> {
        assertTrue(
            "cpp 目录缺失（单测 cwd 应为 :app 模块目录，实际 ${File("").absolutePath}）",
            cppDir.isDirectory,
        )
        val dirs = cppDir.listFiles { f -> f.isDirectory && f.name != "lua" && f.name != "live2d" } ?: error("cpp 目录不可读")
        return dirs.mapNotNull { dir ->
            val mk = File(dir, "Android.mk")
            if (!mk.isFile) return@mapNotNull null
            mk.readLines()
                .firstOrNull { it.contains("LOCAL_MODULE") }
                ?.substringAfter("LOCAL_MODULE")
                ?.substringAfter(":=")
                ?.trim()
                // LOCAL_MODULE 带 lib 前缀时（如 zip → libzip）产物仍为 libzip.so，加载名去前缀（zip）。
                ?.removePrefix("lib")
        }.toSet()
    }

    @Test
    fun `引擎可 require 模块全部登记于浏览器清单`() {
        val engine = engineModules()
        assertTrue("引擎可 require 模块数异常: $engine", engine.size >= 14)
        assertTrue("引擎必须含 luajava 模块", "luajava" in engine)
        assertTrue("引擎必须含 lfs 模块", "lfs" in engine)
        assertTrue("引擎必须含 lsqlite3 模块", "lsqlite3" in engine)
        val missing = engine - ApiIndex.builtinNativeModules.toSet()
        assertEquals("引擎构建的模块未登记于 ApiIndex（漏登）: $missing", emptySet<String>(), missing)
    }

    @Test
    fun `浏览器清单不存在幽灵模块`() {
        val engine = engineModules()
        val ghosts = ApiIndex.builtinNativeModules.toSet() - engine
        assertEquals(
            "ApiIndex 登记了引擎未构建的模块（幽灵登记，如 ftp/http/import/mime/smtp）: $ghosts",
            emptySet<String>(),
            ghosts,
        )
    }

    @Test
    fun `浏览器清单无重复登记`() {
        val list = ApiIndex.builtinNativeModules
        assertEquals("清单存在重复登记", list.toSet().size, list.size)
    }
}
