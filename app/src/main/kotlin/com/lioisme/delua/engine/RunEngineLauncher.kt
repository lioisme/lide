package com.lioisme.delua.engine

import android.app.Activity
import android.content.Context
import android.os.Build
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
                    // 经典推入式转场（与 Compose 页面转场同款）：引擎页从右推入，宿主向左退出。
                    // Activity#overrideActivityTransition(OPEN) 的文档要求是「紧邻 startActivity 之后」调用，
                    // 调用过早（startActivity 之前）在 API 34+ 上不生效，静默回退为默认转场。
                    (context as? Activity)?.applyLaunchTransition()
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

    /** 旧 API [Activity.overridePendingTransition] 自 API 34 起弃用，按版本分流。 */
    private fun Activity.applyLaunchTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
