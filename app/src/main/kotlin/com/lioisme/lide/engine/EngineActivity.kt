package com.lioisme.lide.engine

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.androlua.LuaActivity

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
 * 归位（2026-08-08 源码组织五需求）：引擎页属于 LIDE 宿主代码，随 app 主源编译进
 * IDE dex（打包产物经 P0-7 直用宿主根 dex，引擎页对产物依然可用）；runtime 变体
 * 的 R8 按 manifest 可达性把它从引擎 dex 剥离（verifyRuntimeSurface 断言），
 * 保证全 APK 中引擎页仅一份。
 */
class EngineActivity : LuaActivity() {

    companion object {
        /** 输出文本 extra（String，已截断）。 */
        const val EXTRA_OUTPUT = "output"

        /** 脚本错误标记 extra（boolean）。 */
        const val EXTRA_ERROR = "error"

        /** extra 截断上限：Binder 事务 ~1MB，取 200KB 留足余量（ACC-4）。 */
        private const val OUTPUT_LIMIT = 200 * 1024

        /** 预编译：sendMsg 每行输出都会判定，Regex.matches 每次重编译不可接受。 */
        private val errorPattern =
            Regex(
                "^(Runtime error|Syntax error|error error|Out of memory|GC error|Yield error|Unknown error \\d+): ",
            )
    }

    private var projectDir: String? = null
    private var mainFile: String = "main.lua"
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
        mainFile = intent.getStringExtra(EngineContract.EXTRA_MAIN_FILE) ?: "main.lua"
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
        result.putExtra(EXTRA_OUTPUT, output)
        result.putExtra(EXTRA_ERROR, scriptError)
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
        return "$projectDir/$mainFile"
    }
}