# 编辑器对标 Squircle CE 整改工作单（EDITOR_SQUIRCLE_ALIGNMENT）

> **登记**：2026-08-11，docs/planned/backlog.md §2g
> **来源需求**（用户 2026-08-11，四项投诉 + 三项拍板）：
> 1. 只读模式不是真只读，要求实现 Squircle CE 式真只读；
> 2. 编辑器功能疑似「奇技淫巧」，要求全部重新审计——所有功能必须是 sora-editor 原生
>    支持，性能/功能/优雅度至少达到 Squircle CE；
> 3. 行号与内容要有分界线（像 Squircle CE 一样）；
> 4. 编辑器设置中大量「最佳配置/自适应配置」项无需用户设置，要求移除。
> **用户拍板**（2026-08-11）：
> - P2 去池化：删除 EditorViewPool，回归朴素生命周期；
> - P5 纳入查找/替换 + 跳行功能补缺；
> - P4 确认移除 7 项设置（27 → 20：注册键 17 Bool + 3 Int + 7 冗余键 = 27，删 7 后为 20）。
> **质量要求**：代码质量/架构达大厂验收标准；UI 优美简约、MD3 规范、官方组件库。

## 基线审计（2026-08-11，全量比对 vendor sora-editor 0.24.x 源码 + reference/squircle-ce-master）

- **只读**：LIDE 现状 = `isEditable`（sora 原生门禁，真）+ ReadOnlyBanner /
  菜单置灰 / 手动守卫 / 冗余 IME 修补（伪 UX）。squircle 仅 `editor.isEditable =
  !settings.readOnly`（compose/CodeEditor.kt:94）+ ViewModel 写命令静默 return，
  无横幅无置灰；切换入口 = 扩展键盘锁定按钮（LIDE 无扩展键盘 → 需 ⋮ 菜单直达项）。
  sora `setEditable(false)` 原生即 hideSoftInput + stopSnippet（CodeEditor.java:3324-3330）。
- **视图池**：`EditorViewPool` 为进程级单实例 CodeEditor 池（LuaEditor.kt:94-397），
  squircle 无同类物（remember + onRelease release）；池化需手工 setText(null) 出池
  清引用（2026-08-10 A5 已踩跨工程泄漏）——判定为绕过 sora 视图生命周期的奇技淫巧。
- **行号分界线双零 bug**：① `EditorConfig.lineNumberDividerWidth = 0`（默认关闭）；
  ② sora `TextMateColorScheme.applyVSCTheme/applyTMTheme` 无条件
  `setColor(LINE_DIVIDER, TRANSPARENT)`（TextMateColorScheme.java:127/263）且 LIDE
  主题 JSON 无 divider 键 → 用户调宽度也画不出可见线。
- **设置冗余**：LIDE 21 键 vs squircle 暴露 ~16 + 硬编码常量（lnPanel=false、
  cursorAnimation=false、divider 1dp+主题色、undo 500）。可移除 7 项：
  stickyScrollMaxLines / lnPanel / pinLineNumber / lineNumberDividerWidth /
  cursorAnimation / lineSpacingMultiplier / undoStackSize。
- **功能缺口**：sora 原生、squircle 有、LIDE 无——查找/替换（EditorSearcher）、
  跳行对话框。

## 任务清单

- [x] **T1 文档登记**：backlog §2g 登记 + 本工作单建立（基线审计 + 决策记录）
  （已完成 2026-08-11）
- [ ] **P1 真只读重构**
  - [ ] P1.1 删 `ReadOnlyBanner`（EditorComponents.kt:79-106）与显示条件（189-192）
  - [ ] P1.2 菜单置灰去只读：保存/保存全部（EditorScreen.kt:387-397）与
        `editorTextOpsActions` 写操作（EditorTextOps.kt:24-26）不再按 editable 置灰；
        undo/redo 按钮恢复 `canUndo/canRedo` 单条件（EditorScreen.kt:362-363）
  - [ ] P1.3 `LuaEditor.applyConfig` 删冗余 hideSoftInput/restartInput（284-294）
  - [ ] P1.4 ⋮ 菜单「编辑」分类加「只读」直达切换项（勾选态）= squircle 扩展键盘
        锁定按钮的等价入口
