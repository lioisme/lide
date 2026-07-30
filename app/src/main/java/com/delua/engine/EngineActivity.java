package com.delua.engine;

import com.androlua.LuaActivity;
import com.androlua.LuaApplication;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

public class EngineActivity extends LuaActivity {

    private String projectDir;
    private String mainFile;
    private long startTime;
    private boolean hasContentView = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        projectDir = intent.getStringExtra("projectDir");
        mainFile = intent.getStringExtra("mainFile");
        if (mainFile == null) mainFile = "main.lua";
        startTime = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
    }

    @Override
    public String getLuaPath() {
        return projectDir + "/" + mainFile;
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        hasContentView = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        writeResult();
    }

    private void writeResult() {
        long duration = System.currentTimeMillis() - startTime;
        String error = getScriptError();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":").append(error == null ? "true" : "false");
        sb.append(",\"hasContentView\":").append(hasContentView);
        sb.append(",\"durationMs\":").append(duration);
        sb.append(",\"timestamp\":\"");
        sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .format(new java.util.Date()));
        sb.append("\"");
        if (error != null) {
            sb.append(",\"errorMessage\":\"");
            sb.append(escapeJson(error));
            sb.append("\"");
        }
        sb.append("}");
        try {
            FileOutputStream fos = new FileOutputStream(
                new File(getCacheDir(), "engine_result.json"));
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            // 写文件失败不阻塞销毁
        }
    }

    private String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
