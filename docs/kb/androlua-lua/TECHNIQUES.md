# 方法论与最佳实践（Methodology & Best Practices）

> 可复用的开发方法论、最佳实践与最小代码片段。标注来源，新条目按 README 维护约定追加。

## 1. 标准工程骨架（demo 风格，✅双端一致）

```lua
require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.view.*"

layout = {
  LinearLayout;
  orientation = "vertical";
  layout_width = "fill";
  layout_height = "fill";
  {
    TextView;
    id = "tv";
    text = "Hello";
    textSize = "17sp";
  };
};
activity.setContentView(loadlayout(layout))
```

`init.lua`：

```lua
appname = "demo"
appver = "1.0"
packagename = "com.androlua.demo"
user_permission = { "INTERNET", "WRITE_EXTERNAL_STORAGE" }
```

## 2. 列表 + LuaAdapter（demo 写法）

```lua
item = {
  LinearLayout; layout_width = "fill"; orientation = "vertical";
  { TextView; id = "content"; layout_margin = "5dp"; textSize = "17sp"; };
};
adapter = LuaAdapter(activity, item)
list.setAdapter(adapter)
```

## 3. 双端验证方法（速览，完整流程见 SKILL `androlua-project-validation`）

1. 推工程 → pro 启动（`-n com.androlua/...`）→ 抓 `logcat -s lua:*` + `screencap`。
2. pro 通过后（含 .alp 打包运行）才进 lide，同一工程同一启动方式抓同一组证据。
3. 逐字 diff 两端日志/报错栈；只允许白名单差异（SKILL §6）。
4. 不一致 → 按 SKILL §5 分层（L0 工程/环境 → L1 脚本 → L2 .so → L3 Java 绑定 → L4 宿主 → L5 语言语义）收敛，修 lide 不修工程。

## 4. 探针工程（语言语义差异快速定位）

`docs/reference/project/probe/` 输出全局函数存在性（如 `PROBE tointeger=1`、`PROBE math.tointeger=nil`）。
怀疑语言语义差异时先跑它；两端输出逐字一致即排除 L5 层。

## 4a. 探针工程 v2（MD3 仪表盘 + 分组报告，`docs/reference/project/test/`）

