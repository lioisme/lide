package com.lioisme.lide.apibrowser

/**
 * 分类器：按包名前缀 + 反射验证自动归类，无人工标注。
 * 判定顺序不可交换（R 资源类优先于前缀分流；控件包内需反射验证 View 子类）。
 *
 * 两阶段契约（AGENTS.md / API_BROWSER 索引快照契约）：
 * - [categorizeBase]：纯前缀规则，零反射，供 `buildBase()` 非反射阶段使用。
 *   widget/view 包条目暂归 [ApiCategory.FRAMEWORK]（占位），待 [categorize] 反射复核。
 * - [categorize]：在 [categorizeBase] 之上对 widget/view 包条目做 View 子类反射验证
 *   （[ApiIndex.isViewSubclass]，带缓存），细化出 [ApiCategory.COMMON_WIDGETS]；
 *   只允许在 `completeBuild()` 等反射阶段调用。
 */
object ApiCategoryClassifier {
    /** pro 自定义控件（伪装在 android.* 下的白名单前缀，反射验证在框架中不存在）。 */
    private val androluaAndroidPrefixes =
        listOf(
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

    private val androluaPackagePrefixes =
        listOf(
            "com.androlua.",
            "com.luajava.",
            "com.nirenr.",
            "com.android.cglib.",
        )

    /** 常用控件候选包：View 子类反射验证通过才归入（ArrayAdapter 等非 View 自动归框架）。 */
    private val widgetPackages =
        listOf(
            "android.widget.",
            "android.view.",
        )

    private val rResourceRegex = Regex("""(^|\.)R($|\$)""")

    /** widget/view 包名判定（纯前缀，非反射）。 */
    fun isWidgetPackage(name: String): Boolean = widgetPackages.any { name.startsWith(it) }

    /**
     * 非反射分类（buildBase 阶段，契约见类注释）：widget/view 包条目统一暂归 FRAMEWORK，
     * 不触发 [ApiIndex.isViewSubclass] 反射；反射细化由 [categorize] / completeBuild 完成。
     */
    fun categorizeBase(name: String): ApiCategory {
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

        // widget/view 包：buildBase 阶段不反射，暂归 FRAMEWORK（占位）
        if (isWidgetPackage(name)) return ApiCategory.FRAMEWORK

        // Android 框架
        if (name.startsWith("android.")) return ApiCategory.FRAMEWORK

        return ApiCategory.OTHER
    }

    /**
     * 完整分类（反射阶段专用，契约见类注释）：在 [categorizeBase] 基础上对 widget/view 包
     * 条目做 View 子类反射验证（带缓存），细化 COMMON_WIDGETS。禁止在 buildBase 调用。
     */
    fun categorize(name: String): ApiCategory {
        val base = categorizeBase(name)
        if (base == ApiCategory.FRAMEWORK && isWidgetPackage(name)) {
            if (ApiIndex.isViewSubclass(name)) return ApiCategory.COMMON_WIDGETS
        }
        return base
    }
}
