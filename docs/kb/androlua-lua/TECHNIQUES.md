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
2. pro 通过后（含 .alp 打包运行）才进 delua，同一工程同一启动方式抓同一组证据。
3. 逐字 diff 两端日志/报错栈；只允许白名单差异（SKILL §6）。
4. 不一致 → 按 SKILL §5 分层（L0 工程/环境 → L1 脚本 → L2 .so → L3 Java 绑定 → L4 宿主 → L5 语言语义）收敛，修 delua 不修工程。

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

1. **先 pro 后 delua**：pro 不通就别碰 delua；delua 不一致先查白名单再查分层。
2. **报错一致 = 正常**：工程自身 bug（如 demo md5）必须在两端同样报错，那不是 delua 缺陷。
3. **工程 libs 双端同份**：dex/jar/so 两端 md5 一致，避免"差异"其实是文件不同。
4. **每轮落库**：新能力/问题/方法论收尾前写入本知识库（README 维护约定），留证据链。
5. **不带 -n 的裸 VIEW 不可用于自动化**（见 PITFALLS §3）。
6. **依赖 pro 独有能力前先查 FEATURES §8**（nirenr 自动化、LuaEditor、扩展控件等），避免写完才发现 delua 不可用。
7. 交付 `.luac` 字节码必须同源引擎编译（PITFALLS §8）。
