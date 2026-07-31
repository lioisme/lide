package com.lioisme.delua.engine;

import com.androlua.LuaActivity;
import com.lioisme.delua.R;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class EngineActivity extends LuaActivity {

    private String projectDir;
    private String mainFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        projectDir = intent.getStringExtra("projectDir");
        mainFile = intent.getStringExtra("mainFile");
        if (mainFile == null) mainFile = "main.lua";
        super.onCreate(savedInstanceState);
    }

    @Override
    public void finish() {
        playCloseTransition();
        super.finish();
    }

    @Override
    public void finish(boolean finishTask) {
        playCloseTransition();
        super.finish(finishTask);
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
