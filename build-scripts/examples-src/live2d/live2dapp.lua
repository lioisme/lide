-- live2dapp.lua — 看板娘（Live2D v2）delua 原生引擎封装
--
-- 语义复刻自 pro 侧 jp.live2d.* dex（dexdump 逐指令实证）：
--   LAppView / L2DViewMatrix / L2DModelMatrix / L2DMatrix44 / TouchManager /
--   L2DTargetPoint / Live2DManager（tap/flick/pinch 事件）
--
-- 坐标系约定（与 Java 完全一致）：
--   屏幕像素 → deviceToScreen(S(2/w,2/w)·T(−w/2,+h/2)) → 视图空间（缩放/平移发生处）
--   → modelMatrix 逆变换 → 画布空间（命中）
--   渲染 MVP = Ortho(投影) × viewMatrix × modelMatrix（列主序 16 元表）
--
-- 用法（main.lua）：
--   local app = require "live2dapp"
--   local g = app.new(modelJsonPath)
--   g:setup(w, h)               -- GL surface changed
--   g:loadModel()               -- GL 上下文内
--   g:onFrame(nowMs)            -- 每帧（nowMs = 毫秒时钟）
--   g:touchEvent(action, x, y[, x2, y2])
--   g:onDraw()                  -- 每帧

local native = require "live2d" -- liblive2d.so（无同名 Lua 文件冲突，命中 cpath）

-- ==================== 矩阵（列主序 16 元表，索引 1..16） ====================

local M = {}
M.identity = function()
    return { 1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1 }
end
-- c = a·b（列主序）
M.mul = function(a, b)
    local c = {}
    for col = 0, 3 do
        for row = 0, 3 do
            local s = 0
            for i = 0, 3 do
                s = s + a[row + 1 + i * 4] * b[i + 1 + col * 4]
            end
            c[row + 1 + col * 4] = s
        end
    end
    return c
end
-- t = T(tx,ty)·t（前乘整阵重建，同 L2DMatrix44.multTranslate premultiply）
M.premulTranslate = function(t, tx, ty)
    local out = {}
    for c = 0, 3 do
        out[1 + c * 4] = t[1 + c * 4] + tx * t[4 + c * 4]
        out[2 + c * 4] = t[2 + c * 4] + ty * t[4 + c * 4]
        out[3 + c * 4] = t[3 + c * 4]
        out[4 + c * 4] = t[4 + c * 4]
    end
    for i = 1, 16 do t[i] = out[i] end
end
-- t = S(sx,sy)·t（前乘整阵重建，同 L2DMatrix44.multScale premultiply）
M.premulScale = function(t, sx, sy)
    local out = {}
    for c = 0, 3 do
        out[1 + c * 4] = sx * t[1 + c * 4]
        out[2 + c * 4] = sy * t[2 + c * 4]
        out[3 + c * 4] = t[3 + c * 4]
        out[4 + c * 4] = t[4 + c * 4]
    end
    for i = 1, 16 do t[i] = out[i] end
end
-- 就地缩放（L2DMatrix44.scale 语义：直接赋值 tr[0]/tr[5]）
M.scaleAssign = function(t, sx, sy) t[1] = sx; t[6] = sy end
-- 就地平移（L2DModelMatrix::translate 语义：绝对赋值）
M.translateAssign = function(t, x, y) t[13] = x; t[14] = y end
-- 仿射变换（XY）
M.transformX = function(t, x) return x * t[1] + t[13] end
M.transformY = function(t, y) return y * t[6] + t[14] end
M.invertTransformX = function(t, x) return (x - t[13]) / t[1] end
M.invertTransformY = function(t, y) return (y - t[14]) / t[6] end

-- ==================== L2DViewMatrix 复刻 ====================

local ViewMatrix = {}
ViewMatrix.__index = ViewMatrix
ViewMatrix.new = function()
    local s = setmetatable({}, ViewMatrix)
    s.tr = M.identity()
    s.max = 1; s.min = 1
    s.maxLeft = 0; s.maxRight = 0; s.maxTop = 0; s.maxBottom = 0
    s.screenLeft = 0; s.screenRight = 0; s.screenTop = 0; s.screenBottom = 0
    return s
