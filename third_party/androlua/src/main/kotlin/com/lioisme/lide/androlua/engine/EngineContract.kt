package com.lioisme.lide.androlua.engine

/**
 * 引擎运行契约（D-2 收敛）：Intent extra 键的单一事实源。
 *
 * 消费方：
 * - 生产侧 [EngineBridge.createEngineIntent]（:androlua，写）；
 * - 消费侧 `com.lioisme.lide.engine.EngineActivity`（:app，Java 读裸字面量
 *   "projectDir"/"mainFile"——见 EngineActivity.onCreate）；
 * - 引擎侧 `com.androlua.LuaActivity`（vendor，`getSerializableExtra("arg")`）。
 *
 * 跨模块字面量漂移曾导致契约脆弱（审计 §4.5）；本对象统一声明后，
 * 由 `EngineContractTest`（JVM）断言各侧读写的键与 vendor 侧一致。
 */
object EngineContract {
    /** 工程目录 absolutePath。 */
    const val EXTRA_PROJECT_DIR = "projectDir"

    /** 入口脚本文件名（相对工程目录）。 */
    const val EXTRA_MAIN_FILE = "mainFile"

    /** 脚本变长参数（`...`）——必须与 [com.androlua.LuaActivity] 的 `arg` extra 同名。 */
    const val EXTRA_SCRIPT_ARGS = "arg"

    /** 引擎页类名（:app）。 */
    const val ENGINE_ACTIVITY = "com.lioisme.lide.engine.EngineActivity"
}