- [x] **P2 去池化**（已完成 2026-08-11；2026-08-11 修闪退 S6）
  - [x] P2.1 `LuaEditor.kt` 重写：删 `EditorViewPool`，实例状态改局域
        （remember/factory 内）；AndroidView factory 创建、update 守卫式 diff、
        DisposableEffect onDispose 调 `editor.release()`；订阅迁移到工厂内一次性
  - [x] P2.2 诊断防抖/diagTick/签名/undo-redo 上报改实例级（每页面独立）
  - [x] P2.3 `EditorScreen` 绑定门控简化：editorReady 仍保留（瞬时绑定），
        删 8ms 轮询路径依赖（身份绑定保留）
- [x] **P3 行号分界线**（已完成 2026-08-11）
  - [x] P3.1 两主题 JSON：quietlight `colors` 加 `"editor.lineNumber.divider"`；
        darcula `settings[0].settings` 加 divider 色
  - [x] P3.2 新建 `LideTextMateColorScheme : TextMateColorScheme`（editor 包）：
        覆写 applyDefault → super 后从 rawTheme 读 divider 键映射
        LINE_DIVIDER + STICKY_SCROLL_DIVIDER（对齐 SquircleScheme.kt:111-115）
  - [x] P3.3 `TextMateRuntime.colorScheme()` 改返回新方案；`LuaEditor` acquire 固定
        `setDividerWidth(1dp)` + lineNumberMarginLeft，删 dividerWidth 设置接线
- [x] **P4 设置瘦身（27 → 20）**（已完成 2026-08-11，验收：`:app:compileArm64DebugKotlin`
  通过；字段/键/UI/字符串四处全删，全库 grep 零残留）
  - [x] P4.1 `EditorConfig` 删 7 字段（stickyScrollMaxLines/lnPanel/pinLineNumber/
        lineNumberDividerWidth/cursorAnimation/lineSpacingMultiplier/undoStackSize），
        相关常量（大文件 undo cap 200）移到 LuaEditor 内部常量
        （`DEFAULT_UNDO_STACK_SIZE=500` / `LARGE_FILE_UNDO_STACK=200`）
  - [x] P4.2 `SettingsRegistry` 删 7 键（FloatKey 类型已随行高键一并删除，
        `boolKeys` 17 + `intKeys` 3 = 20 键）
  - [x] P4.3 `EditorSettingsScreen` 删 7 行 + 相关字符串清理
        （values/values-zh 各 12 个 string；`roundToInt`/`KeyboardArrowUp` 等 import 清）
  - [x] P4.4 `LuaEditor`：undo 栈容量固定 500（大文件 cap 200，由
        `LaunchedEffect(content)` 落实）；lnPanel/cursorAnimation 不接线（恒关）；
        divider 恒 1dp（`setDividerWidth(dpUnit)`，P3 已落地）；行高删
        `setLineSpacing(0f, …)` / `setLineSpacingMultiplier` 调用（sora 默认 1.0）
- [ ] **P5 功能补缺：查找/替换 + 跳行**
  - [ ] P5.1 编辑菜单加「查找」入口；`EditorSearchPanel`（COMPOSE，MD3）：
        查找/替换输入 + 上/下一条 + 替换/全部替换 + 正则/大小写/整词 + 关闭 + BackHandler；
        接线 `EditorSearcher`（search/stopSearch/gotoNext/gotoPrevious/
        replaceCurrentMatch/replaceAll，TYPE_NORMAL/WHOLE_WORD/REGULAR_EXPRESSION），
        只读下替换按钮禁用 or 依赖 sora 门禁（EditorSearcher.java:334/385）
  - [ ] P5.2 `GotoLineDialog`（AlertDialog + 数字输入框），⋮ 菜单「编辑」入口；
        setSelection 走既有 editorRef 绑定门控；与运行错误跳行共用
  - [ ] P5.3 新字符串 + zh/en 双语
