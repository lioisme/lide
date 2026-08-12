package com.lioisme.lide.apibrowser

import android.app.Dialog
import android.content.ContentProvider
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Adapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.Spinner

/**
 * 类用途分类（API 浏览器「适配器 / 容器控件 / 下拉菜单控件 / …」维度）。
 * 由反射语义推断得出（isAssignableFrom 规则链 + isAnnotation/isInterface），
 * 无人工标注，失败降级 null。
 */
enum class ApiUsage {
    ANNOTATION,
    INTERFACE,
    ADAPTER,
    LIST,
    CONTAINER,
    INPUT,
    DROPDOWN,
    POPUP,
    BUTTON,
    DRAWING,
    STATE,
    VIEW,
    COMPAT,
    PROVIDER,
    UTILITY,
}

/**
 * 用途分类器：反射规则链，经 [ApiIndex.loadClassRef] 合并缓存（架构审计 P0-1）——
 * 类解析与列表行/详情页共享单张缓存，不再自建缓存。
 * 判定顺序不可交换：注解/接口优先；适配器/列表/容器/输入框/下拉/弹窗均为非 View 或
 * 需在 View 兜底之前命中；ViewGroup 须在 View 之前命中；列表控件须在容器之前命中
 * （AbsListView/RecyclerView 均为 ViewGroup 子类）。
 * androidx.recyclerview 为可选依赖：缺失时经合并缓存回落 null，相应规则自动跳过。
 */
object ApiUsageClassifier {
    /** 明显非 UI 前缀：静态规则直接归工具类，免反射（java/javax 等加载器层不参与）。
     * 引擎 JNI 桥接前缀消费单一事实源 [EnginePackagePrefixes.JNI_BRIDGE_PACKAGES]（D-1 收口）。 */
    private val utilityPrefixes =
        listOf(
            "java.",
            "javax.",
            "org.",
            "jdk.",
            "sun.",
            "dalvik.",
        ) + EnginePackagePrefixes.JNI_BRIDGE_PACKAGES

    /** androidx RecyclerView（可选依赖，进程内只解析一次；缺失回落 null）。 */
    private val recyclerView: Class<*>? by lazy { ApiIndex.loadClassRef("androidx.recyclerview.widget.RecyclerView") }

    private val recyclerViewAdapter: Class<*>? by lazy { ApiIndex.loadClassRef("androidx.recyclerview.widget.RecyclerView.Adapter") }

    fun classify(name: String): ApiUsage? {
        if (name.startsWith("android.R")) return ApiUsage.UTILITY
        if (utilityPrefixes.any { name.startsWith(it) }) return ApiUsage.UTILITY
        val clazz = ApiIndex.loadClassRef(name) ?: return null
        return when {
            clazz.isAnnotation -> ApiUsage.ANNOTATION

            Adapter::class.java.isAssignableFrom(clazz) ||
                (recyclerViewAdapter?.isAssignableFrom(clazz) == true) -> ApiUsage.ADAPTER

            AbsListView::class.java.isAssignableFrom(clazz) ||
                (recyclerView?.isAssignableFrom(clazz) == true) -> ApiUsage.LIST

            ContentProvider::class.java.isAssignableFrom(clazz) -> ApiUsage.PROVIDER

            ViewGroup::class.java.isAssignableFrom(clazz) -> ApiUsage.CONTAINER

            EditText::class.java.isAssignableFrom(clazz) -> ApiUsage.INPUT

            Spinner::class.java.isAssignableFrom(clazz) -> ApiUsage.DROPDOWN

            Dialog::class.java.isAssignableFrom(clazz) ||
                PopupWindow::class.java.isAssignableFrom(clazz) -> ApiUsage.POPUP

            Button::class.java.isAssignableFrom(clazz) -> ApiUsage.BUTTON

            SurfaceView::class.java.isAssignableFrom(clazz) ||
                TextureView::class.java.isAssignableFrom(clazz) -> ApiUsage.DRAWING

            CompoundButton::class.java.isAssignableFrom(clazz) -> ApiUsage.STATE

            View::class.java.isAssignableFrom(clazz) -> ApiUsage.VIEW

            clazz.isInterface -> ApiUsage.INTERFACE

            clazz.simpleName.endsWith("Compat") -> ApiUsage.COMPAT

            else -> ApiUsage.UTILITY
        }
    }
}