end
function ViewMatrix:setScreenRect(l, r, b, t)
    self.screenLeft = l; self.screenRight = r; self.screenBottom = b; self.screenTop = t
end
function ViewMatrix:setMaxScreenRect(l, r, b, t)
    self.maxLeft = l; self.maxRight = r; self.maxBottom = b; self.maxTop = t
end
function ViewMatrix:setMaxScale(v) self.max = v end
function ViewMatrix:setMinScale(v) self.min = v end
function ViewMatrix:getScaleX() return self.tr[1] end
function ViewMatrix:isMaxScale() return self.tr[1] == self.max end
function ViewMatrix:isMinScale() return self.tr[1] == self.min end
function ViewMatrix:adjustScale(cx, cy, scale)
    local t = self.tr
    local target = t[1] * scale
    if target < self.min and t[1] > 0 then scale = self.min / t[1] end
    if target > self.max and t[1] > 0 then scale = self.max / t[1] end
    M.premulTranslate(t, cx, cy)
    M.premulScale(t, scale, scale)
    M.premulTranslate(t, -cx, -cy)
end
function ViewMatrix:adjustTranslate(dx, dy)
    local t = self.tr
    if t[13] + self.maxLeft * t[1] + dx > self.screenLeft then
        dx = self.screenLeft - t[13] - self.maxLeft * t[1]
    end
    if t[13] + self.maxRight * t[1] + dx < self.screenRight then
        dx = self.screenRight - t[13] - self.maxRight * t[1]
    end
    if t[14] + self.maxBottom * t[6] + dy > self.screenBottom then
        dy = self.screenBottom - t[14] - self.maxBottom * t[6]
    end
    if t[14] + self.maxTop * t[6] + dy < self.screenTop then
        dy = self.screenTop - t[14] - self.maxTop * t[6]
    end
    M.premulTranslate(t, dx, dy)
end
function ViewMatrix:updateViewMatrix(cx, cy, scale, dx, dy, enableEvent, app)
    local oldMax = self:isMaxScale()
    local oldMin = self:isMinScale()
    self:adjustScale(cx, cy, scale)
    self:adjustTranslate(dx, dy)
    if enableEvent and app then
        if not oldMax and self:isMaxScale() then
            pcall(function() app:maxScaleEvent() end)
        end
        if not oldMin and self:isMinScale() then
            pcall(function() app:minScaleEvent() end)
        end
    end
end

-- ==================== TouchManager 复刻 ====================

local TouchManager = {}
TouchManager.__index = TouchManager
TouchManager.new = function()
    local s = setmetatable({}, TouchManager)
    s.lastX = 0; s.lastY = 0; s.startX = 0; s.startY = 0
    s.lastX1 = 0; s.lastY1 = 0; s.lastX2 = 0; s.lastY2 = 0
    s.lastTouchDistance = -1
    s.moveX = 0; s.moveY = 0; s.scale = 1
    s.touchSingle = true; s.flipAvailable = true
    return s
end
local function dist(x1, y1, x2, y2)
    local dx = x2 - x1; local dy = y2 - y1
    return math.sqrt(dx * dx + dy * dy)
end
-- dex 实证：同号（或双非正）→0；否则 sign(v1)·min(|v1|,|v2|)
local function calcShift(v1, v2)
    if v1 > 0 then
        if v2 > 0 then return 0 end
        local a = v1; local b = -v2
        return a < b and a or b
    elseif v1 < 0 then
        if v2 < 0 then return 0 end
        local a = -v1; local b = v2
        return -(a < b and a or b)
    end
    return 0
end
function TouchManager:touchBegan(x, y)
    self.lastX = x; self.startX = x
    self.lastY = -y; self.startY = -y
    self.lastTouchDistance = -1
    self.flipAvailable = true
    self.touchSingle = true
end
function TouchManager:touchBegan2(x1, y1, x2, y2)
    -- 首帧中心用旧 lastX1/lastX2（原版 bug 复刻）
    local cx = (self.lastX1 + self.lastX2) * 0.5
    local cy = (-self.lastY1 - self.lastY2) * 0.5
    self.lastX = cx; self.lastY = cy
    self.startX = cx; self.startY = cy
    self.lastTouchDistance = dist(x1, y1, x2, y2)
    self.touchSingle = false
