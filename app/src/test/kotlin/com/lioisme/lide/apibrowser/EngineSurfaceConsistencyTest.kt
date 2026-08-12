package com.lioisme.lide.apibrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 引擎面类清单单一事实源一致性校验（技术债审计 D2 收敛，2026-08-09）。
 *
 * [engine-surface.txt]（app 模块根，2026-08-09 起）是引擎面类唯一事实源：
 * - app/build.gradle.kts 读表生成 engineSurfaceDescriptors（checkEngineDexMerge 断言对象）；
 * - proguard-rules.pro「引擎 keep 面」为本清单的 R8 keep 覆盖；
 * - ApiCategoryClassifier.androluaAndroidPrefixes 条目必须登记于本清单。
 *
 * 本测试守护三件事（防止三处手工维护再漂移）：
 * 1. 分类器每一条精确类名前缀均登记于清单（清单 = 分类器的超集）；
 * 2. 清单全部条目经 [ApiCategoryClassifier.categorizeBase] 归为 ANDROLUA
 *    （android.content.FileProvider 白名单例外：manifest 注册类，前缀归 FRAMEWORK 属预期）；
 * 3. 清单全部条目被 proguard-rules.pro 某条 keep 规则覆盖（包通配或单列）。
 *
 * 注意：从源码树读取清单与 keep 规则（Gradle 单测工作目录 = 模块目录），不依赖 Android 运行时。
 */
class EngineSurfaceConsistencyTest {
    private val surfaceFile = File("engine-surface.txt")
    private val proguardFile = File("proguard-rules.pro")

    private fun surfaceNames(): Set<String> {
        assertTrue(
            "engine-surface.txt 缺失（工作目录应为 :app 模块目录，实际 cwd=${File("").absolutePath}）",
            surfaceFile.isFile,
        )
        return surfaceFile
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    private fun proguardText(): String {
        assertTrue("proguard-rules.pro 缺失", proguardFile.isFile)
        return proguardFile.readText()
    }

    @Test
    fun `清单文件可解析且条目数达标`() {
        val names = surfaceNames()
        assertTrue("引擎面类清单条目过少: ${names.size}", names.size >= 30)
        assertTrue("清单必须覆盖全部自定义控件伪装包", names.any { it.startsWith("android.widget.") })
        assertTrue("清单必须含引擎核心 LuaActivity", "com.androlua.LuaActivity" in names)
        assertTrue("清单必须含 LuaApplication", "com.androlua.LuaApplication" in names)
        assertTrue("清单必须含 FileProvider 特殊登记", "android.content.FileProvider" in names)
        val badFormat = names.filterNot { it.matches(Regex("[a-zA-Z0-9_.$]+")) && it.contains('.') }
        assertEquals("清单格式错误（必须为点分全限定类名）: $badFormat", emptyList<String>(), badFormat)
    }

    @Test
    fun `分类器精确类名前缀全部登记于清单`() {
        val names = surfaceNames()
        val classifierOnly = ApiCategoryClassifier.androluaAndroidPrefixes.filterNot { it in names }
        assertEquals(
            "ApiCategoryClassifier 条目未登记于 engine-surface.txt（分类器 → 清单单向闭环）: $classifierOnly",
            emptyList<String>(),
            classifierOnly,
        )
    }

    @Test
    fun `清单全部条目被分类器归为 ANDROLUA`() {
        val misclassified =
            surfaceNames()
                .filterNot { it == "android.content.FileProvider" }
                .filter { ApiCategoryClassifier.categorizeBase(it) != ApiCategory.ANDROLUA }
        assertEquals(
            "engine-surface.txt 条目未归类 ANDROLUA（清单 → 分类器单向闭环）: $misclassified",
            emptyList<String>(),
            misclassified,
        )
    }

    @Test
    fun `清单全部条目被 proguard keep 规则覆盖`() {
        val rules = proguardText()
        val uncovered =
            surfaceNames().filterNot { name ->
                val keepSingle = "-keep class $name { *; }" in rules
                val parts = name.split('.')
                val keepByPackage =
                    (1 until parts.size).any { depth ->
                        "-keep class ${parts.take(depth).joinToString(".")}.** { *; }" in rules
                    }
                keepSingle || keepByPackage
            }
        assertEquals(
            "engine-surface.txt 条目缺少 R8 keep 覆盖（清单 → keep 规则闭环）: $uncovered",
            emptyList<String>(),
            uncovered,
        )
    }
}
