package com.delua.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.delua.androlua.engine.LuaRunner

class LuaService : Service() {

    private var luaRunner: LuaRunner? = null

    override fun onCreate() {
        super.onCreate()
        luaRunner = LuaRunner(this).also { it.initResident() }
        Log.d("LuaService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        val scriptName = action.removePrefix("com.delua.service.action.")
        Log.d("LuaService", "Starting script: $scriptName")
        Thread {
            try {
                luaRunner?.runScript(filesDir.absolutePath, "$scriptName.lua")
            } catch (e: Exception) {
                Log.e("LuaService", "Script error: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        luaRunner?.destroy()
        luaRunner = null
        Log.d("LuaService", "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
