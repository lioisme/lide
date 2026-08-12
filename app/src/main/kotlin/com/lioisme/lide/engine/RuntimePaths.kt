package com.lioisme.lide.engine

import com.androlua.LuaApplication
import java.io.File

/**
 * 引擎存储路径门面（2026-08-11 解耦审计 C-1/C-2 收口）。
 *
 * 引擎外部存储根（`LuaApplication.luaExtDir`）的唯一宿主入口：UI / project / 根 Activity
 * 一律经本门面取值，禁止直接触碰 `com.androlua.LuaApplication`（含 Kotlin 对 Java
 * protected 字段的越权直读——上游字段改名即宿主全碎，且引擎内部事实散落各层）。
 *
 * 实现说明：
 * - 一律走 public getter `getLuaExtDir()`（LuaApplication.java:327），不读字段；
 * - 全部返回绝对路径的 [File] / [String]，由调用方自行决定 mkdirs / 写盘时机；
 * - 本门面无状态（引擎单例即状态源），线程安全天然成立。
 */
object RuntimePaths {
    /** E4（2026-08-12）：工程根目录子目录名（单一事实源，project 包复用）。 */
    const val PROJECT_DIR_NAME = "project"

    /** E4（2026-08-12）：打包产物目录子目录名（与 AndroLua Pro 一致）。 */
    const val APK_DIR_NAME = "apk"

    /** 引擎外部存储根（`/storage/emulated/0/AndroLua` 或私有回落目录，pro 同款规则）。 */
    fun luaExtDir(): String = LuaApplication.getInstance().getLuaExtDir()

    /** 工程根目录（`<luaExtDir>/project/`，工程列表/新建/收养的基目录）。 */
    fun projectsDir(): File = File(luaExtDir(), PROJECT_DIR_NAME)

    /** 打包产物默认输出目录（`<luaExtDir>/apk/`，与 AndroLua Pro 一致）。 */
    fun defaultApkDir(): File = File(luaExtDir(), APK_DIR_NAME)
}
