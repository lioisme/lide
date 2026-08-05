package com.lioisme.lide.apibrowser

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.Spinner

/**
 * 类用途分类（pro 版本 API 浏览器的「适配器 / 容器控件 / …」维度）。
 * 由反射语义推断得出（isAssignableFrom 规则链），无人工标注，失败降级 null。
 */
enum class ApiUsage {
    ADAPTER, CONTAINER, INPUT, UTILITY, BUTTON, STATE, VIEW, POPUP, DROPDOWN,
}

/**
 * 用途分类器：反射规则链 + 并发缓存。
 * 判定顺序不可交换：Adapter/容器/输入框/下拉/弹窗均为非 View 或需在 View 兜底之前命中；
 * ViewGroup 须在 View 之前命中。
 */
object ApiUsageClassifier {

    /**
     * 推断缓存（含失败负缓存）。
     * 仅构建期 IO 线程单线程访问（ApiIndex.assignUsages），故用普通 HashMap；
     * ConcurrentHashMap.getOrPut 不允许 null value，会抛 NPE；HashMap 的 getOrPut 对 null
     * 视为未命中会重复计算，故失败用哨兵对象缓存。
     */
    private val cache = HashMap<String, Any?>()
    private val FAILED = Any()

    /** 明显非 UI 前缀：静态规则直接归工具类，免反射（java/javax 等加载器层不参与）。 */
    private val utilityPrefixes = listOf(
        "java.", "javax.", "org.", "jdk.", "sun.", "com.luajava.", "com.android.cglib.", "dalvik.",
    )

    fun classify(name: String): ApiUsage? {
        if (name.startsWith("android.R")) return ApiUsage.UTILITY
        if (utilityPrefixes.any { name.startsWith(it) }) return ApiUsage.UTILITY
        val cached: Any? = cache.getOrPut(name) {
            val clazz: Any = try {
                Class.forName(name, false, ApiIndex.hostLoader())
            } catch (_: Throwable) {
                FAILED
            }
            if (clazz === FAILED) {
                FAILED
            } else {
                val c = clazz as Class<*>
                when {
                    Adapter::class.java.isAssignableFrom(c) -> ApiUsage.ADAPTER
                    ViewGroup::class.java.isAssignableFrom(c) -> ApiUsage.CONTAINER
                    EditText::class.java.isAssignableFrom(c) -> ApiUsage.INPUT
                    Spinner::class.java.isAssignableFrom(c) -> ApiUsage.DROPDOWN
                    Dialog::class.java.isAssignableFrom(c) ||
                        PopupWindow::class.java.isAssignableFrom(c) -> ApiUsage.POPUP
                    Button::class.java.isAssignableFrom(c) -> ApiUsage.BUTTON
                    CompoundButton::class.java.isAssignableFrom(c) -> ApiUsage.STATE
                    View::class.java.isAssignableFrom(c) -> ApiUsage.VIEW
                    else -> ApiUsage.UTILITY
                }
            }
        }
        return if (cached === FAILED) null else cached as ApiUsage
    }
}
