package com.lioisme.delua.engine;

import com.androlua.LuaActivity;
import android.content.Intent;
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
    public String getLuaPath() {
        luaDir = projectDir;
        return projectDir + "/" + mainFile;
    }
}
