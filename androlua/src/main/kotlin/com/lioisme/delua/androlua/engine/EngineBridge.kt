package com.lioisme.delua.androlua.engine

import android.content.Context
import android.content.Intent

class EngineBridge(private val context: Context) {

    companion object {
        const val EXTRA_PROJECT_DIR = "projectDir"
        const val EXTRA_MAIN_FILE = "mainFile"
    }

    fun createEngineIntent(
        projectDir: String,
        mainFile: String = "main.lua"
    ): Intent {
        return Intent().apply {
            setClassName(context.packageName, "com.lioisme.delua.engine.EngineActivity")
            putExtra(EXTRA_PROJECT_DIR, projectDir)
            putExtra(EXTRA_MAIN_FILE, mainFile)
        }
    }
}
