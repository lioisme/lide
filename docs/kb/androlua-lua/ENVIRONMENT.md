# 运行环境与配置（Runtime Environment & Configuration）

> 双端验证的运行环境事实基础。改动环境（换模拟器/重装/换设备）时先更新本文件。

## 1. 设备与通信

- 本机运行 **x64 架构安卓虚拟机**，通过 ADB 通信，默认设备 `emulator-5554`。
- 前置检查：`adb devices` 必须显示 `device`（非 `offline`/`unauthorized`）。

## 2. 双端 app

| 项 | AndroLua Pro 原版 | lide |
|---|---|---|
| 包名 | `com.androlua`（4.4.2） | `com.lioisme.lide` |
| 源码位置 | `AndroLuaPro v4.4.2 上游（运行时代码 `third_party/androlua/`、引擎 `app/src/main/cpp/`）` | 本仓库（`:androlua` / `:app` / `:editor`） |
| 运行时脚本 | `app/src/main/resources/lua/`（28 个） | `app/src/main/assets/lua/`（逐字节一致） |
| 宿主入口 | `com.androlua.LuaActivity` 直挂 manifest | `EngineActivity` → `LuaActivity` 转发 |

安装确认：`adb shell pm list packages` 应同时含 `com.androlua` 与 `com.lioisme.lide`。

## 3. 设备工程根

```
/storage/emulated/0/AndroLua/project/<name>/
├── init.lua   # 元数据：appname/appver/appcode/appsdk/packagename/developer/description/debugmode/user_permission
├── main.lua   # 入口
├── icon.png   # 图标（可选）
└── libs/      # 工程内库：*.jar / *.dex / lib<fn>.so（.alp 导出即携带）
```

参考工程（pro 已验证可运行）：`reference/project/` 下的
demo、test、testjson、kleinbottle、DemoProject、probe、builddemo、「Sora editor」、简、石头、看板娘、雷达统计图。

## 4. 推工程与双端启动（实测有效）

```powershell
adb shell mkdir -p /storage/emulated/0/AndroLua/project/<name>
adb push reference\project\<name> /storage/emulated/0/AndroLua/project/<name>

# pro：必须带 -n 指定组件，否则进 ResolverActivity 选择器
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.androlua/com.androlua.LuaActivity

# lide：同一 intent 换组件
adb shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/AndroLua/project/<name>/main.lua" -n com.lioisme.lide/com.androlua.LuaActivity
```

- `androlua://` scheme 只进各自主程序 UI（需手工点工程行 → 点「运行」），仅作 UI 路径抽查。
- 每次启动前 `adb shell am force-stop com.androlua`（或 lide）；抓日志前 `adb logcat -c`。

## 5. 构建（lide 侧）

- 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`
- 模拟器用 x64：`.\gradlew.bat :app:assembleX64Debug --console=plain`
- 回归：`scripts/adb_regress.ps1 [-Device emulator-5554] [-SkipBuild]`（6 项矩阵）

## 6. 日志与基线

- 引擎输出通道：logcat 标签 `lua`（`adb logcat -d -s lua:* AndroidRuntime:E`）。
- 双端基线：`docs/log/androluapro.log`（pro）、`docs/log/lide.log`（lide）——同一 test 工程 956 项 probe（历史资产，lide 仓库未收录）。
- 临时对比日志统一存 `%TEMP%\opencode\`。

## 7. 库源码（可编译 .dex 进工程 libs/）

`reference/libs/`：BaiduMTJ、SmartRefreshLayout、umeng 等。
编译方法见 `TECHNIQUES.md §5`。

## 8. 模拟器坑位（2026-08-01 实测）

- **写 /sdcard 报 EPERM = 缺 `MANAGE_EXTERNAL_STORAGE`**（appops 默认 `default`，应用不可写共享存储任意路径）。重装（`adb install -r`）或首次安装后必须：
  `adb shell appops set com.lioisme.lide MANAGE_EXTERNAL_STORAGE allow`
  症状：`ZipUtil` 解包文件 `FileOutputStream` 抛 `EPERM`（目录能建、文件写不进），`appops get` 可见 `rejectTime`。Android 11+ 共享存储文件属主 `u0_a99`（media_provider）属正常现象，**不是**属主 bug。
- **`adb exec-out screencap -p > file` 在 Windows 下损坏 PNG**（socket 二进制损坏）。改用两步：`adb shell screencap -p /sdcard/x.png; adb pull /sdcard/x.png`。
- **`adb uninstall <pkg>` 可能报 `DELETE_FAILED_INTERNAL_ERROR`**（模拟器状态问题）。用 `adb install -r` 覆盖安装即可。
- **`adb install -r` 保留 appops 与 /sdcard 工程数据**（数据隔离，卸载才清）。实测重装后 `MANAGE_EXTERNAL_STORAGE` 仍为 allow。
- **uiautomator dump 中文文本在 PowerShell 控制台乱码**，但 `bounds` 可用；文本定位不可靠时按坐标点击。大截图无法内嵌模型分析，验证以 UI dump + logcat 为准。
- **删除 host 测试工件**：PowerShell 非交互模式 `Remove-Item` 受限，用 `cmd /c "del /q <path> 2>nul"`。

## 9. 示例 .alp 资产与导入

- 资产源：`scripts/examples-src/`（6 工程）；生成：`scripts/make-examples.ps1`（或 Gradle `:app:generateExamples`）→ `app/src/main/assets/examples/*.alp`。
- **改了示例源必须重新生成 .alp 并重建 APK 重装**，否则导入的是旧内容（实测教训：listview 数据元素修成 LuaTable 后未重新生成，lide 端复现 pro 同款 ClassCastException；重新生成 + 重装 + 重新导入后双端一致）。
- 导入目录名由 init.lua 的 `appname` 决定，与 .alp 文件名无关（例：`TestJson.alp` → 工程目录 `TestJson/`，appname `com.mycompany.myapp3`）。
- 导入覆盖语义：重新导入会覆盖已存在目录内容（实测 listview 覆盖导入生效）。
