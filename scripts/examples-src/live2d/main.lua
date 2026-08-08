-- 看板娘 main.lua — lide 原生引擎版（多模型）
-- Lua -> C 直调 liblive2d.so（require "live2dapp"），GL 线程零 Java；
-- 语义来源：历史迁移实证 LIVE2D_NATIVE_MIGRATION_PLAN（dex 逐指令实证，结论已蒸馏）
-- 仅支持 lide 原生库（liblive2d.so）；不携带任何 pro（jp.live2d.* dex）回退代码/资产
require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.view.*"
import "android.opengl.GLSurfaceView"
import "android.opengl.GLES20"
import "layout"

print("LIVE2D_APP start")

-- 全屏（含刘海屏适配）
local window = activity.getWindow()
window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
pcall(function()
    local lp = window.getAttributes()
    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    window.setAttributes(lp)
end)

local baseDir = tostring(this.getLuaDir())

-- GL 线程零 Java 强化（tombstone_12 GLThread 崩溃实证）：native 模式下把全局
-- print 重定向到 C 侧 logcat（liblive2d.so __android_log_print）。GL 回调/每帧
-- 内的 print（FPS、Kanban loaded/FAILED、bg setup…）不再经 luajava 回调 Java
-- （print→Toast 每秒一次 → 主线程 Binder 负担 + 从 native GL 线程的 JNI 调用）
local native = require "live2d"
if type(native) == "table" and native.log then
    print = function(...)
        local parts = {}
        for i = 1, select("#", ...) do
            parts[i] = tostring(select(i, ...))
        end
        native.log(table.concat(parts, "\t"))
    end
end

-- 原生路径：pcall(require,"live2dapp") → liblive2d.so 直调（GLSurfaceView 托管）
-- require 失败（库缺失/ABI 不匹配）→ 明确报错退出，不回退任何兼容路径
local okNative, live2dapp = pcall(require, "live2dapp")
if not okNative then
    print("LIVE2D_PATH native FAILED: " .. tostring(live2dapp))
    pcall(activity.finish)
    return
end
print("LIVE2D_PATH native")
local app = live2dapp.new(baseDir)
app.onFail = function()
    -- 加载失败兜底：释放 GL 线程并退出
    print("Kanban onFail -> finish")
    pcall(activity.finish)
end

-- draw 循环零 Java 调用红线（PITFALLS 17:06 实证）：JavaObject 用户态对象只增
-- 不减 + 缓存方法引用不可调用 → 每帧仅调 native（liblive2d.so），Java 方法只在
-- 低频事件（每 120 帧的 FPS/glGetError 校验）使用
local frameCount = 0
local lastFpsTime = native.nowMs()

local w = GLSurfaceView(activity)
w.setEGLContextClientVersion(2) -- 引擎用 GLES2 shader
w.setRenderer(GLSurfaceView.Renderer{
    onSurfaceCreated = function(self, gl, config)
        print("GL onSurfaceCreated")
        local ok, err = xpcall(app.initBackground, function(e) return debug.traceback(tostring(e), 2) end, app)
        print("GL initBackground ok=" .. tostring(ok) .. (ok and "" or (" err=" .. tostring(err))))
    end,
    onSurfaceChanged = function(self, gl, wd, ht)
        -- PITFALLS §24：ES2 下代理传参异常（gl 为 number/nil），尺寸从 View 取
        local vw, vh = w.getWidth(), w.getHeight()
        if vw == 0 or vh == 0 then vw, vh = wd, ht end
        if vw == 0 or vh == 0 then vw, vh = 1080, 1920 end
        GLES20.glViewport(0, 0, vw, vh)
        print("GL onSurfaceChanged " .. tostring(vw) .. "x" .. tostring(vh))
        -- 首帧模型数 0 → changeModel → 下帧 reload（dex onSurfaceChanged 实证）
        pcall(app.setup, app, vw, vh)
    end,
    onDrawFrame = function(self, gl)
        -- glClear 由 native.drawBg 内含（方案 B，C 侧 GL_COLOR_BUFFER_BIT）
        app:onFrame(native.nowMs())
        app:onDraw()
        frameCount = frameCount + 1
        if frameCount % 120 == 0 then
            local now = native.nowMs()
            local msg = "FPS " .. string.format("%.1f", 120000 / (now - lastFpsTime))
            if native.glError then
                msg = msg .. " glErr=" .. tostring(native.glError())
            end
            native.log(msg)
            lastFpsTime = now
        end
    end,
})
w.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)

local lastDown = {}
w.setOnTouchListener{
    onTouch = function(v, event)
        local action = event.getActionMasked()
        local pc = event.getPointerCount()
        if action == 0 then -- ACTION_DOWN
            lastDown = { event.getX(), event.getY() }
            if pc >= 2 then
                pcall(app.touchEvent, app, 0, event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            else
                pcall(app.touchEvent, app, 0, event.getX(), event.getY())
            end
            return true
        elseif action == 2 then -- ACTION_MOVE
            if pc >= 2 then
                pcall(app.touchEvent, app, 2, event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            else
                pcall(app.touchEvent, app, 2, event.getX(), event.getY())
            end
            return true
        elseif action == 1 then -- ACTION_UP
            -- 单击判定（dex 实证：onSingleTapConfirmed 用 TouchManager.getX/getY — Y 负存）
            local dx = event.getX() - lastDown[1]
            local dy = event.getY() - lastDown[2]
            if math.sqrt(dx * dx + dy * dy) < 20 then
                pcall(app.tapEvent, app, app:toViewX(event.getX()), app:toViewY(-event.getY()))
            end
            pcall(app.touchEvent, app, 1, event.getX(), event.getY())
            return true
        end
        return false
    end,
}

activity.setContentView(loadlayout(layout))
local param = FrameLayout.LayoutParams(-1, -1) -- 舞台大小
frame.addView(w, param)
change.bringToFront()
change.onClick = function()
    app:changeModel()
end

-- 双击返回键退出（先停 GL 线程再 finish，避免 GL 线程访问已关闭的 Lua 状态）
local count = 0
local function exitApp()
    pcall(function() w.onPause() end)
    pcall(activity.finish)
end
function onKeyDown(code, event)
    if code == KeyEvent.KEYCODE_BACK then
        local now = os.time()
        if count + 2 > now then
            exitApp()
        else
            Toast.makeText(activity, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
            count = now
        end
        return true
    end
end
function onDestroy()
    -- 任何退出路径（含宿主导航 finish / onFail finish）：先停 GL 线程
    pcall(function() w.onPause() end)
end
