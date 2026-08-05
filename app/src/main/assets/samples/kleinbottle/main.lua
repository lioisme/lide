


require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.view.*"
import "android.opengl.*"

-- 核心 OpenGL 模块
local gl = import "gl"

-- 1. 状态参数
local GRID_SIZE = 100
local time = 0
local rotateX, rotateY = 25, 45
local zoom = 1.0  -- 缩放因子
local autoRotateSpeed = 0.5 -- 自动旋转速度

local vertexData = {}
local colorData = {}

-- 2. 数学模型：克莱因瓶
local function updateKleinBottle()
  vertexData = {}
  colorData = {}
  time = time + 0.05
  
  local u_steps, v_steps = GRID_SIZE, 40
  
  for i = 0, u_steps do
    local u = (i / u_steps) * 2 * math.pi
    for j = 0, v_steps do
      local v = (j / v_steps) * 2 * math.pi
      
      -- 克莱因瓶几何方程
      local x = (2.5 + 1.5 * math.cos(v)) * math.cos(u)
      local y = (2.5 + 1.5 * math.cos(v)) * math.sin(u)
      local z = -2.5 * math.sin(v) * math.cos(u/2)
      
      table.insert(vertexData, x * 0.25)
      table.insert(vertexData, y * 0.25)
      table.insert(vertexData, z * 0.25)
      
						
						
      -- 动态色彩映射
      local r = math.sin(u + time) * 0.5 + 0.5
      local g = math.cos(v + time) * 0.5 + 0.5
      local b = 0.8
      table.insert(colorData, r)
      table.insert(colorData, g)
      table.insert(colorData, b)
      table.insert(colorData, 1.0)
    end
  end
end

-- 3. 渲染器
GLRenderer = GLSurfaceView.Renderer({
  onSurfaceCreated = function(gl2, config)
    gl.glClearColor(0, 0, 0, 1)
    gl.glEnable(gl.GL_DEPTH_TEST)
    gl.glShadeModel(gl.GL_SMOOTH)
    gl.glEnable(gl.GL_CULL_FACE)
    gl.glCullFace(gl.GL_BACK)
  end,
  
  onDrawFrame = function(gl2)
    gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT)
    gl.glMatrixMode(gl.GL_MODELVIEW)
    gl.glLoadIdentity()
    
    -- 自动旋转逻辑
    rotateY = rotateY + autoRotateSpeed
    
    -- 缩放与观察位置
    gl.glTranslate(0, 0, -3.5 * zoom)
    gl.glRotate(rotateX, 1, 0, 0)
    gl.glRotate(rotateY, 6, 1, 3)

    updateKleinBottle()

    gl.glEnableClientState(gl.GL_VERTEX_ARRAY)
    gl.glEnableClientState(gl.GL_COLOR_ARRAY)
    gl.glVertexPointer(vertexData, 3)
    gl.glColorPointer(colorData, 4)
    
    gl.glPointSize(2.0)
    gl.glDrawArrays(gl.GL_LINE_STRIP, 0, #vertexData / 3)
    
    gl.glDisableClientState(gl.GL_VERTEX_ARRAY)
    gl.glDisableClientState(gl.GL_COLOR_ARRAY)
  end,
  
  onSurfaceChanged = function(gl2, w, h)
    gl.glViewport(0, 0, w, h)
    gl.glMatrixMode(gl.GL_PROJECTION)
    gl.glLoadIdentity()
    local ratio = w / h
    gl.glFrustum(-ratio, ratio, -1, 1, 1, 20)
  end
})

-- 4. 交互逻辑 (旋转与双指缩放)
local glView = GLSurfaceView(activity)
glView.setRenderer(GLRenderer)
glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
luakt=luakt or luajava

-- 1. 定义监听器逻辑
local listener = luakt.createProxy("android.view.ScaleGestureDetector$OnScaleGestureListener", {
  onScale = function(d)
    -- 计算缩放比例
    zoom = math.min(math.max(zoom / d.getScaleFactor(), 0.5), 3.0)
    return true
  end,
  onScaleBegin = function(d) return true end,
  onScaleEnd = function(d) end
})

-- 2. 使用代理对象创建 detector
local detector = ScaleGestureDetector(activity, listener
({
  onScale = function(d)
    zoom = math.min(math.max(zoom / d.getScaleFactor(), 0.5), 3.0)
    return true
  end
}))

local lastTouchX, lastTouchY = 0, 0
glView.onTouch = function(v, event)
  detector.onTouchEvent(event)
  
  local x, y = event.getX(), event.getY()
  if event.getPointerCount() == 1 then
    if event.getAction() == MotionEvent.ACTION_MOVE then
      rotateY = rotateY + (x - lastTouchX) * 0.5
      rotateX = rotateX + (y - lastTouchY) * 0.5
    end
    lastTouchX, lastTouchY = x, y
  end
  return true
end

activity.setContentView(glView)
