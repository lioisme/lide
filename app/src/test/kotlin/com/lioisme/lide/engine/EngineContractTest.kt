package com.lioisme.lide.engine

import com.lioisme.lide.androlua.engine.EngineContract
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 引擎运行契约（审计 §4.5 / D-2）：断言 Intent extra 键与各消费侧一致。
 *
 * 键的生产侧在 :androlua（[EngineContract]），消费侧：
 * - `EngineActivity.java`（:app）读 `EXTRA_PROJECT_DIR`/`EXTRA_MAIN_FILE`；
 * - vendor `LuaActivity.java`（:androlua）经 `getSerializableExtra("arg")`
 *   读脚本变长参数（`ARG = "arg"`，见 LuaActivity.java:83）。
 * 本测试固化两侧契约，防止跨模块字面量漂移回归。
 */
class EngineContractTest {
    @Test
    fun `contract keys match EngineActivity extras`() {
        assertEquals("projectDir", EngineContract.EXTRA_PROJECT_DIR)
        assertEquals("mainFile", EngineContract.EXTRA_MAIN_FILE)
        assertEquals("arg", EngineContract.EXTRA_SCRIPT_ARGS)
    }

    @Test
    fun `engine activity class name is stable`() {
        assertEquals("com.lioisme.lide.engine.EngineActivity", EngineContract.ENGINE_ACTIVITY)
    }
}
