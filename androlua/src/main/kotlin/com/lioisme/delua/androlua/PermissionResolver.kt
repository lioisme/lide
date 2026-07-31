package com.lioisme.delua.androlua

/**
 * user_permission 语义（纯函数，JVM 可单测）。
 *
 * 对齐 pro：`projectinfo/main.lua:85-101` 把勾选权限写回 init.lua 的
 * `user_permission` 数组（字符串或裸标识符）；打包时按白名单过滤 manifest。
 * 本类负责声明读取与标准权限判定，不涉 Android 运行时 API。
 */
object PermissionResolver {

    /** 从 init.lua 读取工程声明的权限清单（缺省/空表 → 空集合）。 */
    fun declared(initContent: String?): List<String> {
        if (initContent.isNullOrBlank()) return emptyList()
        return LuaConfigParser.stringArrayField(initContent, "user_permission")
    }

    /**
     * 是否为可运行时请求的权限（非特殊权限/非 manifest 权限）。
     * 特殊权限（SYSTEM_ALERT_WINDOW/WRITE_SETTINGS/MANAGE_EXTERNAL_STORAGE…）
     * 走系统设置页，不经 RequestMultiplePermissions。
     */
    fun isRuntimeRequestable(permission: String): Boolean {
        if (permission.isBlank()) return false
        if (permission.startsWith("android.permission.")) {
            val name = permission.removePrefix("android.permission.")
            return !SPECIAL_PERMISSIONS.contains(name)
        }
        return true
    }

    /** 仅保留可运行时请求的权限（过滤掉特殊权限）。 */
    fun runtimeRequestable(declared: List<String>): List<String> =
        declared.filter(::isRuntimeRequestable)

    private val SPECIAL_PERMISSIONS = setOf(
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "MANAGE_EXTERNAL_STORAGE",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "REQUEST_INSTALL_PACKAGES",
        "PACKAGE_USAGE_STATS",
    )
}
