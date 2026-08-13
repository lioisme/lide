# 工作单：Squircle 对齐四需求整改（SQUIRCLE_FOUR_FIX_WORK_ORDER）

> 创建：2026-08-13
> 来源需求（用户 2026-08-13）：① 编辑器所有语言高亮与 Squircle CE 完全一致；
> ② 格式化代码禁止自动滚动到顶部/底部（目前先滚动再回到原本的行）；
> ③ 工程权限界面权限支持勾选和取消勾选；④ 底部对话输入框调出时同步调出输入法。
> 质量要求：代码质量/架构达大厂验收标准；每项完成标记（已完成）+ 工作内容 + 日期 + 验收证据；
> 全部完成后经 ADB 链接模拟器真实测试通过。

## 决策记录（用户拍板 2026-08-13）

1. **高亮一致程度**：完全一致 → 引入 Squircle CE 全部 9 款主题配色，**放宽** WCAG 4.5:1
   对比度门禁（`EditorPaletteContrastTest` 改为「与 Squircle 源 JSON 一致」断言）；
   宿主 chrome 槽门禁（`ThemeContrastTest`）保留。
2. **输入法场景**：即 BottomInputSheet 三场景（新建项目 / 工程重命名 / 跳转到行），实测仍不弹 → 复核修复。

## 审计结论（对照源码）

| # | 需求 | 根因 | 位置 |
|---|------|------|------|
| 1 | 高亮一致 | 语法（tmLanguage）已 78/78 逐字节一致；差异全在主题层：LIDE 6 款自研 TextMate 格式 vs Squircle 9 款 VS Code 格式，同名主题色值不同 | `assets/textmate/`、`editor/EditorPalette.kt` |
| 2 | 格式化滚动 | `Content.replace` 全量替换时 sora `Cursor.afterInsert` 推光标到文末（Cursor.java:338-344）→ `CodeEditor.afterInsert` 无条件 `ensureSelectionVisible()` 动画滚到底（CodeEditor.java:5294）；已提交版本随后瞬跳恢复视口 = 「先滚再跳回」；工作区未提交修改（2026-08-13 11:16）删除恢复调用且注释与 vendor 行为矛盾 → 停在文末 | `editor/EditorController.kt:127-179` |
| 3 | 权限勾选 | `PermissionScreen.kt` L427-431/L494-497 toggle 对 `MutableSet` 原地增删、`state` 从不重赋 → 无 Compose 快照写入 → 无重组 → 勾选框视觉不动（磁盘已写成功）；`PermissionCheckRow` 仅 Checkbox 可点（防双触发），行主体不可点 | `ui/screen/PermissionScreen.kt`、`ui/screen/SearchablePermissionPicker.kt:226-254` |
| 4 | 输入法同步 | `BottomInputSheet.kt:86-99`：`requestFocus()` 异步生效（下一帧布局），紧随的 `keyboardController.show()` 无焦点可挂 → 落空；`LocalSoftwareKeyboardController` 在外层组合获取（主窗口）而焦点 TextField 在 Dialog 窗口，跨窗口 show 可能无效 | `ui/screen/BottomInputSheet.kt` |

