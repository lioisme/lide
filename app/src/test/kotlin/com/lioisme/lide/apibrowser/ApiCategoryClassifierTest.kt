package com.lioisme.lide.apibrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ApiCategoryClassifier 分类契约测试（技术债审计 R6 收敛，2026-08-09）。
 *
 * 覆盖 [ApiCategoryClassifier.categorizeBase] 全部 8 个分类分支的典型条目
 * （零反射路径，可在 JVM 单测运行）+ 控件包判定：
 * - R 资源类优先短路（R / xxx.R / R\$id）；
 * - material / androidx / androlua（含 pro 自定义控件白名单）/ java / widget-view /
 *   android 框架 / 其余兜底。
 * 反射细化路径（categorize → COMMON_WIDGETS）依赖设备运行时 View 类，不在单测覆盖
 * （EngineSurfaceConsistencyTest 已守护清单类全部归 ANDROLUA 的静态闭环）。
 */
class ApiCategoryClassifierTest {
    @Test
    fun `R 资源类优先短路`() {
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorizeBase("android.R"))
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorizeBase("com.androlua.R"))
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorizeBase("com.lioisme.lide.R"))
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorizeBase("com.lioisme.lide.R\$string"))
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorizeBase("android.widget.R\$id"))
    }

    @Test
    fun `material 与 androidx 分类`() {
        assertEquals(ApiCategory.MATERIAL, ApiCategoryClassifier.categorizeBase("com.google.android.material.button.MaterialButton"))
        assertEquals(ApiCategory.ANDROIDX, ApiCategoryClassifier.categorizeBase("androidx.collection.ArraySet"))
        assertEquals(ApiCategory.ANDROIDX, ApiCategoryClassifier.categorizeBase("androidx.compose.ui.Modifier"))
        assertEquals(ApiCategory.ANDROIDX, ApiCategoryClassifier.categorizeBase("android.support.v4.app.Fragment"))
    }

    @Test
    fun `androlua 引擎与自定义控件白名单分类`() {
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("com.androlua.LuaActivity"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("com.luajava.LuaState"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("com.nirenr.screencapture.ScreenShot"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("com.android.cglib.proxy.Enhancer"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("android.app.FloatWindow"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("android.widget.RoundRectDrawable"))
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorizeBase("android.widget.PageAdapter"))
    }

    @Test
    fun `java 标准库与 android 框架分类`() {
        assertEquals(ApiCategory.JAVA, ApiCategoryClassifier.categorizeBase("java.lang.String"))
        assertEquals(ApiCategory.JAVA, ApiCategoryClassifier.categorizeBase("javax.swing.JFrame"))
        assertEquals(ApiCategory.FRAMEWORK, ApiCategoryClassifier.categorizeBase("android.app.Activity"))
        assertEquals(ApiCategory.FRAMEWORK, ApiCategoryClassifier.categorizeBase("android.view.View"))
    }

    @Test
    fun `widget 包在 base 阶段暂归 FRAMEWORK 占位`() {
        assertTrue(ApiCategoryClassifier.isWidgetPackage("android.widget.ListView"))
        assertTrue(ApiCategoryClassifier.isWidgetPackage("android.view.View"))
        assertFalse(ApiCategoryClassifier.isWidgetPackage("android.widgetx.Widget"))
        assertFalse(ApiCategoryClassifier.isWidgetPackage("com.android.widget.Widget"))
        assertEquals(ApiCategory.FRAMEWORK, ApiCategoryClassifier.categorizeBase("android.widget.ListView"))
    }

    @Test
    fun `其余命名空间兜底 OTHER`() {
        assertEquals(ApiCategory.OTHER, ApiCategoryClassifier.categorizeBase("com.lioisme.lide.ui.MainActivity"))
        assertEquals(ApiCategory.OTHER, ApiCategoryClassifier.categorizeBase("kotlin.Unit"))
        assertEquals(ApiCategory.OTHER, ApiCategoryClassifier.categorizeBase("org.json.JSONObject"))
    }

    @Test
    fun `categorize 对非 widget 条目与 base 一致`() {
        assertEquals(ApiCategory.ANDROLUA, ApiCategoryClassifier.categorize("com.androlua.LuaActivity"))
        assertEquals(ApiCategory.JAVA, ApiCategoryClassifier.categorize("java.lang.String"))
        assertEquals(ApiCategory.MATERIAL, ApiCategoryClassifier.categorize("com.google.android.material.button.MaterialButton"))
        assertEquals(ApiCategory.R_RESOURCES, ApiCategoryClassifier.categorize("android.R"))
    }
}
