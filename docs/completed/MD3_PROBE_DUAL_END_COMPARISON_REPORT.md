# MD3 探针工程双端对比报告（pro vs lide）

- 日期：2026-08-01（终版：MD3 组件落地）
- 设备：emulator-5554（Android 14，x86_64，adb root）
- 工程：`docs/reference/project/test/`（main.lua / engine.lua / ui.lua / init.lua，只改工程不改双端）
- 结果：**pro 96% (834/865)**、**lide 96% (832/865)**，37 章 / 865 项（计分口径，另含 level 3 观测项）
- UI：lide = **真 MD3 组件**（dumpsys 实证），pro = 系统组件降级（无 Material 类）

## 1. 执行方式

```
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/AndroLua/project/test/main.lua" \
  -n com.androlua/com.androlua.LuaActivity        # pro
  -n com.lioisme.lide/com.androlua.LuaActivity   # lide
```

- 日志：`adb logcat -d -s lua:*`（报告逐行 print，避免单条超限截断，1028 行完整）
- UI 真实类名：`adb shell dumpsys activity top | Select-String "material"`（uiautomator dump 显示的是 a11y 类名，见 PITFALLS §16）
- 证据归档：`%TEMP%\opencode\verify\test-pro-final3.log`、`test-lide-final3.log`、`ui-lide-mdc*.xml`、`ui-dialog-test.xml`

## 2. 覆盖基线（对照原版 v12）

| 口径 | 原版 v12（真机） | 本次（模拟器） |
|------|-----------------|----------------|
| 计分项 | 851 行 / 62 章节标记（日志 39 章） | 865 项 / 37 章 |
| 得分 | pro 879/956 (92%)、lide 878/956 | pro 834/865 (96%)、lide 832/865 (96%) |
| 章节 | 版本/数值/类型/位运算/算术/关系/字符串/表/元表/协程/错误/模块/标准库/Android/原生库/性能/长度/变长/闭包/递归/弱表/作用域/控制流/函数/语法/5.x矩阵/全局/安全/内存/调试/字节码/包深度/Java桥/扩展库/ALP/危险/哈希 | 同（新增 位运算 独立章、扩展库全览 35 项、ALP 全局 13 项、Android 类清单 16 项） |

覆盖标准达标：865 > 851 项；章节覆盖原版 39 章全部主题，新增 位运算/扩展库/ALP/Java类可用率 等观测维度。

## 3. 双端差异清单（全部白名单）

| # | 项 | pro | lide | 定性 |
|---|----|-----|-------|------|
| 1 | WRITE/READ_EXTERNAL_STORAGE | 已授予 | 未授予 | targetSdk 23 vs 36 系统级差异（PITFALLS §12） |
| 2 | zip require | 失败 | 失败（同模式） | 行为一致（PITFALLS §14） |
| 3 | setfenv/getfenv | false | false | 与原版 v12 同样 [--]（行为一致） |
| 4 | gcinfo/newproxy | N/A | N/A | 5.1 遗留 API，双端同 |
| 5 | Http 全局 | false | false | 与原版 v12 同样 [--]（行为一致） |
| 6 | 扩展库 24 项（lpeg/ffi/struct/ssl/rapidjson/copas/sha2/sha1/bit/bitop/iup/wx/lgi/ltk/qtwidgets/tekui/plplot/proAudio/lmdb/torch/nn/image/audio/magick/opencv） | 无 | 无 | 与原版 v12 同样"无/超时"（行为一致） |
| 7 | 其余 830 项 | 逐字一致 | 逐字一致 | — |

## 4. 关键实证（落库于 `docs/kb/androlua-lua/PITFALLS.md` §16-23）

- **MD3 组件落地（lide）**：`dumpsys activity top` 实证 MaterialCardView×38（hero+37 章卡）、LinearProgressIndicator×38、MaterialButton×3（复制/分享/重跑）、MaterialTextView×N；MaterialAlertDialogBuilder 构造+show 成功（详情对话框）。
- **pro 降级分支**：无 Material 类（md3check 已证），自动走系统组件（CardView 替代圆角容器、TextView 按钮、手搓进度条、AlertDialog.Builder），无 UI_ERROR。
- **AndroLua 反射限制**：`setIndicatorColor(int.../ColorStateList)`、`setShowTrack(boolean)` 不可用 → 组件配置整体 pcall + 进度色用主题默认 M3 primary（§17）。
- **uiautomator 类名陷阱**：dump 显示 a11y 类名（MaterialCardView→androidx.cardview.widget.CardView）→ 用 dumpsys activity top（§16）。
- **pro 平台行为差异（已对齐口径）**：md5 1.2 仅 raw（§19）、utf8.offset 非标准语义（§20）、newInstance 构造不可用（§21）、x86 构建 double 模式（§23）、`pcall(loadstring)` 立即执行陷阱（§18）。
- **logcat 单条上限**：大报告必须逐行 print（§22）。

## 5. 遗留

- zip require 双端一致失败，根因留待物理设备深挖（同前版报告）。
- 整数精度（2^53+1）：模拟器 x86 pro 为 double 模式降为观测项；真机 arm64 预期 int64（原版实证），留待真机复核。
- 对话框视图类为 appcompat（MD3 主题样式生效，类名非 material）——外观级 MD3 验证需真机目测/截图。

## 6. 回归

- 双端重跑无 UI_ERROR/RENDER_ERROR/崩溃；桌面冒烟 98% (737/748)（仅平台项失败）。
- 探测项 865 vs 原版 851 覆盖达标；双端差异仅白名单 2 分（权限），其余 830 项逐字一致。
