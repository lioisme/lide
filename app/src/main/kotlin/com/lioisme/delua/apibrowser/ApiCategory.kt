package com.lioisme.delua.apibrowser

/**
 * API 浏览器分类。分类全部由 [ApiCategoryClassifier] 自动判定，无人工标注。
 */
enum class ApiCategory {
    /** 常用控件：android.widget/android.view 包中经反射验证的 [android.view.View] 子类。 */
    COMMON_WIDGETS,

    /** 谷歌质感库控件：com.google.android.material.*（宿主内置 material 1.13.0，真实可反射）。 */
    MATERIAL,

    /** AndroidX 控件：androidx.* / android.support.*。 */
    ANDROIDX,

    /** R 资源类：android.R 及内部类、宿主/工程库中的 R 类。 */
    R_RESOURCES,

    /** AndroLua 扩展：com.androlua.* / com.luajava.* / com.nirenr.* 及 pro 自定义控件白名单。 */
    ANDROLUA,

    /** Android 框架：其余 android.* 类。 */
    FRAMEWORK,

    /** Java 标准库：java.* / javax.*。 */
    JAVA,

    /** 工程库：工程 libs/ 下的 dex|jar 枚举出的类（动态自动识别）。 */
    PROJECT,

    /** 其他：org.* / dalvik.* / 未匹配项。 */
    OTHER,
}