end
function TouchManager:touchesMoved(x, y)
    self.lastX = x
    self.lastY = -y
    self.lastTouchDistance = -1
    self.touchSingle = true
end
function TouchManager:touchesMoved2(x1, y1, x2, y2)
    local d = dist(x1, y1, x2, y2)
    local cx = (x1 + x2) * 0.5
    local cy = (-y1 - y2) * 0.5
    if self.lastTouchDistance > 0 then
        self.scale = (d / self.lastTouchDistance) ^ 0.75
        self.moveX = calcShift(x1 - self.lastX1, x2 - self.lastX2)
        self.moveY = calcShift(-y1 - self.lastY1, -y2 - self.lastY2)
    else
        self.scale = 1
        self.moveX = 0
        self.moveY = 0
    end
    self.lastX = cx; self.lastY = cy
    self.lastX1 = x1; self.lastY1 = y1
    self.lastX2 = x2; self.lastY2 = y2
    self.lastTouchDistance = d
    self.touchSingle = false
end

-- ==================== L2DTargetPoint 复刻 ====================

local TargetPoint = {}
TargetPoint.__index = TargetPoint
TargetPoint.new = function()
    return setmetatable({ x = 0, y = 0, vx = 0, vy = 0, tx = 0, ty = 0, lastTimeSec = 0 }, TargetPoint)
end
function TargetPoint:set(x, y) self.tx = x; self.ty = y end
function TargetPoint:update(nowMs)
    -- dex @01a4fc 全量实证：MAX_A=0.177778, FRAME_TO_MAX_SPEED=4.5, FRAME_RATE=30
    if self.lastTimeSec == 0 then
        self.lastTimeSec = nowMs
        return
    end
    local deltaTimeWeight = (nowMs - self.lastTimeSec) * 30 / 1000
    self.lastTimeSec = nowMs
    local MAX_A = 0.177778
    local FRAME_TO_MAX_SPEED = 4.5
    local a = deltaTimeWeight * MAX_A / FRAME_TO_MAX_SPEED
    local dx = self.tx - self.x
    local dy = self.ty - self.y
    if dx == 0 and dy == 0 then return end
    local d = math.sqrt(dx * dx + dy * dy)
    local vx = dx * MAX_A / d
    local vy = dy * MAX_A / d
    local ax = vx - self.vx
    local ay = vy - self.vy
    local alen = math.sqrt(ax * ax + ay * ay)
    if alen > a then
        ax = ax * a / alen
        ay = ay * a / alen
    end
    self.vx = self.vx + ax
    self.vy = self.vy + ay
    local maxV = (math.sqrt(a * a + 8 * a * d) - a) * 0.5
    local curV = math.sqrt(self.vx * self.vx + self.vy * self.vy)
    if curV > maxV then
        self.vx = self.vx * maxV / curV
        self.vy = self.vy * maxV / curV
    end
    self.x = self.x + self.vx
    self.y = self.y + self.vy
end

-- ==================== Live2DApp（LAppView + Live2DManager 复刻） ====================

local Live2DApp = {}
Live2DApp.__index = Live2DApp

-- LAppDefine 对照
Live2DApp.FLICK_DISTANCE = 100
Live2DApp.PRIORITY_IDLE = 1
Live2DApp.PRIORITY_NORMAL = 2

Live2DApp.new = function(modelJsonPath)
    local s = setmetatable({}, Live2DApp)
    s.model = native.new()
    s.loaded = false
    s.modelPath = modelJsonPath
    s.canvasW = 0; s.canvasH = 0
    s.modelMatrix = M.identity()
    s.viewMatrix = ViewMatrix.new()
    s.deviceToScreen = M.identity()
    s.touchMgr = TouchManager.new()
    s.dragMgr = TargetPoint.new()
    s.flickAvailable = true
    s.screenW = 0; s.screenH = 0
    return s
end

