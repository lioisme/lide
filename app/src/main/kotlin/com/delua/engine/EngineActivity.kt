package com.delua.engine

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import java.io.File
import java.util.ArrayList
import java.util.HashMap

class EngineActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EngineActivity"
        private const val RESULT_FILE = "engine_result.json"
        private const val OUTPUT_FILE = "engine_output.txt"
    }

    private var projectDir: String = ""
    private var mainFile: String = "main.lua"
    private var startTime: Long = 0L
    private var contentViewSet = false
    private var scriptError: String? = null

    private var luaRef: LuaStateRef? = null
    private var engineDexLoader: ClassLoader? = null
    private val outputBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()

        projectDir = intent.getStringExtra("projectDir") ?: return finish()
        mainFile = intent.getStringExtra("mainFile") ?: "main.lua"

        try {
            System.loadLibrary("luajava")
            Log.d(TAG, "Loaded libluajava.so")

            val libsDir = File(cacheDir, "libs").also { it.mkdirs() }
            copyNativeLib("gl", libsDir)

            // Android 14+ (API 34+) blocks writable dex files; use InMemoryDexClassLoader + reflection
            // to grant the engine classloader access to app-native libraries.
            // Pre-API 34: use DexClassLoader normally.
            val dexBytes = assets.open("engine/engine.dex").use { it.readBytes() }
            if (Build.VERSION.SDK_INT >= 34) {
                engineDexLoader = InMemoryDexClassLoader(
                    java.nio.ByteBuffer.wrap(dexBytes),
                    classLoader.parent
                )
                addNativeLibPath(engineDexLoader!!, applicationInfo.nativeLibraryDir)
            } else {
                val dexFile = prepareDexFile()
                val odexDir = File(codeCacheDir, "odex").also { it.mkdirs() }
                engineDexLoader = DexClassLoader(
                    dexFile.absolutePath,
                    odexDir.absolutePath,
                    applicationInfo.nativeLibraryDir,
                    classLoader.parent
                )
            }

            val luaStateClass = engineDexLoader!!.loadClass("com.luajava.LuaState")
            val constructor = luaStateClass.getDeclaredConstructor()
            constructor.isAccessible = true
            val luaState = constructor.newInstance()

            luaRef = LuaStateRef(luaState, luaStateClass)
            luaRef!!.trySetClassLoader(engineDexLoader!!)
            luaRef!!.openLibs()
            Log.d(TAG, "Lua VM created and libraries opened")

            luaRef!!.pushJavaObject(this)
            luaRef!!.setGlobal("activity")
            luaRef!!.pushJavaObject(this)
            luaRef!!.setGlobal("this")

            val luaDir = File(cacheDir, "lua")
            val projectPath = buildPackagePath(projectDir, luaDir.absolutePath)
            luaRef!!.setPackagePath(projectPath)

            val cpath = "$libsDir/lib?.so;$libsDir/lib?/init.so;"
            luaRef!!.setPackageCPath(cpath)

            val initFile = File(projectDir, "init.lua")
            if (initFile.exists()) {
                luaRef!!.doFile(initFile.absolutePath)
            }

            luaRef!!.doFile(File(projectDir, mainFile).absolutePath)

            Log.d(TAG, "Script completed successfully")

            if (!contentViewSet) {
                finish()
            }
        } catch (e: Throwable) {
            scriptError = "${e::class.java.simpleName}: ${e.message}"
            Log.e(TAG, "Script failed", e)
            writeResultFiles()
            finish()
        }
    }

    override fun setContentView(view: View?) {
        if (view != null) super.setContentView(view)
        contentViewSet = true
    }

    override fun onDestroy() {
        luaRef?.close()
        luaRef = null
        writeResultFiles()
        super.onDestroy()
    }

    private fun copyNativeLib(name: String, destDir: File) {
        val src = File(applicationInfo.nativeLibraryDir, "lib$name.so")
        if (!src.exists()) return
        val dest = File(destDir, "lib$name.so")
        if (dest.exists()) return
        try {
            src.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            Log.d(TAG, "Copied lib$name.so to ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy lib$name.so", e)
        }
    }

    private fun prepareDexFile(): File {
        val dexDir = File(filesDir, "dex").also { it.mkdirs() }
        val dexFile = File(dexDir, "engine.dex")
        if (dexFile.exists() && dexFile.length() > 0L) return dexFile
        assets.open("engine/engine.dex").use { input ->
            dexFile.outputStream().use { output -> input.copyTo(output) }
        }
        return dexFile
    }

    private fun addNativeLibPath(loader: ClassLoader, nativeLibDir: String) {
        try {
            val pathList = Class.forName("dalvik.system.BaseDexClassLoader")
                .getDeclaredField("pathList").apply { isAccessible = true }
                .get(loader) ?: return
            val plClass = pathList.javaClass

            // API 28+ uses pre-built NativeLibraryElement[]
            val elementsField = try {
                plClass.getDeclaredField("nativeLibraryPathElements").also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) { null }
            if (elementsField != null) {
                val elements = elementsField.get(pathList) as Array<*>
                val appDir = File(nativeLibDir)
                val hasAppDir = elements.any { el ->
                    if (el == null) return@any false
                    try {
                        el.javaClass.getDeclaredField("dir").apply { isAccessible = true }.get(el) == appDir
                    } catch (_: Exception) { false }
                }
                if (!hasAppDir) {
                    val elClass = elements.javaClass.componentType
                    val elCtor = elClass.getDeclaredConstructor(File::class.java).apply { isAccessible = true }
                    val appElement = elCtor.newInstance(appDir)
                    val newArray = java.lang.reflect.Array.newInstance(elClass, elements.size + 1)
                    System.arraycopy(elements, 0, newArray, 0, elements.size)
                    java.lang.reflect.Array.set(newArray, elements.size, appElement)
                    elementsField.set(pathList, newArray)
                }
                return
            }

            // API 24-27: nativeLibraryDirectories is File[]
            val dirsField = plClass.getDeclaredField("nativeLibraryDirectories").apply { isAccessible = true }
            val dirs = dirsField.get(pathList) as Array<File>
            val appDir = File(nativeLibDir)
            if (dirs.none { it.absolutePath == appDir.absolutePath }) {
                dirsField.set(pathList, dirs + appDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "addNativeLibPath failed (non-fatal): ${e.message}")
        }
    }

    fun getClassLoaders(): ArrayList<ClassLoader> {
        val list = ArrayList<ClassLoader>()
        list.add(classLoader)
        engineDexLoader?.let { list.add(it) }
        return list
    }

    fun getLibrarys(): HashMap<String, String> {
        val map = HashMap<String, String>()
        val libFile = File(cacheDir, "libs/libgl.so")
        if (libFile.exists()) {
            map["gl"] = libFile.absolutePath
        }
        return map
    }

    fun sendMsg(msg: String) {
        outputBuffer.append(msg).append("\n")
    }

    private fun writeResultFiles() {
        if (scriptError == null && luaRef != null) return
        if (scriptError == null && contentViewSet) return
        val duration = System.currentTimeMillis() - startTime
        val json = buildString {
            append("{\"success\":${scriptError == null}")
            append(",\"errorMessage\":${scriptError?.let { "\"${escapeJson(it)}\"" } ?: "null"}")
            append(",\"hasContentView\":$contentViewSet")
            append(",\"durationMs\":$duration")
            append(",\"timestamp\":\"${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())}\"")
            append("}")
        }
        try {
            File(cacheDir, RESULT_FILE).writeText(json)
            if (outputBuffer.isNotEmpty()) {
                File(cacheDir, OUTPUT_FILE).writeText(outputBuffer.toString())
            }
            Log.d(TAG, "Result files written: success=${scriptError == null}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write result files", e)
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun buildPackagePath(projectDir: String, luaDir: String): String {
        return buildString {
            append("$projectDir/?.lua;$projectDir/?/init.lua;")
            append("$luaDir/?.lua;$luaDir/?/init.lua;")
            append("./?.lua;./?/init.lua;")
        }
    }

    private class LuaStateRef(
        private val state: Any,
        private val cls: Class<*>
    ) {
        private val openLibs = cls.getMethod("openLibs")
        private val pushJavaObject = cls.getMethod("pushJavaObject", Any::class.java)
        private val setGlobal = cls.getMethod("setGlobal", String::class.java)
        private val doFile = cls.getMethod("LdoFile", String::class.java)
        private val doString = cls.getMethod("LdoString", String::class.java)
        private val getGlobal = cls.getMethod("getGlobal", String::class.java)
        private val pushString = cls.getMethod("pushString", String::class.java)
        private val rawGet = cls.getMethod("rawGet", Int::class.java)
        private val pop = cls.getMethod("pop", Int::class.java)
        private val getTop = cls.getMethod("getTop")
        private val setField = cls.getMethod("setField", Int::class.java, String::class.java)
        private val pushGlobalTable = cls.getMethod("pushGlobalTable")
        private val close = cls.getMethod("close")
        private val toString = cls.getMethod("toString", Int::class.java)
        private val createTable = cls.getMethod("createTable", Int::class.java, Int::class.java)

        fun openLibs() = openLibs.invoke(state)
        fun pushJavaObject(obj: Any?) = pushJavaObject.invoke(state, obj)
        fun setGlobal(name: String) = setGlobal.invoke(state, name)
        fun doFile(path: String): Int = doFile.invoke(state, path) as Int
        fun doString(script: String): Int = doString.invoke(state, script) as Int
        fun pop(n: Int) = pop.invoke(state, n)
        fun getTop(): Int = getTop.invoke(state) as Int
        fun close() = close.invoke(state)
        fun toString(idx: Int): String = toString.invoke(state, idx) as String
        fun pushGlobalTable() = pushGlobalTable.invoke(state)
        fun setGlobalString(name: String, value: String) {
            pushString.invoke(state, value)
            setGlobal.invoke(state, name)
        }

        fun setPackagePath(path: String) {
            pushGlobalTable()
            pushString.invoke(state, "package")
            rawGet.invoke(state, -2)
            pushString.invoke(state, "path")
            pushString.invoke(state, path)
            setField.invoke(state, -3, "path")
            pop.invoke(state, 1)
        }

        fun setPackageCPath(cpath: String) {
            pushGlobalTable()
            pushString.invoke(state, "package")
            rawGet.invoke(state, -2)
            pushString.invoke(state, "cpath")
            pushString.invoke(state, cpath)
            setField.invoke(state, -3, "cpath")
            pop.invoke(state, 1)
        }

        fun trySetClassLoader(loader: ClassLoader) {
            try {
                val m = cls.getMethod("setClassLoader", ClassLoader::class.java)
                m.invoke(state, loader)
            } catch (_: NoSuchMethodException) {
                Log.d(TAG, "LuaState.setClassLoader() not available (engine may set it internally)")
            }
        }
    }
}
