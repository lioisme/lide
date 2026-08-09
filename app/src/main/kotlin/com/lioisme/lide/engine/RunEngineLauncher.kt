package com.lioisme.lide.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lioisme.lide.R
import com.lioisme.lide.model.Project

/**
 * 运行入口：直接拉起 EngineActivity（全屏引擎界面），不经过任何宿主中间页。
 * 依赖管理采用 AndroLua Pro 同款「工程内自行管理」模型——工程 libs/ 与 lua/
 * 目录由用户自行维护，引擎经 LuaDexLoader.loadLibs / package.path 自动消费，
 * 本类不做任何运行前物化或校验。
 */
class RunEngineLauncher(
    private val context: Context,
) {
    fun launch(
        projectDir: String,
        mainFile: String = Project.DEFAULT_MAIN_FILE,
        args: Array<Any?>? = null,
    ) {
        context.startActivity(createIntent(projectDir, mainFile, args))
        // 经典推入式转场（与 Compose 页面转场同款）：引擎页从右推入，宿主向左退出。
        // Activity#overrideActivityTransition(OPEN) 的文档要求是「紧邻 startActivity 之后」调用，
        // 调用过早（startActivity 之前）在 API 34+ 上不生效，静默回退为默认转场。
        (context as? Activity)?.applyLaunchTransition()
    }

    /**
     * 构建引擎 Intent（WP-002：宿主经 ActivityResult 通道启动以便收取运行结果，
     * 转场动画在经 launcher 启动的路径下不生效——ActivityResult 时机不可插桩，属可接受的视觉回退）。
     */
    fun createIntent(
        projectDir: String,
        mainFile: String = Project.DEFAULT_MAIN_FILE,
        args: Array<Any?>? = null,
    ): Intent = EngineBridge(context).createEngineIntent(projectDir, mainFile, args)

    /** 旧 API [Activity.overridePendingTransition] 自 API 34 起弃用，按版本分流。 */
    private fun Activity.applyLaunchTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