- [ ] **T6 验证与收口**
  - [ ] T6.1 `:app:compileArm64DebugKotlin` + `:app:testArm64DebugUnitTest`（build.* 改动无）
  - [ ] T6.2 `:app:verifyMinimalismDebug`（单 dex 集断言不受影响）
  - [ ] T6.3 工作单全文标注（已完成）+ 结论蒸馏（docs/kb/EDITOR.md §11 追加）+ backlog 状态回填
- [x] **T7 编辑器闪退修复（P2.1 构造期泄漏 this）**（已完成 2026-08-11）
  - [x] 现象：进入编辑器界面即闪退（稳定复现）。
  - [x] 根因：`EditorInstance.editor = CodeEditor(context).apply { setEditorLanguage(createLanguage(...)) }`
        初始化器中 `createLanguage` 内部 `attachEditor(editor)` 读取的是
        `EditorInstance.editor` 属性——此刻属性尚未赋值（值为 null）→
        Kotlin 非空参数检查 NPE（`Parameter specified as non-null is null:
        EditorLanguage.attachEditor`）。去池化（P2.1）后便在此构建路径，崩栈
        LuaEditor.kt:408 → EditorInstance.<init>:115 → createLanguage:192。
        与 TextMate Loading/Ready 状态机无关（静态推演误伤）。
  - [x] 修复：`createLanguage` 增加 `attachTo: CodeEditor` 参数；构造路径传 apply
        块接收者 `this`（LuaEditor.kt:115），applyConfig 重建路径传 `editor`
        （LuaEditor.kt:245）。杜绝初始化器中读取未赋值属性。
  - [x] 验收（x86_64 模拟器 127.0.0.1:5555，x64 debug 构建）：
        `:app:assembleX64Debug` 通过；`editor.*` 单测通过；进入 demo 工程
        main.lua 编辑器正常渲染（行号/高亮/工具栏）；暴力测试
        = 250 轮随机 tap/swipe/keyevent + IME 文本输入 + 屏幕旋转 +
        3 轮冷启动快速进编辑器 + 10 轮进出循环 + HOME 后台切换 +
        40 轮大滑动，全程 `logcat -b crash` 零 FATAL，进程存活。

## 验收标准

1. 只读下：无横幅、无菜单置灰；键盘/粘贴/剪切/替换/输入法全链路被 sora 门禁拦截；
   选择/复制/搜索/跳行可用；⋮ 菜单可一键切换只读，切回即时可输入；
   只读开启时切换前若键盘展开则自动收起（sora 原生行为）。
2. 编辑器每次进出按 `remember + release` 生命周期创建/释放，无 Content 泄漏
   （进出同一文件不闪旧文本、undo 栈随 Content 保留）。
3. 双主题下（darcula/quietlight）行号与内容之间恒有可见 1dp 分割线，
   设置页无「分割线宽度」项。
4. 设置页 20 项（27 删 7）；旧 7 项不做隐性黑箱（字段/键/UI 全删）。
5. 查找/替换面板与跳行对话框可用（MD3 控件），替换在只读下被拦截或禁用；
   正则/整词/大小写选项生效；面板关闭恢复编辑器焦点。

## 红线自查（AGENTS.md）

- P1-P5 全部改动在 `com.lioisme.lide.editor` / `com.lioisme.lide.ui.screen` /
  `com.lioisme.lide.ui.theme` / `com.lioisme.lide.session`，零 androlua/luajava 触碰；
- 编辑器 API 主线程；补全/诊断/事件回调 try/catch(Throwable) 降级保持。