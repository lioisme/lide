-- live2dapp.lua — 看板娘（Live2D v2）delua 原生引擎封装（多模型版）
--
-- 仅支持 delua 原生库（liblive2d.so），不携带 pro（jp.live2d.* dex）兼容。
-- 语义对齐 live2d 官方 v2 引擎（迁移计划 docs/planned/LIVE2D_NATIVE_MIGRATION_PLAN.md）：
--   LAppView / L2DViewMatrix / L2DModelMatrix / L2DMatrix44 / TouchManager /
--   L2DTargetPoint / LAppLive2DManager（4 模型轮换 + 双模型分支 + tap/flick/pinch）
--
-- 与 live2dtest（单模型）差异：
--   1) 多模型容器：models[] 共享 viewMatrix/deviceToScreen/touchMgr/dragMgr
--      （对齐 Java：LAppView 持有共享状态，LAppModel 各自 modelMatrix）
--   2) reload 分支（dex @01fac4 实证）：modelCount%4
--      0→haru、1→shizuku、2→wanko、3→haru_01+haru_02（双模型）；初始 -1
--   3) 每模型 json layout 应用（dex @0203fe 实证）：
--      width→setWidth、x→setX、y→setY、center_x→centerX、center_y→centerY
--      （5 个模型实测均有 y 键！模型站立高度定位）
--   4) 背景图（LAppRenderer.setupBackground 实证）：方案 B 原生原语
--      native.setupBg/drawBg（liblive2d.so 内 stb_image 解码 + GLES2 shader quad，
--      零 Java 代理；语义对齐 setDrawRect(-2,2,-2,2)+setUVRect(0,1,0,1)）
--   5) flickEvent 动作组为 "flick_head"（dex @01f5b4 实证，priority 2）
--
-- 已声明差异（白名单）：
--   D1 淡入：Java feedIn alpha 0→1（10 帧 FBO 半透明）；原生直接显示
--   D2 音效：sounds/*.wav 不存在于工程 res → 双端均无声
--   D3 加速度计：AccelHelper（旋转矩阵/方向映射）不移植；模拟器无传感器，
--      双端行为一致（真机倾斜/摇晃差异）
--
-- 坐标系约定（与 Java 完全一致）：
--   屏幕像素 → deviceToScreen(S(2/w,2/w)·T(−w/2,+h/2)) → 视图空间（缩放/平移）
--   → modelMatrix 逆变换 → 画布空间（命中）
--   渲染 MVP = Ortho(±1,±aspect) × viewMatrix × modelMatrix（列主序 16 元表）

require "import"

local native = require "live2d" -- liblive2d.so

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
-- 就地平移（L2DMatrix44.translate 语义：绝对赋值 tr[12]/tr[13]，dex @019800 实证）
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

-- ==================== 模型 json layout 读取（迷你解析） ====================
-- 看板娘 5 个模型 json 的 layout 均为 "key":number 形式，正则扫描即可
-- （实测：haru{y:1.2,width:2.9}、haru_01{center_x:0.5,y:1,width:2}、
--   haru_02{center_x:-0.5,y:1,width:2}、wanko{y:1.8,width:2.9}、
--   shizuku{y:1.2,width:2.4}）

local LAYOUT_KEYS = { "width", "height", "x", "y", "center_x", "center_y", "top", "bottom", "left", "right" }
local function jsonLayout(absPath)
    local f = io.open(absPath, "r")
    if not f then
        print("Kanban json open failed: " .. absPath)
        return {}
    end
    local s = f:read("*a"); f:close()
    local layout = {}
    for _, key in ipairs(LAYOUT_KEYS) do
        local num = string.match(s, '"' .. key .. '"%s*:%s*([%-%d%.]+)')
        if num then layout[key] = tonumber(num) end
    end
    return layout
end

-- ==================== 背景图（方案 B：liblive2d.so 原生原语） ====================
--
-- delua 代理性能红线（17:06 实证）：draw 循环内对 Java 对象做 __index 取方法会
-- 每个新 JavaObject 用户态对象（50k 上限 → 崩溃），缓存方法引用调用又报
-- "can not call the function"（LuaState 对方法 wrapper 仅支持隐式 l_i 接口形状）。
-- 因此背景纹理/程序/quad 全部下沉 native（setupBg/drawBg，stb_image 解码），
-- draw 循环内对 Java 的调用降为 0（glClear 也在 C 内完成）。

-- ==================== KanbanApp（模型容器 + 视图状态） ====================

local KanbanApp = {}
KanbanApp.__index = KanbanApp

KanbanApp.FLICK_DISTANCE = 100
KanbanApp.PRIORITY_IDLE = 1
KanbanApp.PRIORITY_NORMAL = 2

KanbanApp.new = function(baseDir)
    local s = setmetatable({}, KanbanApp)
    s.baseDir = baseDir
    s.models = {}          -- { {ptr=, matrix=, cw=, ch=, name=} }
    s.modelCount = -1      -- dex <init> @01f4c8：modelCount=-1
    s.reloadFlg = false
    s.viewMatrix = ViewMatrix.new()
    s.deviceToScreen = M.identity()
    s.touchMgr = TouchManager.new()
    s.dragMgr = TargetPoint.new()
    s.flickAvailable = true
    s.screenW = 0; s.screenH = 0
    s.bgReady = false
    s.onFail = nil -- 加载失败回调（main.lua 注入 activity.finish）
    return s
end

function KanbanApp:setup(w, h)
    -- LAppView.setupView 复刻（dex @0213cc 实证）
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
    -- dex onSurfaceChanged @01f810：模型数 0 → changeModel（首帧触发）
    if #self.models == 0 then
        self:changeModel()
    end
end

function KanbanApp:changeModel()
    -- dex @01f55c：reloadFlg=true; modelCount+=1
    self.reloadFlg = true
    self.modelCount = self.modelCount + 1
end

function KanbanApp:releaseModels()
    -- dex releaseModel @01f88c：逐个 release + clear
    for _, m in ipairs(self.models) do
        pcall(native.free, m.ptr)
    end
    self.models = {}
end

-- layout 应用（dex @0203fe 实证顺序：width→height→x→y→center_x→center_y→…）
-- setWidth(w)=scale(w/cw, -w/cw)；setX/setY=绝对赋值 tr[12]/tr[13]；
-- centerX(x)=tr[13] = x - w/2（w=布局宽度=canvas·scaleX）
function KanbanApp:applyLayout(absPath, cw, ch)
    local t = M.identity()
    local layout = jsonLayout(absPath)
    if layout.width then
        t[1] = layout.width / cw
        t[6] = -layout.width / cw
    end
    if layout.height then
        t[1] = layout.height / ch
        t[6] = layout.height / ch
    end
    if layout.x then t[13] = layout.x end
    if layout.y then t[14] = layout.y end
    if layout.center_x then t[13] = layout.center_x - layout.width * 0.5 end
    if layout.center_y then t[14] = layout.center_y - layout.height * 0.5 end
    -- top/bottom/left/right：5 个模型均无此键，略（有则需按 L2DModelMatrix 公式）
    return t
end

function KanbanApp:loadOne(absPath)
    local m = { ptr = native.new(), name = absPath, cw = 0, ch = 0, matrix = M.identity() }
    local ok, err = native.load(m.ptr, absPath)
    if not ok then
        print("Kanban load FAILED: " .. tostring(err))
        pcall(native.free, m.ptr) -- 防泄漏：load 失败的对象必须释放
        if self.onFail then pcall(self.onFail) end
        return
    end
    local ok2, cw, ch = pcall(native.getCanvasSize, m.ptr)
    if ok2 and cw and ch then m.cw, m.ch = cw, ch end
    m.matrix = self:applyLayout(absPath, m.cw, m.ch)
    table.insert(self.models, m)
    -- idle 循环（对齐 live2dtest 已验证行为）
    pcall(native.startRandomMotion, m.ptr, "idle", KanbanApp.PRIORITY_IDLE)
    print("Kanban loaded " .. absPath .. " canvas " .. tostring(m.cw) .. "x" .. tostring(m.ch)
        .. " tr " .. table.concat(m.matrix, ","))
end

-- reload（GL 线程，dex update @01fac4 实证分支）
function KanbanApp:reload()
    self.reloadFlg = false
    local paths
    local c = self.modelCount % 4
    if c == 0 then
        paths = { "live2d/haru/haru.model.json" }
    elseif c == 1 then
        paths = { "live2d/shizuku/shizuku.model.json" }
    elseif c == 2 then
        paths = { "live2d/wanko/wanko.model.json" }
    else
        paths = { "live2d/haru/haru_01.model.json", "live2d/haru/haru_02.model.json" }
    end
    self:releaseModels()
    for _, rel in ipairs(paths) do
        -- 资源路径约定：<luaDir>/res/<rel>（与宿主 LuaDir 约定一致）
        self:loadOne(self.baseDir .. "/res/" .. rel)
    end
end

-- 视图坐标 ← 屏幕像素（LAppView.transformDeviceToViewX/Y）
function KanbanApp:toViewX(deviceX)
    return M.invertTransformX(self.viewMatrix.tr, M.transformX(self.deviceToScreen, deviceX))
end
function KanbanApp:toViewY(deviceY)
    return M.invertTransformY(self.viewMatrix.tr, M.transformY(self.deviceToScreen, deviceY))
end

-- 命中（C++ hitTestArea 无内部变换 → 传 modelMatrix 逆变换后的画布坐标）
function KanbanApp:hitTestArea(m, area, viewX, viewY)
    local cx = M.invertTransformX(m.matrix, viewX)
    local cy = M.invertTransformY(m.matrix, viewY)
    local ok, hit = pcall(native.hitTest, m.ptr, area, cx, cy)
    return ok and hit
end

-- 事件（dex tapEvent @01f9b4 / flickEvent @01f5b4 实证：遍历全部模型，
-- head/body 独立判定、不提前返回；tap 恒返回 true）
function KanbanApp:tapEvent(viewX, viewY)
    print("tapEvent at " .. string.format("%.3f,%.3f", viewX, viewY))
    for _, m in ipairs(self.models) do
        if self:hitTestArea(m, "head", viewX, viewY) then
            print("tapEvent HIT head -> setRandomExpression")
            pcall(native.setRandomExpression, m.ptr)
        elseif self:hitTestArea(m, "body", viewX, viewY) then
            print("tapEvent HIT body -> tap_body")
            pcall(native.startRandomMotion, m.ptr, "tap_body", KanbanApp.PRIORITY_NORMAL)
        end
    end
    return true
end

function KanbanApp:flickEvent(viewX, viewY)
    print("flickEvent at " .. string.format("%.3f,%.3f", viewX, viewY))
    for _, m in ipairs(self.models) do
        if self:hitTestArea(m, "head", viewX, viewY) then
            print("flickEvent HIT head -> flick_head")
            pcall(native.startRandomMotion, m.ptr, "flick_head", KanbanApp.PRIORITY_NORMAL)
        end
    end
end

function KanbanApp:maxScaleEvent()
    print("maxScaleEvent -> pinch_out")
    for _, m in ipairs(self.models) do
        pcall(native.startRandomMotion, m.ptr, "pinch_out", KanbanApp.PRIORITY_NORMAL)
    end
end

function KanbanApp:minScaleEvent()
    print("minScaleEvent -> pinch_in")
    for _, m in ipairs(self.models) do
        pcall(native.startRandomMotion, m.ptr, "pinch_in", KanbanApp.PRIORITY_NORMAL)
    end
end

-- action: 0=DOWN 1=UP 2=MOVE；x2,y2 非 nil 表示双指
-- 注意（dex 实证）：所有 transformDeviceToView 转换直接吃 touchMgr 的屏幕像素
-- （touchMgr 内部 Y 负存），不得先转视图空间再转一次。
function KanbanApp:touchEvent(action, x, y, x2, y2)
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
                if math.sqrt(dx * dx + dy * dy) > KanbanApp.FLICK_DISTANCE then
                    pcall(function()
                        self:flickEvent(self:toViewX(self.touchMgr.startX), self:toViewY(self.touchMgr.startY))
                    end)
                    self.flickAvailable = false
                end
            end
        end
    elseif action == 1 then
        -- Java touchesEnded：dragMgr.set(0,0)
        self.dragMgr:set(0, 0)
    end
end

-- 每帧（LAppView.update @021918 + LAppLive2DManager.update @01fac4 复刻）
function KanbanApp:onFrame(nowMs)
    self.dragMgr:update(nowMs)
    if self.reloadFlg then self:reload() end
    for _, m in ipairs(self.models) do
        pcall(native.setDrag, m.ptr, self.dragMgr.x, self.dragMgr.y)
        pcall(native.update, m.ptr)
    end
end

-- ortho 投影（Java onSurfaceChanged glOrthof(left,right,bottom,top) 实证）
function KanbanApp:ortho()
    local l = self.viewMatrix.screenLeft; local r = self.viewMatrix.screenRight
    local b = self.viewMatrix.screenBottom; local t = self.viewMatrix.screenTop
    return {
        2 / (r - l), 0, 0, 0,
        0, 2 / (t - b), 0, 0,
        0, 0, 1, 0,
        -(r + l) / (r - l), -(t + b) / (t - b), 0, 1,
    }
end

-- MVP = Ortho × viewMatrix × modelMatrix（Java GL 前乘链）
function KanbanApp:modelMvp(m)
    return M.mul(M.mul(self:ortho(), self.viewMatrix.tr), m.matrix)
end

-- ==================== 背景图（GLES2） ====================

function KanbanApp:initBackground()
    -- LAppRenderer.setupBackground @020d50 复刻：image/back_class_normal.png
    -- （原生：stb_image 解码 + 纹理/程序/quad 全在 liblive2d.so 内，GL 线程调用）
    local path = self.baseDir .. "/res/image/back_class_normal.png"
    local ok, err = native.setupBg(path)
    print("Kanban bg setup=" .. tostring(ok) .. (ok and "" or (" err=" .. tostring(err))))
    self.bgReady = ok
end

function KanbanApp:drawBackground()
    if not self.bgReady then return end
    -- 背景在 viewMatrix 变换内绘制（Java：PushMatrix+MultMatrixf(viewMatrix) 后画 bg）；
    -- C 侧 drawBg 内含 glClear（dex onDrawFrame @020dcc: glClear(0x4000) 最先执行）
    native.drawBg(M.mul(self:ortho(), self.viewMatrix.tr))
end

function KanbanApp:onDraw()
    if #self.models == 0 then return end
    self:drawBackground()
    for _, m in ipairs(self.models) do
        pcall(native.draw, m.ptr, self:modelMvp(m))
    end
end

return {
    new = KanbanApp.new,
    matrix = M,
    _app = KanbanApp,
}