- 入口 `main.lua`：入口处 print `UI_ERROR: <err>` 兜底 + 每区块 `RENDER_ERROR: <err>` 捕获（PITFALLS §15）；区块卡片 + hero 总分 + 复制/分享/重新检测按钮。
- 表达式求值 `ev(expr)`（engine.lua）：先 `load("return "..expr)` **原样编译**，失败才补 `"return "..` 前缀（避免 `return return`/`return local` 双 return）；返回 pcall 语义 `(true,值)/(nil,err)`。
- `val(v)` 归一化显示：`nil`→`"nil"`、`-nan`→`"nan"`（PITFALLS §11）；截断 >120 字符。
- 分组写法：`M.sections={ {key="arith",title="算术运算符",fn=function() ... end} }`，`item(expr,val,pass)` 产出行。
- 平台相关项（`os.getenv`/`os.execute`/`gcinfo`/`newproxy`）标 level 3（信息，不计分）；`fcases` 循环把 level 作为第 4 参传给 `item`。
- 桌面冒烟（提交前必跑）：`lua53.exe` + `package.path` 指向工程目录，`require("engine")` 后驱动 sections；非 Android 项（Android 环境/原生库组）预期失败属正常。
- 语法检查：`luac53 -p`（工具位于 `C:\Users\liois\AppData\Local\Temp\opencode\lua53\`）。

## 4b. UI 断言（模型读不了截图时）

`uiautomator dump` 的 XML 里 `text=` 节点即可断言（hero 总分、区块标题、按钮）；截图仅 `cmd /c "adb exec-out screencap -p > file"`（PowerShell `>` 损坏二进制），两者存 `%TEMP%\opencode\verify\`。

## 5. libs 编译 .dex（`docs/reference/libs/` 用法）

```powershell
# 用 d8（AGP 自带，路径示例，按本机 SDK 调整）把 jar/class 打成 dex
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<ver>\d8.bat" --lib "$env:LOCALAPPDATA\Android\Sdk\platforms\android-<ver>\android.jar" --output <工程>\libs input.jar
```

- 产出 `.dex` 放进 `<project>/libs/`，双端验证必须用**同一份**文件（先 `md5sum` 确认）。
- `import "xxx:com.foo"` 的 `xxx` 前缀由库加载规则决定；解析失败走 PITFALLS §2 降级路径。
- 全链路细节见 `docs/completed/LIBS_SUPPORT_WORK_ORDER.md`。

## 6. 诊断命令参考

```powershell
adb logcat -c
adb logcat -d -s lua:* AndroidRuntime:E        # 引擎错误 + 崩溃
adb exec-out screencap -p > verify\<name>-<side>.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell pidof com.androlua                    # 进程存活
adb shell "dumpsys activity activities" | Select-String topResumedActivity
```

## 7. 最佳实践清单

1. **先 pro 后 lide**：pro 不通就别碰 lide；lide 不一致先查白名单再查分层。
2. **报错一致 = 正常**：工程自身 bug（如 demo md5）必须在两端同样报错，那不是 lide 缺陷。
3. **工程 libs 双端同份**：dex/jar/so 两端 md5 一致，避免"差异"其实是文件不同。
4. **每轮落库**：新能力/问题/方法论收尾前写入本知识库（README 维护约定），留证据链。
5. **不带 -n 的裸 VIEW 不可用于自动化**（见 PITFALLS §3）。
6. **依赖 pro 独有能力前先查 FEATURES §8**（nirenr 自动化、LuaEditor、扩展控件等），避免写完才发现 lide 不可用。
7. 交付 `.luac` 字节码必须同源引擎编译（PITFALLS §8）。

## 8. 原生 .so 直调 + 手势/矩阵复刻方法论（Live2D 看板娘案例）

Live2D 走 lide 原生引擎（`require "live2d"` → liblive2d.so）时，Lua 侧必须复刻 pro Java 层的**全部矩阵与触摸管线**，因为引擎只暴露原语（load/update/draw/hitTest/startMotion），手势与命中判定在 Java 层（`LAppView`/`TouchManager`/`L2DViewMatrix`/`L2DTargetPoint`/`L2DModelMatrix`）。方法：用 pro APK 的 `jp.live2d.*` dex（baksmali）逐方法实证公式，再在 Lua 复刻。关键实证结论：

- **模型矩阵**：C++ `L2DBaseModel` 构造 `L2DModelMatrix(canvasW, canvasH)` + `setWidth(2)` + `setCenterPosition(0,0)`；`L2DModelMatrix.cpp` 的 setWidth/translate/scale 均为**绝对赋值**（非累乘）→ Lua 等价公式 `scale(2/cw, -2/cw)` + `translate(-1, ch/cw)`。haru 实测矩阵 `[2/2400, -2/2400, -1, 1.3333]` 与公式一致。
- **deviceToScreen** = `S(2/w, 2/w) · T(-w/2, +h/2)`（前乘链）；`transformY(y) = (y + h/2) · 2/w`。
- **viewMatrix 初始**：max=2.0 / min=0.8 / maxScreenRect=(-2,2,-2,2) / screenRect=(-1,1,-aspect,aspect)；`adjustScale/adjustTranslate` 的 isMax/isMin 用**严格相等**比较（精确 clamp 后为 2.0/0.8）；单指拖拽平移经 `moveX·deviceToScreen.scaleX` 缩放。
- **MVP** = `Ortho(±1, ±aspect) · viewMatrix · modelMatrix`（列主序 16 表），传给 `draw(m, mvp)`。
- **L2DTargetPoint.update**（平滑拖拽）：常量 MAX_A=0.177778、FRAME_TO_MAX_SPEED=4.5、FRAME_RATE=30；theta=dt·30/1000；加速度限幅 a=theta·MAX_A/FRAME_TO_MAX_SPEED；速度限幅 maxV=(√(a²+8a·d)−a)/2；注意 `0.15`/`5.33333` 是死常量不参与运算。
- **命中判定**：Lua 传**模型空间坐标**（modelMatrix.invertTransform 后）给 `hitTest(m, area, x, y)`（C++ 直接 bbox 比较，注释 "input already transformed"）；Java 的 `hitTestSimple` 才做逆变换，两路径空间语义不同，不要混用。
- **事件→动作映射**：tap 命中 head→`setRandomExpression`、body→`startRandomMotion("tap_body")`；flick→`startRandomMotion("flick")`；maxScaleEvent→`"pinch_out"`、minScaleEvent→`"pinch_in"`。
- **双指捏合**：scale = `(dist/prev)^0.75`（**指数 0.75** 不是线性）；`touchBegan2` 首帧中心用旧 lastX1/lastX2（原版 bug 复刻）；`calcShift` 同号→0 否则 sign(v1)·min(|a|,|b|)；moveY 负号。
- 设备验证：`input tap/swipe` 驱动 + logcat 断言（`tapEvent HIT head/body`、`flickEvent`、`maxScaleEvent/minScaleEvent`）+ 截图像素统计（System.Drawing 采样非黑点）三重证据链。
- 完整实现：`C:\Users\liois\AppData\Local\Temp\opencode\live2dtest\live2dapp.lua`（17KB，公式逐条带 dex 行号注释）；引擎 API 表见 `build-scripts/live2d/src/lua/lua_live2d.cpp`。
