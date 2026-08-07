package com.lioisme.lide.androlua.engine

import android.content.Context
import android.content.Intent

class EngineBridge(private val context: Context) {

    fun createEngineIntent(
        projectDir: String,
        mainFile: String = "main.lua",
        args: Array<Any?>? = null
    ): Intent {
        return Intent().apply {
            setClassName(context.packageName, EngineContract.ENGINE_ACTIVITY)
            putExtra(EngineContract.EXTRA_PROJECT_DIR, projectDir)
            putExtra(EngineContract.EXTRA_MAIN_FILE, mainFile)
            if (args != null && args.isNotEmpty()) {
                putExtra(EngineContract.EXTRA_SCRIPT_ARGS, args)
            }
        }
    }
}
