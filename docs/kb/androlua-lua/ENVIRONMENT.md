# 运行环境与配置（Runtime Environment & Configuration）

> 双端验证的运行环境事实基础。改动环境（换模拟器/重装/换设备）时先更新本文件。

## 1. 设备与通信

- 本机运行 **x64 架构安卓虚拟机**，通过 ADB 通信，默认设备 `emulator-5554`。
- 前置检查：`adb devices` 必须显示 `device`（非 `offline`/`unauthorized`）。

## 2. 双端 app

| 项 | AndroLua Pro 原版 | delua |
|---|---|---|
| 包名 | `com.androlua`（4.4.2） | `com.lioisme.delua` |
| 源码位置 | `docs/reference/androlua/AndroLua_pro-master/` | 本仓库（`:androlua` / `:app` / `:editor`） |
| 运行时脚本 | `app/src/main/resources/lua/`（28 个） | `app/src/main/assets/lua/`（逐字节一致） |
| 宿主入口 | `com.androlua.LuaActivity` 直挂 manifest | `EngineActivity` → `LuaActivity` 转发 |

安装确认：`adb shell pm list packages` 应同时含 `com.androlua` 与 `com.lioisme.delua`。

## 3. 设备工程根

```
/storage/emulated/0/AndroLua/project/<name>/
├── init.lua   # 元数据：appname/appver/appcode/appsdk/packagename/developer/description/debugmode/user_permission
├── main.lua   # 入口
├── icon.png   # 图标（可选）
└── libs/      # 工程内库：*.jar / *.dex / lib<fn>.so（.alp 导出即携带）
```

参考工程（pro 已验证可运行）：`docs/reference/project/` 下的
demo、test、testjson、kleinbottle、DemoProject、probe、builddemo、「Sora editor」、简、石头、看板娘、雷达统计图。

## 4. 推工程与双端启动（实测有效）

```powershell
adb shell mkdir -p /storage/emulated/0/AndroLua/project/<name>
adb push docs\reference\project\<name> /storage/emulated/0/AndroLua/project/<name>

# pro：必须带 -n 指定组件，否则进 ResolverActivity 选择器
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.androlua/com.androlua.LuaActivity

# delua：同一 intent 换组件
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.lioisme.delua/com.androlua.LuaActivity
```

- `androlua://` scheme 只进各自主程序 UI（需手工点工程行 → 点「运行」），仅作 UI 路径抽查。
- 每次启动前 `adb shell am force-stop com.androlua`（或 delua）；抓日志前 `adb logcat -c`。

## 5. 构建（delua 侧）

- 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`
- 模拟器用 x64：`.\gradlew.bat :app:assembleX64Debug --console=plain`
- 回归：`scripts/adb_regress.ps1 [-Device emulator-5554] [-SkipBuild]`（6 项矩阵）

## 6. 日志与基线

- 引擎输出通道：logcat 标签 `lua`（`adb logcat -d -s lua:* AndroidRuntime:E`）。
- 双端基线：`docs/log/androluapro.log`（pro）、`docs/log/delua.log`（delua）——同一 test 工程 956 项 probe。
- 临时对比日志统一存 `%TEMP%\opencode\`。

## 7. 库源码（可编译 .dex 进工程 libs/）

`docs/reference/libs/`：BaiduMTJ、SmartRefreshLayout、umeng 等。
编译方法见 `TECHNIQUES.md §5`。
