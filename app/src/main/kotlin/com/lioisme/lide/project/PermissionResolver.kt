package com.lioisme.lide.project

/**
 * user_permission 语义（纯函数，JVM 可单测）。
 *
 * 对齐 pro：`projectinfo/main.lua:85-101` 把勾选权限写回 init.lua 的
 * `user_permission` 数组（字符串或裸标识符）；打包时按白名单过滤 manifest。
 * 本类负责声明读取、标准权限判定与权限名归一化，不涉 Android 运行时 API。
 *
 * 权限名口径（审计修正，避免宿主/工程权限混淆）：
 * - 工程 init.lua 允许写裸标识符（`"CAMERA"`）或全名（`"android.permission.CAMERA"`），
 *   运行时请求与打包 manifest 白名单一律经 [normalize] 归一化为全名后使用；
 * - 裸名直接传 `checkSelfPermission`/`RequestMultiplePermissions` 恒失败
 *   （系统只认 `android.permission.` 全名），归一化是运行前请求生效的前提。
 */
object PermissionResolver {

    private const val PREFIX = "android.permission."

    /** 从 init.lua 读取工程声明的权限清单（缺省/空表 → 空集合）。 */
    fun declared(initContent: String?): List<String> {
        if (initContent.isNullOrBlank()) return emptyList()
        return LuaConfigParser.stringArrayField(initContent, "user_permission")
    }

    /** 归一化为全名：`CAMERA` → `android.permission.CAMERA`；已带前缀/非标准名原样返回。 */
    fun normalize(permission: String): String {
        val name = permission.trim()
        if (name.isEmpty()) return name
        if (name.startsWith("android.permission.") || name.startsWith("com.")) return name
        val simple = name.substringAfterLast('.')
        return PREFIX + simple
    }

    /** 是否为特殊权限（悬浮窗/系统设置/安装/忽略电池优化/外部存储/使用统计…）。 */
    fun isSpecial(permission: String): Boolean =
        SPECIAL_PERMISSIONS.contains(permission.removePrefix(PREFIX))

    /**
     * 是否为可运行时请求的权限（非特殊权限/非 manifest 权限）。
     * 特殊权限（SYSTEM_ALERT_WINDOW/WRITE_SETTINGS/MANAGE_EXTERNAL_STORAGE…）
     * 走系统设置页，不经 RequestMultiplePermissions。
     */
    fun isRuntimeRequestable(permission: String): Boolean {
        if (permission.isBlank()) return false
        if (permission.startsWith(PREFIX)) {
            return !SPECIAL_PERMISSIONS.contains(permission.removePrefix(PREFIX))
        }
        return true
    }

    /** 仅保留可运行时请求的权限（过滤掉特殊权限）。 */
    fun runtimeRequestable(declared: List<String>): List<String> =
        declared.filter(::isRuntimeRequestable)

    /** 特殊权限（裸名，不含前缀）。与 [isSpecial]/[isRuntimeRequestable] 共用。 */
    val specialPermissionNames: Set<String> get() = SPECIAL_PERMISSIONS

    private val SPECIAL_PERMISSIONS = setOf(
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "MANAGE_EXTERNAL_STORAGE",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "REQUEST_INSTALL_PACKAGES",
        "PACKAGE_USAGE_STATS",
    )
}
