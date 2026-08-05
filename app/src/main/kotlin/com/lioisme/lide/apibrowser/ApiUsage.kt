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
    ADAPTER,
    CONTAINER,
    INPUT,
    UTILITY,
    BUTTON,
    STATE,
    VIEW,
    POPUP,
    DROPDOWN,
}

/**
 * 用途分类器：反射规则链，经 [ApiIndex.loadClassRef] 合并缓存（架构审计 P0-1）——
 * 类解析与列表行/详情页共享单张缓存，不再自建缓存。
 * 判定顺序不可交换：Adapter/容器/输入框/下拉/弹窗均为非 View 或需在 View 兜底之前命中；
 * ViewGroup 须在 View 之前命中。
 */
object ApiUsageClassifier {
    /** 明显非 UI 前缀：静态规则直接归工具类，免反射（java/javax 等加载器层不参与）。 */
    private val utilityPrefixes =
        listOf(
            "java.",
            "javax.",
            "org.",
            "jdk.",
            "sun.",
            "com.luajava.",
            "com.android.cglib.",
            "dalvik.",
        )

    fun classify(name: String): ApiUsage? {
        if (name.startsWith("android.R")) return ApiUsage.UTILITY
        if (utilityPrefixes.any { name.startsWith(it) }) return ApiUsage.UTILITY
        val clazz = ApiIndex.loadClassRef(name) ?: return null
        return when {
            Adapter::class.java.isAssignableFrom(clazz) -> ApiUsage.ADAPTER

            ViewGroup::class.java.isAssignableFrom(clazz) -> ApiUsage.CONTAINER

            EditText::class.java.isAssignableFrom(clazz) -> ApiUsage.INPUT

            Spinner::class.java.isAssignableFrom(clazz) -> ApiUsage.DROPDOWN

            Dialog::class.java.isAssignableFrom(clazz) ||
                PopupWindow::class.java.isAssignableFrom(clazz) -> ApiUsage.POPUP

            Button::class.java.isAssignableFrom(clazz) -> ApiUsage.BUTTON

            CompoundButton::class.java.isAssignableFrom(clazz) -> ApiUsage.STATE

            View::class.java.isAssignableFrom(clazz) -> ApiUsage.VIEW

            else -> ApiUsage.UTILITY
        }
    }
}
