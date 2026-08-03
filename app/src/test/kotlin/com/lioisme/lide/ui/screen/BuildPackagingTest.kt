package com.lioisme.lide.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 打包装配（需求②）：工程文件收集 + 宿主引擎库按需保留。 */
class BuildPackagingTest {

    private fun tempDir(name: String): File =
        Files.createTempDirectory(name).toFile().apply { deleteOnExit() }

    @Test
    fun collectProjectFiles_keepsWholeDir_skipsHidden_usingReported() {
        val root = tempDir("proj")
        File(root, "main.lua").writeText("print(1)")
        File(root, "init.lua").writeText("appname = \"t\"")
        File(root, "module.luac").writeBytes(byteArrayOf(0x1B))
        File(root, "cache.odex").writeBytes(byteArrayOf(1))
        File(root, ".using").writeText("require \"json\"\n")
        File(root, ".git").mkdir()
        File(root, ".git/config").writeText("x")
        val res = File(root, "res").apply { mkdir() }
        File(res, "layout.aly").writeText("{LinearLayout;}")
        File(res, "img.png").writeBytes(byteArrayOf(1, 2, 3))
        val libs = File(root, "libs").apply { mkdir() }
        File(libs, "libfoo.so").writeBytes(byteArrayOf(9))

        val out = LinkedHashMap<String, ByteArray>()
        val using = ArrayList<File>()
        collectProjectFiles(root, "", out, using)

        assertTrue(out.containsKey("main.lua"))
        assertTrue(out.containsKey("init.lua"))
        assertTrue(out.containsKey("res/layout.aly"))
        assertTrue(out.containsKey("res/img.png"))
        assertTrue(out.containsKey("libs/libfoo.so"))
        assertFalse(out.keys.any { it.contains(".using") || it.startsWith(".") })
        // 中间产物（.luac/.odex/.vdex/.apk）不打包
        assertFalse(out.keys.any { it.endsWith(".luac") || it.endsWith(".odex") || it.endsWith(".vdex") || it.endsWith(".apk") })
        assertEquals(listOf(File(root, ".using")), using)
    }

    @Test
    fun engineLuaLibs_whitelistPlusRequireScan_filtersProjectOwnedAndUnknown() {
        val projectFiles = mapOf(
            "main.lua" to "require \"import\"\nrequire \"json\"\nimport \"socket.http\"\nimport \"console\"\n".toByteArray(),
            "json.lua" to "return {}".toByteArray(),
        )
        val host = setOf(
            "import.lua", "loadlayout.lua", "loadlayout2.lua", "loadlayout3.lua",
            "loadbitmap.lua", "loadmenu.lua", "permission.lua",
            "json.lua", "socket/http.lua", "console.lua", "notused.lua",
        )

        val libs = engineLuaLibs(projectFiles, emptyList(), host)

        // 白名单恒在
        assertTrue(libs.containsAll(listOf("import.lua", "loadlayout.lua", "loadbitmap.lua", "loadmenu.lua", "permission.lua")))
        // require/import 命中
        assertTrue("socket/http.lua 应在", libs.contains("socket/http.lua"))
        assertTrue("console.lua 应在", libs.contains("console.lua"))
        // 工程自带同名文件 → 工程优先，不进引擎库
        assertFalse("json.lua 工程自带，不应在引擎库", libs.contains("json.lua"))
        // 宿主没有的模块 / 未引用的库不进入
        assertFalse(libs.contains("notused.lua"))
        // 有序输出
        assertEquals(libs.sorted(), libs)
    }

    @Test
    fun engineLuaLibs_usingFileContentScanned() {
        val using = File.createTempFile("using", null).apply { writeText("import \"http\"\n") }
        val host = setOf("http.lua", "import.lua", "loadlayout.lua", "loadlayout2.lua", "loadlayout3.lua", "loadbitmap.lua", "loadmenu.lua", "permission.lua")

        val libs = engineLuaLibs(emptyMap(), listOf(using), host)

        assertTrue("http.lua 经 .using 应被保留", libs.contains("http.lua"))
    }
}
