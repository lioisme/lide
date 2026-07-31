package com.lioisme.delua.engine;

import com.androlua.LuaActivity;
import com.lioisme.delua.R;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * 引擎页：纯 LuaActivity 子类（全屏内置控制台）。
 *
 * WP-002 诊断闭环（结果回传）：
 * - 重写 [sendMsg] 收集全部控制台输出（print 经 import.lua 改写为 activity.sendMsg，
 *   错误文本同样经此通道），引擎内控制台显示不受影响（双通道）；
 * - [finish] 时经 setResult(RESULT_OK, Intent) 回传（同进程 Activity 栈，无需文件协议），
 *   extra 携带输出文本与错误标记；输出超限截断防止 Binder 事务溢出；
 * - 历史文件协议（engine_result.json / engine_output.txt）不恢复。
 */
public class EngineActivity extends LuaActivity {

    /** 输出文本 extra（String，已截断）。 */
    public static final String EXTRA_OUTPUT = "output";
    /** 脚本错误标记 extra（boolean）。 */
    public static final String EXTRA_ERROR = "error";

    /** extra 截断上限：Binder 事务 ~1MB，取 200KB 留足余量（ACC-4）。 */
    private static final int OUTPUT_LIMIT = 200 * 1024;

    /** 预编译：sendMsg 每行输出都会判定，String.matches 每次重编译正则不可接受。 */
    private static final java.util.regex.Pattern ERROR_PATTERN =
            java.util.regex.Pattern.compile(
                    "^(Runtime error|Syntax error|error error|Out of memory|GC error|Yield error|Unknown error \\d+): ");

    private String projectDir;
    private String mainFile;
    private final StringBuilder outputBuffer = new StringBuilder();
    private boolean scriptError = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        projectDir = intent.getStringExtra("projectDir");
        mainFile = intent.getStringExtra("mainFile");
        if (mainFile == null) mainFile = "main.lua";
        super.onCreate(savedInstanceState);
    }

    @Override
    public void sendMsg(String msg) {
        if (msg != null) {
            synchronized (outputBuffer) {
                if (outputBuffer.length() < OUTPUT_LIMIT) {
                    outputBuffer.append(msg).append('\n');
                }
                if (!scriptError && ERROR_PATTERN.matcher(msg).find()) {
                    scriptError = true;
                }
            }
        }
        super.sendMsg(msg);
    }

    /** runFunc 等回调错误路径（LuaActivity 经 sendError 上报）同样计入错误标记。 */
    @Override
    public void sendError(String title, Exception msg) {
        synchronized (outputBuffer) {
            scriptError = true;
        }
        super.sendError(title, msg);
    }

    @Override
    public void finish() {
        deliverResult();
        playCloseTransition();
        super.finish();
    }

    @Override
    public void finish(boolean finishTask) {
        deliverResult();
        playCloseTransition();
        super.finish(finishTask);
    }

    /** 结果回传：宿主经 ActivityResult 通道收取（输出截断 + 错误标记）。 */
    private void deliverResult() {
        Intent result = new Intent();
        String output;
        synchronized (outputBuffer) {
            output = outputBuffer.toString();
        }
        result.putExtra(EXTRA_OUTPUT, output);
        result.putExtra(EXTRA_ERROR, scriptError);
        setResult(RESULT_OK, result);
    }

    /**
     * 经典返回转场（与 Compose 页面转场同款）：宿主从左侧推入，引擎页向右退出。
     * 旧 API overridePendingTransition 自 API 34 起弃用，按版本分流；
     * 文档要求「紧邻 startActivity / finish 调用」，故置于 super.finish() 之前。
     */
    private void playCloseTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    OVERRIDE_TRANSITION_CLOSE,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right);
        } else {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    @Override
    public String getLuaPath() {
        luaDir = projectDir;
        return projectDir + "/" + mainFile;
    }
}
