package com.lioisme.lide.engine

import android.content.Context
import android.content.Intent
import com.lioisme.lide.R

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
            // 引擎页关闭转场动画经 Intent 注入（引擎 dex 不携带资源，见 EngineContract 注释）。
            putExtra(EngineContract.EXTRA_ANIM_IN, R.anim.slide_in_left)
            putExtra(EngineContract.EXTRA_ANIM_OUT, R.anim.slide_out_right)
        }
    }
}