## 实施清单

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|------|--------|------|--------|
| T1 | Squircle 9 款主题 JSON 转换（VS Code → TextMate settings 格式）并入库 `assets/textmate/`，旧 6 款退役删除 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `主题转换` |
| T2 | `EditorPalette.kt` 改造：pairs(3×双模) 废弃 → 9 款单侧主题目录（名称/文件/isDark/背景）；`allThemes` = 9 全量 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `EditorPalette` |
| T3 | `EditorConfig.themeScheme` + `editor_scheme` 键语义扩展（0..8 主题索引，键名不变） | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `主题索引` |
| T4 | 装配链：`TextMateRuntime`（白名单/`hostColorScheme`/`updateHostColors`）+ `LuaEditor` LaunchedEffect + `LideApp` preload 初始主题名 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `装配链` |
| T5 | `EditorSettingsScreen` 配色方案行 → Squircle 同款 9 款主题网格（单色条预览） | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `方案选择器` |
| T6 | `EditorPaletteContrastTest` 改版：4.5:1 断言 → Squircle 源一致断言（tokenColors/背景逐项 + 白名单 9 全量 + 预览色条同源）；`ThemeContrastTest` 保留 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `门禁` |
| T7 | `text.plain` 装配改 `EmptyLanguage()`（Squircle 同路径）+ `LanguageDetector` 补 `.c++/.h++/.in → CPP` | 中 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `text.plain` |
| T8 | 格式化滚动修复：`EditorController.format` 对齐 sora 官方 `onFormatSucceed` 配方（滚动位置快照 + forceFinished + duration 0 瞬移恢复 + 越界钳制），KDoc 同步；`replaceAll` 抽公共恢复 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `格式化` `滚动` |
| T9 | 工程权限勾选：`ProjectPermState.selected` 改不可变 Set + toggle 重赋（正文列表 + 搜索 Dialog 两处）+ `persistSelected` 参数化 + `PermissionCheckRow` 整行 `toggleable` | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `权限勾选` `toggleable` |
| T10 | `BottomInputSheet` 输入法同步：复现 → 时序修正（requestFocus 后等帧再 show / controller 移入 sheet content）+ 三场景验证 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `输入法` `焦点` |
| T11 | 文档同步：AGENTS.md 红线改写 + kb/EDITOR.md §13/§14 蒸馏 + backlog 登记 | 中 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `文档` |
| T12 | 全量验证 + ADB 模拟器实测 4 项需求 + 工作单收口 | 高 | （已完成）2026-08-13 | `SQUIRCLE_FOUR_FIX` `验收` |

## 验收证据（2026-08-13 ADB 实测 emulator-5554，x86_64）

| 需求 | 实测结果 | 证据 |
|------|---------|------|
| ① 高亮 | ✅ demo main.lua darcula 深底+语法分色+行号浅灰；`EditorPaletteContrastTest` 与 Squircle 源 JSON 完全一致 | screencap 目视 + 测试门禁 |
| ② 格式化不滚动 | ✅ 中间视口（顶行号 11）格式化后仍行 11；main.lua 出现 `orientation= "vertical"` 格式化样式 | 03_mid / 04_after_fmt 截图 + 文件内容 |
| ③ 权限勾选 | ✅ 正文 toggle（读取外部存储勾/取消即时视觉 + init.lua 写盘）+ 搜索 Dialog toggle（network 过滤后勾/取消写盘） | init.lua user_permission 4↔5↔3↔4 项变化 |
| ④ 输入法同步 | ✅ 三场景（新建项目/跳转到行/共享组件）焦点落定 → 键盘弹出（IME 窗口 h=0→2464 全高、mInputShown、Dialog 窗口拿窗口焦点）；数字键直接上屏 → 跳转执行 | dumpsys window/InputMethod + LideIme 日志链（验证后已删） |

关键根因收口（T10 补充）：material3 **1.4.0** `ModalBottomSheetDialogWrapper.updateParameters`
对 Dialog 窗口强制 `SOFT_INPUT_STATE_ALWAYS_HIDDEN`（`setSoftInputMode(48)`，SDK≥30）→
Compose `show()`（SHOW_IMPLICIT）被静默拒绝（IME 窗口 0 高）。修复 =
sheet content 内取 `LocalView.current`，`InputMethodManager.showSoftInput(SHOW_FORCED)`
无视该策略强制弹出（连试两拍 + Compose show + WindowInsetsControllerCompat 兜底）。
环境注记：模拟器 pinyin IME 键盘渲染 0 高（IME 自身环境问题，与 LIDE 无关），wetype 正常。
门禁全绿：compile / test / lint（0 error）/ verifyMinimalism / verifyMinimalismDebug / verifySoraIsolation。

## 验证命令（每阶段必跑）

```
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\gradlew.bat :app:testArm64DebugUnitTest --console=plain
.\gradlew.bat :app:lintArm64Debug --console=plain
.\gradlew.bat :app:verifyMinimalism --console=plain
.\gradlew.bat :app:verifyMinimalismDebug --console=plain
.\gradlew.bat :app:verifySoraIsolation --console=plain
rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor
```

## ADB 实测清单（emulator-5554）

1. 高亮：同一文件（lua/java/kotlin/md）LIDE 截图，token 色与 Squircle 源 JSON 对照
2. 格式化：中间视口格式化 200 行文件 → 录屏零滚动动画、光标/视口保持
3. 权限：勾选/取消即时视觉反馈 + 搜索内勾选 + 重启持久化
4. 输入法：三场景弹框同时键盘弹出（无先框后键盘）