function Live2DApp:setup(w, h)
    -- LAppView.setupView 复刻（dex @28736 实证）
    self.screenW = w; self.screenH = h
    local aspect = h / w
    self.viewMatrix:setScreenRect(-1, 1, -aspect, aspect)
    self.viewMatrix:setMaxScreenRect(-2, 2, -2, 2)
    self.viewMatrix:setMaxScale(2.0)
    self.viewMatrix:setMinScale(0.8)
    -- deviceToScreen = S(2/w, 2/w)·T(−w/2, +h/2)（两次前乘）
    self.deviceToScreen = M.identity()
    M.premulTranslate(self.deviceToScreen, -w / 2, h / 2)
    M.premulScale(self.deviceToScreen, 2 / w, 2 / w)
    if self.loaded then
        pcall(function() native.resize(self.model, w, h) end)
    end
end

function Live2DApp:loadModel()
    -- 必须在 GL 上下文内调用（纹理/着色器惰性创建）
    local ok, err = native.load(self.model, self.modelPath)
    if not ok then
        print("live2dapp loadModel failed: " .. tostring(err))
        return false
    end
    self.loaded = true
    local cw, ch = native.getCanvasSize(self.model)
    self.canvasW = cw; self.canvasH = ch
    -- modelMatrix 复刻（L2DBaseModel.cpp:35-39 实证：setWidth(2)+setCenterPosition(0,0)）
    -- sx = 2/cw, sy = −sx；tr[12] = −cw·sx/2 = −1；tr[13] = −ch·sy/2 = ch/cw
    local sx = 2.0 / cw
    self.modelMatrix = { sx, 0, 0, 0, 0, -sx, 0, 0, 0, 0, 1, 0, -1, ch / cw, 0, 1 }
    if self.screenW > 0 then
        pcall(function() native.resize(self.model, self.screenW, self.screenH) end)
    end
    -- idle 循环
    pcall(function() native.startRandomMotion(self.model, "idle", Live2DApp.PRIORITY_IDLE) end)
    print("live2dapp loaded: canvas " .. cw .. "x" .. ch .. " matrix " .. table.concat(self.modelMatrix, ","))
    return true
end

-- 视图坐标 ← 屏幕像素（LAppView.transformDeviceToViewX/Y）
function Live2DApp:toViewX(deviceX)
    return M.invertTransformX(self.viewMatrix.tr, M.transformX(self.deviceToScreen, deviceX))
end
function Live2DApp:toViewY(deviceY)
    return M.invertTransformY(self.viewMatrix.tr, M.transformY(self.deviceToScreen, deviceY))
end

-- action: 0=DOWN 1=UP 2=MOVE；x2,y2 非 nil 表示双指
-- 注意（dex 实证）：所有 transformDeviceToView 转换直接吃 touchMgr 的屏幕像素
-- （touchMgr 内部 Y 负存），不得先转视图空间再转一次。
function Live2DApp:touchEvent(action, x, y, x2, y2)
    if action == 0 then
        self.flickAvailable = true
        if x2 then
            self.touchMgr:touchBegan2(x, y, x2, y2)
            self.dragMgr:set(self:toViewX(self.touchMgr.lastX), self:toViewY(self.touchMgr.lastY))
        else
            self.touchMgr:touchBegan(x, y)
            self.dragMgr:set(self:toViewX(self.touchMgr.lastX), self:toViewY(self.touchMgr.lastY))
        end
    elseif action == 2 then
        if x2 then
            self.touchMgr:touchesMoved2(x, y, x2, y2)
            -- Java touchesMoved(FFFF)：Δ 平移与中心缩放（dex @021708 实证）
            local dx = self.touchMgr.moveX * self.deviceToScreen[1]
            local dy = self.touchMgr.moveY * self.deviceToScreen[1]
            local cx = M.transformX(self.deviceToScreen, self.touchMgr.lastX) * self.touchMgr.scale
            local cy = M.transformY(self.deviceToScreen, self.touchMgr.lastY) * self.touchMgr.scale
            self.viewMatrix:updateViewMatrix(cx, cy, self.touchMgr.scale, dx, dy, true, self)
            self.dragMgr:set(self:toViewX(self.touchMgr.lastX), self:toViewY(self.touchMgr.lastY))
        else
            self.touchMgr:touchesMoved(x, y)
            self.dragMgr:set(self:toViewX(self.touchMgr.lastX), self:toViewY(self.touchMgr.lastY))
            -- flick 判定（dex @0215f4 实证：getFlickDistance() > 100，起点=startX/startY 屏幕像素）
            if self.touchMgr.touchSingle and self.flickAvailable then
                local dx = self.touchMgr.lastX - self.touchMgr.startX
                local dy = self.touchMgr.lastY - self.touchMgr.startY
                if math.sqrt(dx * dx + dy * dy) > Live2DApp.FLICK_DISTANCE then
                    pcall(function()
                        self:flickEvent(self:toViewX(self.touchMgr.startX), self:toViewY(self.touchMgr.startY))
                    end)
                    self.flickAvailable = false
                end
            end
        end
    elseif action == 1 then
        self.dragMgr:set(0, 0)
    end
