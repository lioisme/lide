package com.lioisme.lide.build

import com.luajava.LuaState
import com.luajava.LuaStateFactory

/** Lua 编译失败：携带可定位的行号（解析失败时行号为 null）。 */
class LuaCompileException(val line: Int?, message: String) : Exception(message)

/**
 * Lua 源码编译（打包链路第一步，宿主进程内）。
 *
 * 复用引擎同款 LuaState（LuaStateFactory + openLibs）执行 LloadBuffer + dump，
 * 与宿主运行同库同 ABI（x64/arm64 均为 little-endian、同一 Lua 版本），
 * 产物字节码可直接被 LloadBuffer/LloadFile 加载，跨 ABI 可移植性视为等价。
 * 失败抛出 [LuaCompileException]，错误文本已去除 chunk 名前缀。
 */
object LuaCompiler {

    private val CHUNK_LINE = Regex("^[@=]?[^:]*:(\\d+):")
    private val CHUNK_PREFIX = Regex("^[@=]?[^:]*:\\d+:\\s*")
    private val UTF8_BOM = "\uFEFF"

    /** 编译 Lua 源码为字节码；[chunkName] 仅用于错误信息定位。 */
    fun compile(source: String, chunkName: String = "main.lua"): ByteArray {
        // 去除 UTF-8 BOM（Windows 记事本等写出的 .lua 常带），否则 BOM 会被词法器并入
        // 首个标识符（"\uFEFFprint" ≠ "print"），运行时按 nil 全局调用报错。
        val cleaned = if (source.startsWith(UTF8_BOM)) source.removePrefix(UTF8_BOM) else source
        val L = LuaStateFactory.newLuaState()
        try {
            L.openLibs()
            val rc = L.LloadBuffer(cleaned.toByteArray(Charsets.UTF_8), "@$chunkName")
            if (rc != 0) {
                val raw = L.toString(-1) ?: "compile failed (rc=$rc)"
                val line = parseErrorLine(raw)
                val msg = raw.replaceFirst(CHUNK_PREFIX, "").trim()
                throw LuaCompileException(line, msg)
            }
            val bytes = L.dump(-1)
            if (bytes == null || bytes.isEmpty()) {
                throw LuaCompileException(null, "dump failed")
            }
            return bytes
        } finally {
            L.close()
        }
    }

    /** 从 "name:12: message" 提取行号，无行号时返回 null。 */
    fun parseErrorLine(raw: String): Int? {
        return CHUNK_LINE.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    }
}
