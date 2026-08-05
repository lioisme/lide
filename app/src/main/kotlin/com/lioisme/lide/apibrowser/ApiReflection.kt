package com.lioisme.lide.apibrowser

import java.lang.reflect.Method
import java.util.LinkedHashSet

/**
 * 类详情反射：对齐 pro 原版 javaapi/main.lua 的分组与推导语义，
 * 但改为基于 [Method] 对象解析（修正 pro 从 toString 正则提取时
 * getter 显示 "(public)Name" 的缺陷，返回真实类型）。
 *
 * 事件推导规则与 pro 一致：setOnXxxListener → onXxx。
 */
object ApiReflection {
    data class Detail(
        val header: String,
        val constructors: List<String>,
        val events: List<String>,
        val getters: List<String>,
        val setters: List<String>,
        val fields: List<String>,
        val methods: List<String>,
    )

    private val eventPattern = Regex("setOn(\\w+)Listener")
    private val getterPattern = Regex("get(\\w+)")
    private val setterPattern = Regex("set(\\w+)")

    /**
     * 反射失败（类不存在 / 初始化异常）返回 null，调用方降级。
     * 与 [ApiIndex] 行级缓存共享合并缓存（架构审计 P0-1）：类解析统一走
     * [ApiIndex.loadClassRef]，同一类名全进程一次反射；初始化语义一致（initialize=false）。
     * 必须在 IO 线程调用（AGENTS.md 线程契约）。
     */
    fun reflect(className: String): Detail? {
        return try {
            val clazz = ApiIndex.loadClassRef(className) ?: return null
            val methods = clazz.methods.toList()
            val header =
                buildString {
                    append(clazz.toString())
                    clazz.superclass?.let {
                        append(" extends ")
                        append(it.name)
                    }
                }
            Detail(
                header = header,
                constructors = clazz.constructors.map { it.toString() },
                events = deriveEvents(methods),
                getters = deriveGetters(methods),
                setters = deriveSetters(methods),
                fields = clazz.fields.map { it.toString() },
                methods = methods.map { it.toString() },
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** setOnXxxListener → onXxx（去重保序；pro 规则，仅语义对齐）。 */
    private fun deriveEvents(methods: List<Method>): List<String> {
        val result = LinkedHashSet<String>()
        for (m in methods) {
            val match = eventPattern.matchEntire(m.name) ?: continue
            result.add("on" + match.groupValues[1])
        }
        return result.toList()
    }

    /** getXxx() 无参 → "(返回类型)Name"。 */
    private fun deriveGetters(methods: List<Method>): List<String> {
        val result = LinkedHashSet<String>()
        for (m in methods) {
            val match = getterPattern.matchEntire(m.name) ?: continue
            if (m.parameterCount != 0) continue
            result.add("(${typeName(m.returnType)})${match.groupValues[1]}")
        }
        return result.toList()
    }

    /** setXxx(单参) → "Name(参数类型)"。 */
    private fun deriveSetters(methods: List<Method>): List<String> {
        val result = LinkedHashSet<String>()
        for (m in methods) {
            val match = setterPattern.matchEntire(m.name) ?: continue
            if (m.parameterCount != 1) continue
            result.add("${match.groupValues[1]}(${typeName(m.parameterTypes[0])})")
        }
        return result.toList()
    }

    private fun typeName(type: Class<*>): String = type.simpleName
}
