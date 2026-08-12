package com.lioisme.lide.engine

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.androlua.LuaActivity
import com.lioisme.lide.model.Project
import java.io.File

/**
 * 引擎页：纯 LuaActivity 子类（全屏内置控制台）。
 *
 * WP-002 诊断闭环（结果回传）：
 * - 重写 [sendMsg] 收集全部控制台输出（print 经 import.lua 改写为 activity.sendMsg，
 *   错误文本同样经此通道），引擎内控制台显示不受影响（双通道）；
 * - [finish] 时经 setResult(RESULT_OK, Intent) 回传（同进程 Activity 栈，无需文件协议），
 *   extra 携带输出文本与错误标记；输出超限截断防止 Binder 事务溢出；
 * - 历史文件协议（engine_result.json / engine_output.txt）不恢复。
 *
 * 归位（2026-08-08 源码组织五需求）：引擎页属于 LIDE 宿主代码，随 app 主源编译；
 * 2026-08-09 引擎并入单 dex 集后，引擎类与宿主类同 dex 集，全 APK 仅一份（无拆分）。
 */
class EngineActivity : LuaActivity() {

    companion object {
        /** extra 截断上限：Binder 事务 ~1MB，取 200KB 留足余量（ACC-4）。 */
        private const val OUTPUT_LIMIT = 200 * 1024

        /** 预编译（Q5）：复用 [EngineResultParser.ERROR_PREFIX] 单一事实源，不再本地持有副本。 */
        private val errorPattern = EngineResultParser.ERROR_PREFIX
    }

    private var projectDir: String? = null
    private var mainFile: String = Project.DEFAULT_MAIN_FILE
    private val outputBuffer = StringBuilder()
    private var scriptError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = intent
        projectDir = intent.getStringExtra(EngineContract.EXTRA_PROJECT_DIR)
        if (projectDir == null) {
            // 打包模式（WP-005）：assets/lua 已由 LuaApplication.extractLuaScripts
            // 解出到 filesDir/lua（getDir("lua", MODE_PRIVATE) 同路径），以其为工程目录。
            projectDir = getDir("lua", MODE_PRIVATE).absolutePath
        }
        mainFile = intent.getStringExtra(EngineContract.EXTRA_MAIN_FILE) ?: Project.DEFAULT_MAIN_FILE
        super.onCreate(savedInstanceState)
    }

    override fun sendMsg(msg: String?) {
        if (msg != null) {
            synchronized(outputBuffer) {
                if (outputBuffer.length < OUTPUT_LIMIT) {
                    outputBuffer.append(msg).append('\n')
                }
                if (!scriptError && errorPattern.containsMatchIn(msg)) {
                    scriptError = true
                }
            }
        }
        super.sendMsg(msg)
    }

    /** runFunc 等回调错误路径（LuaActivity 经 sendError 上报）同样计入错误标记。 */
    override fun sendError(title: String?, msg: Exception?) {
        synchronized(outputBuffer) {
            scriptError = true
        }
        super.sendError(title, msg)
    }

    override fun finish() {
        deliverResult()
        playCloseTransition()
        super.finish()
    }

    override fun finish(finishTask: Boolean) {
        deliverResult()
        playCloseTransition()
        super.finish(finishTask)
    }

    /** 结果回传：宿主经 ActivityResult 通道收取（输出截断 + 错误标记）。 */
    private fun deliverResult() {
        val result = Intent()
        val output: String = synchronized(outputBuffer) { outputBuffer.toString() }
        result.putExtra(EngineContract.EXTRA_OUTPUT, output)
        result.putExtra(EngineContract.EXTRA_ERROR, scriptError)
        setResult(RESULT_OK, result)
    }

    /**
     * 经典返回转场（与 Compose 页面转场同款）：宿主从左侧推入，引擎页向右退出。
     * 旧 API overridePendingTransition 自 API 34 起弃用，按版本分流；
     * 文档要求「紧邻 startActivity / finish 调用」，故置于 super.finish() 之前。
     *
     * 动画资源 ID 经 Intent extra 注入（宿主 arsc 的 R.anim.*，见 EngineContract
     * EXTRA_ANIM_IN/EXTRA_ANIM_OUT）；缺失时回落系统默认转场。
     */
    @Suppress("DEPRECATION")
    private fun playCloseTransition() {
        val animIn = intent.getIntExtra(EngineContract.EXTRA_ANIM_IN, 0)
        val animOut = intent.getIntExtra(EngineContract.EXTRA_ANIM_OUT, 0)
        if (animIn == 0 || animOut == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, animIn, animOut)
        } else {
            overridePendingTransition(animIn, animOut)
        }
    }

    override fun getLuaPath(): String {
        luaDir = projectDir.orEmpty()
        // E4（2026-08-12）：路径拼接统一走 File(dir, name)，避免分隔符/空目录拼接问题。
        return File(projectDir.orEmpty(), mainFile).absolutePath
    }
}