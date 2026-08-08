package com.lioisme.lide.engine

/**
 * 引擎运行契约（D-2 收敛）：Intent extra 键的单一事实源。
 *
 * 消费方：
 * - 生产侧 [EngineBridge]（同包，写）；
 * - 消费侧 [EngineActivity]（同包，读）；
 * - 引擎侧 `com.androlua.LuaActivity`（vendor，`getSerializableExtra("arg")`）。
 *
 * 跨模块字面量漂移曾导致契约脆弱（审计 §4.5）；本类统一声明后，
 * 由 `EngineContractTest`（JVM）断言各侧读写的键与 vendor 侧一致。
 */
object EngineContract {
    /** 工程目录 absolutePath。 */
    const val EXTRA_PROJECT_DIR = "projectDir"

    /** 入口脚本文件名（相对工程目录）。 */
    const val EXTRA_MAIN_FILE = "mainFile"

    /** 脚本变长参数（`...`）——必须与 `com.androlua.LuaActivity` 的 `arg` extra 同名。 */
    const val EXTRA_SCRIPT_ARGS = "arg"

    /**
     * 引擎页关闭转场动画资源 ID（int）。动画 ID 由宿主经 Intent 注入
     * （宿主自身 arsc 中的 `R.anim.*`）；缺失（0）时引擎页使用系统默认转场。
     */
    const val EXTRA_ANIM_IN = "animIn"

    /** 引擎页关闭转场动画资源 ID（int），见 [EXTRA_ANIM_IN]。 */
    const val EXTRA_ANIM_OUT = "animOut"

    /** 引擎页类名（:app 模块，宿主 manifest 与打包 launcher 同源）。 */
    const val ENGINE_ACTIVITY = "com.lioisme.lide.engine.EngineActivity"
}