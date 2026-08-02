-- live2d 看板娘 — delua 原生引擎直调示例
-- Lua -> C 直调 liblive2d.so（require "live2d"），无任何 Java 引擎类参与；
-- Java 侧只有 GLSurfaceView（提供 GL 上下文），每帧 Lua 调用
-- live2d.update()/live2d.draw() 进入原生引擎。
require "import"
import "android.app.*"
import "android.os.*"
import "android.view.*"
import "android.widget.*"
import "android.opengl.GLSurfaceView"
import "android.opengl.GLES20"
import "java.lang.System"

print("LIVE2D_APP start")

local baseDir = tostring(activity.getLuaDir())
local modelPath = baseDir .. "/res/live2d/haru/haru.model.json"

local ok, live2dapp = pcall(require, "live2dapp")
if not ok then
    error("require live2dapp failed: " .. tostring(live2dapp))
end
print("LIVE2D_PATH native")
local app = live2dapp.new(modelPath)

local surfaceW, surfaceH = 0, 0
local w = nil

local renderer = GLSurfaceView.Renderer{
    onSurfaceCreated = function(self, gl, config)
        print("GL onSurfaceCreated")
        if app then
            local okLoad, err = app:loadModel()
            print("GL loadModel ok=" .. tostring(okLoad) .. " err=" .. tostring(err))
        end
    end,
    onSurfaceChanged = function(self, gl, wd, ht)
        -- ES2 下 AndroLua 代理传参异常（gl 为 number/nil），尺寸从 View 取
        local vw, vh = 0, 0
        if w then vw = w.getWidth(); vh = w.getHeight() end
        if vw == 0 or vh == 0 then
            if type(wd) == "number" and type(ht) == "number" then vw, vh = wd, ht end
        end
        if vw == 0 or vh == 0 then vw, vh = 1080, 1920 end
        surfaceW, surfaceH = vw, vh
        GLES20.glViewport(0, 0, vw, vh)
        print("GL onSurfaceChanged " .. tostring(vw) .. "x" .. tostring(vh))
        if app then app:setup(vw, vh) end
    end,
    onDrawFrame = function(self, gl)
        GLES20.glClear(16384) -- GL_COLOR_BUFFER_BIT
        if app then
            app:onFrame(System.currentTimeMillis())
            app:onDraw()
        end
        frameCount = frameCount + 1
        if frameCount % 300 == 0 then
            local err = GLES20.glGetError()
            local now = System.currentTimeMillis()
            local fps = 300000 / (now - lastFpsTime)
            print("FPS " .. string.format("%.1f", fps) .. " glErr=" .. tostring(err))
            lastFpsTime = now
        end
    end,
}

frameCount = 0
lastFpsTime = System.currentTimeMillis()

w = GLSurfaceView(activity)
w.setEGLContextClientVersion(2) -- 引擎用 GLES2 shader
w.setRenderer(renderer)
w.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)

local lastDown = {}
w.setOnTouchListener{
    onTouch = function(v, event)
        local action = event.getActionMasked()
        local pc = event.getPointerCount()
        if action == 0 then -- ACTION_DOWN
            lastDown = { event.getX(), event.getY() }
            if pc >= 2 then
                app:touchEvent(0, event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            else
                app:touchEvent(0, event.getX(), event.getY())
            end
            return true
        elseif action == 2 then -- ACTION_MOVE
            if pc >= 2 then
                app:touchEvent(2, event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            else
                app:touchEvent(2, event.getX(), event.getY())
            end
            return true
        elseif action == 1 then -- ACTION_UP
            -- 单击判定（dex 实证：onSingleTapConfirmed 用 TouchManager.getX/getY —— Y 负存）
            local dx = event.getX() - lastDown[1]
            local dy = event.getY() - lastDown[2]
            if math.sqrt(dx * dx + dy * dy) < 20 then
                pcall(app.tapEvent, app,
                    app:toViewX(event.getX()), app:toViewY(-event.getY()))
            end
            app:touchEvent(1, event.getX(), event.getY())
            return true
        end
        return false
    end,
}

activity.setContentView(w)

print("LIVE2D_APP setup done, model=" .. modelPath)
