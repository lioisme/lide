package com.delua.engine

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class LuaRunner(private val context: Context? = null) {

    private val globals: Globals = JsePlatform.standardGlobals()

    fun runScript(projectDir: String, fileName: String = "main.lua"): ExecutionResult {
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        val oldStdout = globals.STDOUT
        globals.STDOUT = ps

        return try {
            val filePath = File(projectDir, fileName).absolutePath
            val chunk = globals.loadfile(filePath)
            val result = chunk.call()
            globals.STDOUT = oldStdout
            ps.close()
            val stdout = baos.toString("UTF-8").trimEnd()
            val ret = if (result.isnil()) "" else result.tojstring()
            ExecutionResult(
                success = true,
                output = stdout,
                returnValue = ret,
                fullText = if (ret.isEmpty()) stdout else "$stdout\n> $ret"
            )
        } catch (e: LuaError) {
            globals.STDOUT = oldStdout
            ps.close()
            Log.e("LuaRunner", "LuaError: ${e.message}")
            ExecutionResult(success = false, fullText = baos.toString("UTF-8").trimEnd() + "\nError: ${e.message}")
        } catch (e: Exception) {
            globals.STDOUT = oldStdout
            ps.close()
            Log.e("LuaRunner", "Error: ${e.message}")
            ExecutionResult(success = false, fullText = baos.toString("UTF-8").trimEnd() + "\nError: ${e.message}")
        }
    }

    fun checkSyntax(source: String): List<SyntaxError> {
        return try {
            globals.load(source, "check.lua")
            emptyList()
        } catch (e: LuaError) {
            val line = extractLine(e.message)
            listOf(SyntaxError(line = line, message = e.message ?: "syntax error"))
        }
    }

    fun loadDex(dexPath: String): Boolean {
        val ctx = context ?: return false
        return try {
            val dexFile = File(dexPath)
            if (!dexFile.exists()) return false
            val optimizedDir = File(ctx.codeCacheDir, "dex_opt")
            optimizedDir.mkdirs()
            val loader = DexClassLoader(
                dexPath,
                optimizedDir.absolutePath,
                null,
                ctx.classLoader
            )
            globals.set("DexClassLoader", CoerceJavaToLua.coerce(loader))
            true
        } catch (e: Exception) {
            Log.e("LuaRunner", "loadDex failed: ${e.message}")
            false
        }
    }

    fun registerFunction(name: String, fn: LuaValue) {
        globals.set(name, fn)
    }

    fun resetOutput() {
        globals.STDOUT = System.out
    }

    private fun extractLine(message: String?): Int {
        if (message == null) return 0
        val match = Regex("line (\\d+)").find(message)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun destroy() {
    }
}

data class ExecutionResult(
    val success: Boolean,
    val output: String = "",
    val returnValue: String = "",
    val fullText: String = ""
)

data class SyntaxError(val line: Int, val message: String)