end

-- 事件（Java Live2DManager.tapEvent / flickEvent / maxScaleEvent / minScaleEvent 复刻）
-- 命中坐标：画布空间 = modelMatrix.invertTransform(视图坐标)（C++ hitTestArea 无内部变换）
function Live2DApp:hitTestArea(area, viewX, viewY)
    local cx = M.invertTransformX(self.modelMatrix, viewX)
    local cy = M.invertTransformY(self.modelMatrix, viewY)
    local ok, hit = pcall(native.hitTest, self.model, area, cx, cy)
    return ok and hit
end

function Live2DApp:tapEvent(viewX, viewY)
    print("tapEvent at " .. string.format("%.3f,%.3f", viewX, viewY))
    if self:hitTestArea("head", viewX, viewY) then
        print("tapEvent HIT head -> setRandomExpression")
        pcall(native.setRandomExpression, self.model)
        return true
    end
    if self:hitTestArea("body", viewX, viewY) then
        print("tapEvent HIT body -> tap_body")
        pcall(native.startRandomMotion, self.model, "tap_body", Live2DApp.PRIORITY_NORMAL)
        return true
    end
    print("tapEvent miss")
    return false
end

function Live2DApp:flickEvent(viewX, viewY)
    print("flickEvent at " .. string.format("%.3f,%.3f", viewX, viewY))
    if self:hitTestArea("head", viewX, viewY) then
        print("flickEvent HIT head -> flick")
        pcall(native.startRandomMotion, self.model, "flick", 0)
        return true
    end
    return false
end

function Live2DApp:maxScaleEvent()
    print("maxScaleEvent -> pinch_out")
    pcall(native.startRandomMotion, self.model, "pinch_out", Live2DApp.PRIORITY_NORMAL)
end

function Live2DApp:minScaleEvent()
    print("minScaleEvent -> pinch_in")
    pcall(native.startRandomMotion, self.model, "pinch_in", Live2DApp.PRIORITY_NORMAL)
end

function Live2DApp:onFrame(nowMs)
    if not self.loaded then return end
    -- LAppView.update 复刻（dex @021918 实证）：dragMgr 平滑 → setDrag
    self.dragMgr:update(nowMs)
    pcall(native.setDrag, self.model, self.dragMgr.x, self.dragMgr.y)
    pcall(native.update, self.model)
end

-- ortho 投影（Java onSurfaceChanged glOrthof(left,right,bottom,top) 实证）
function Live2DApp:ortho()
    local l = self.viewMatrix.screenLeft; local r = self.viewMatrix.screenRight
    local b = self.viewMatrix.screenBottom; local t = self.viewMatrix.screenTop
    return {
        2 / (r - l), 0, 0, 0,
        0, 2 / (t - b), 0, 0,
        0, 0, 1, 0,
        -(r + l) / (r - l), -(t + b) / (t - b), 0, 1,
    }
end

-- MVP = Ortho × viewMatrix × modelMatrix（Java GL 前乘链，lua_draw 全量接管）
function Live2DApp:onDraw()
    if not self.loaded then return end
    local mvp = M.mul(M.mul(self:ortho(), self.viewMatrix.tr), self.modelMatrix)
    pcall(native.draw, self.model, mvp)
end

-- 供 main.lua 或调试使用
function Live2DApp:modelMvp()
    return M.mul(M.mul(self:ortho(), self.viewMatrix.tr), self.modelMatrix)
end

return {
    new = Live2DApp.new,
    matrix = M,
    _app = Live2DApp,
}
