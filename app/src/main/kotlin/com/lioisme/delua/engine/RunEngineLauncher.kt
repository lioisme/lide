package com.lioisme.delua.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lioisme.delua.R
import com.lioisme.delua.androlua.common.DependencyManager
import com.lioisme.delua.androlua.engine.EngineBridge
import java.util.concurrent.Executors

/**
 * 运行入口：物化依赖后直接拉起 EngineActivity（全屏引擎界面），
 * 不经过任何宿主中间页。依赖物化失败时仅提示，不启动引擎。
 */
class RunEngineLauncher(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun launch(projectDir: String, mainFile: String = "main.lua") {
        executor.execute {
            val ok = DependencyManager(context)
                .materializeProjectDependencies(projectDir)
                .isSuccess
            mainHandler.post {
                if (ok) {
                    context.startActivity(
                        EngineBridge(context).createEngineIntent(projectDir, mainFile)
                    )
                } else {
                    Toast.makeText(
                        context,
                        R.string.run_materialize_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
