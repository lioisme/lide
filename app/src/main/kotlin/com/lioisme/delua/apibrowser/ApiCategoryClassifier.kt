package com.lioisme.delua.apibrowser

/**
 * 分类器：按包名前缀 + 反射验证自动归类，无人工标注。
 * 判定顺序不可交换（R 资源类优先于前缀分流；控件包内需反射验证 View 子类）。
 */
object ApiCategoryClassifier {

    /** pro 自定义控件（伪装在 android.* 下的白名单前缀，反射验证在框架中不存在）。 */
    private val androluaAndroidPrefixes = listOf(
        "android.app.FloatWindow",
        "android.widget.ArrayExpandableListAdapter",
        "android.widget.ArrayListAdapter",
        "android.widget.ArrayPageAdapter",
        "android.widget.BasePageAdapter",
        "android.widget.PageAdapter",
        "android.widget.ExListView",
        "android.widget.FloatButton",
        "android.widget.HorizontalListView",
        "android.widget.PageLayout",
        "android.widget.PageView",
        "android.widget.PullingLayout",
        "android.widget.RippleLayout",
        "android.widget.SlidingLayout",
        "android.widget.ToolBar",
        "android.widget.CircleImageView",
        "android.widget.CardView",
        "android.widget.DrawerLayout",
        "android.widget.RoundRectDrawable",
        "android.widget.RippleHelper",
        "android.widget.ViewDragHelper",
    )

    private val androluaPackagePrefixes = listOf(
        "com.androlua.", "com.luajava.", "com.nirenr.", "com.android.cglib.",
    )

    /** 常用控件候选包：View 子类反射验证通过才归入（ArrayAdapter 等非 View 自动归框架）。 */
    private val widgetPackages = listOf(
        "android.widget.", "android.view.",
    )

    private val rResourceRegex = Regex("""(^|\.)R($|\$)""")

    fun categorize(name: String): ApiCategory {
        // R 资源类（R / xxx.R / xxx.R$id）
        if (rResourceRegex.containsMatchIn(name)) return ApiCategory.R_RESOURCES

        // 谷歌质感库
        if (name.startsWith("com.google.android.material.")) return ApiCategory.MATERIAL

        // AndroidX
        if (name.startsWith("androidx.") || name.startsWith("android.support.")) {
            return ApiCategory.ANDROIDX
        }

        // AndroLua 扩展（含 pro 自定义控件白名单）
        if (androluaPackagePrefixes.any { name.startsWith(it) } ||
            androluaAndroidPrefixes.any { name.startsWith(it) }
        ) {
            return ApiCategory.ANDROLUA
        }

        // Java 标准库
        if (name.startsWith("java.") || name.startsWith("javax.")) return ApiCategory.JAVA

        // 常用控件：widget/view 包内反射验证 View 子类（惰性缓存）
        if (widgetPackages.any { name.startsWith(it) }) {
            if (ApiIndex.isViewSubclass(name)) return ApiCategory.COMMON_WIDGETS
            return ApiCategory.FRAMEWORK
        }

        // Android 框架
        if (name.startsWith("android.")) return ApiCategory.FRAMEWORK

        return ApiCategory.OTHER
    }
}
