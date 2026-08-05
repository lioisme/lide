package com.lioisme.lide.androlua.engine

import android.content.Context
import android.content.Intent

class EngineBridge(private val context: Context) {

    companion object {
        const val EXTRA_PROJECT_DIR = "projectDir"
        const val EXTRA_MAIN_FILE = "mainFile"

        /**
         * 脚本变长参数（`...`）的 Intent key。
         * 必须与 [com.androlua.LuaActivity] 读取的 `arg` extra 同名：
         * 引擎侧 `onCreate` 经 `getSerializableExtra(ARG)` 取出后
         * 作为 `doFile(luaPath, arg)` 的 varargs 推给 Lua 脚本。
         */
        const val EXTRA_SCRIPT_ARGS = "arg"
    }

    fun createEngineIntent(
        projectDir: String,
        mainFile: String = "main.lua",
        args: Array<Any?>? = null
    ): Intent {
        return Intent().apply {
            setClassName(context.packageName, "com.lioisme.lide.engine.EngineActivity")
            putExtra(EXTRA_PROJECT_DIR, projectDir)
            putExtra(EXTRA_MAIN_FILE, mainFile)
            if (args != null && args.isNotEmpty()) {
                putExtra(EXTRA_SCRIPT_ARGS, args)
            }
        }
    }
}
