# 编辑器菜单 / 撤销重做 / 设置上限整改工作单（EDITOR_MENU_UNDO_SETTINGS）

> **登记**：2026-08-10，docs/planned/backlog.md
> **来源需求**（用户 2026-08-10）：
> 1. 编辑器 Appbar 菜单按钮符合「文件 / 编辑 / 工程」行业惯例；
> 2. 编辑菜单可直达编辑器设置界面，工程菜单可直达 AndroLua 设置界面；
> 3. 编辑器设置界面全部功能正常，功能/性能达到 sora-editor 上限（参考本项目内 Squirce CE 源码）；
> 4. 撤销 / 重做按钮对齐 sora-editor 上限（参考 Squirce CE 源码）。
> **基线审计**（2026-08-10，全量比对 vendor sora-editor 0.24.x 源码 + squircle-ce-master）：
> - 菜单仅有 工程/文本 两分类（无 文件/编辑），编辑无「编辑器设置」、工程无「AndroLua 设置」直达；
> - 编辑操作缺 剪切/复制/粘贴/全选；文件操作（保存/全部保存/重载/关闭类）无菜单入口；
> - undo/redo 状态只在 `ContentChangeEvent` INSERT/DELETE 分支重算（`LuaEditor.kt`），
>   `ACTION_SET_NEW_TEXT`（文件切换）与首次 attach 不重算 → 切标签后按钮灰态残留；
>   只读模式未禁用 undo/redo（Squircle `EditorViewModel` 三时机显式同步 + readOnly 门控）；
> - 设置页 wordWrap 与 stickyScroll 互斥无提示（sora 渲染器静默忽略）；undo 栈硬编码 2500/200
>   且不可设置；字体族 / 行高 / 粘性滚动行数等 sora 上限能力未暴露。

## 决策记录（2026-08-10 用户确认）

| 决策点 | 结论 |
|---|---|
| 菜单形态 | 保持 ⋮ 聚合，内部重组为 文件 / 编辑 / 工程 三个一级分类（cascade 子菜单） |
| 文件菜单范围 | 本期**不**加「打开文件（系统选择器）」，仅 保存/全部保存/重新加载/关闭标签/关闭其他/关闭全部 |
| 设置上限扩展 | 一并扩展：字体族（typefaceText/typefaceLineNumber）、行高（setLineSpacingMultiplier）、undo 栈容量（setMaxUndoStackSize）、粘性滚动行数（props.stickyScrollMaxLines） |

## 任务清单

- [x] **T1 文档登记**：backlog 登记 + 本工作单建立（条约 + 审计结论 + 决策记录）
  （已完成 2026-08-10：backlog.md §2e 三行登记；本工作单含基线审计证据定位与验收标准）
- [ ] **T2 撤销/重做状态同步修复（需求 4）**
  - [ ] T2.1 `EditorViewPool`：`lastCanUndo/lastCanRedo` 提升为池级字段；`ACTION_SET_NEW_TEXT`
        分支同样重算并回调；`attachCallbacks` 立即推送一次当前状态（对齐 Squircle 三时机）
  - [ ] T2.2 `EditorScreen`：undo/redo 点击执行后显式重算并提交会话；readOnly 时禁用（对齐 Squircle）
  - [ ] T2.3 单测：`EditorSession.onUndoRedoChanged` 值变化才发射（含已有用例核对）
- [ ] **T3 菜单重组 文件/编辑/工程（需求 1、2）**
  - [ ] T3.1 字符串资源：`menu_category_file` / `menu_category_edit` / 保存 / 全部保存 / 重新加载 /
        剪切 / 复制 / 粘贴 / 全选 / 编辑器设置 / AndroLua 设置（中英双语）
  - [ ] T3.2 `EditorTextOps`：剪贴板四件套（`cutText`/`copyText`/`pasteText`/`selectAll`）入「编辑」分类
  - [ ] T3.3 `EditorSession.reload(fileName)`：丢缓存重载（undo 栈随 Content 重置）+ 单测
  - [ ] T3.4 `EditorScreen`：新回调 `onEditorSettings` / `onAndroLuaSettings`；moreActions 三分类重组
  - [ ] T3.5 `LideNavHost`：接线既有路由（EDITOR_SETTINGS / ANDROLUA_SETTINGS）
- [ ] **T4 设置互斥修复（需求 3）**
  - [ ] T4.1 wordWrap 开启自动禁用 stickyScroll + sttickyScroll 行置灰（对齐 Squircle 互斥）
  - [ ] T4.2 undo/redo 只读门控在工具栏 enabled 表达式落实（与 T2.2 一致）
- [ ] **T5 设置上限扩展（需求 3）**
  - [ ] T5.1 `EditorConfig` +4 字段（fontFamily / lineSpacingMultiplier / undoStackSize / stickyScrollMaxLines）
  - [ ] T5.2 `SettingsRegistry` 键表扩展（含 FloatKey 支持）
  - [ ] T5.3 `LuaEditor.applyConfig` 守卫式 diff 追加 4 项 + `acquire` 初值；undo 栈容量接入
        大文件降级（min(undoStackSize, 200)）
  - [ ] T5.4 `EditorSettingsScreen` 新行（字体族选择对话框 / 行高倍率 / undo 栈容量 / 粘性行数）
- [ ] **T6 验证与收口**
  - [ ] T6.1 `:app:compileArm64DebugKotlin` + 相关单测
  - [ ] T6.2 `:app:verifyMinimalism`（架构红线 R1–R4 自查：零 engine 依赖）
  - [ ] T6.3 本工作单全文标注（已完成）备注 + 结论蒸馏（docs/kb/EDITOR.md 如需）

## 验收标准

1. ⋮ 菜单含 文件 / 编辑 / 工程 三分类；编辑类含 剪切/复制/粘贴/全选 + 行操作 + 编辑器设置；
   工程类含 工程信息/权限/打包 APK + AndroLua 设置；点击直达对应页面，返回后会话完整恢复。
2. 切换标签后撤销/重做按钮灰态即时正确（含共享 undo 历史的标签往返）；只读文件按钮禁用；
   退出设置页再回编辑器不残留旧状态。
3. 设置页 wordWrap 开 → stickyScroll 自动关且置灰；25 项设置（21 旧 + 4 新）全部经守卫式 diff 生效；
   大文件降级矩阵保持 min(undoStackSize, 200)。
4. 全部改动零违反 AGENTS.md R1–R5（编辑器包无宿主依赖、无 luajava/androlua 触碰）。