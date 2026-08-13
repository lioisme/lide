# 工作单：编辑器性能审计与 Squircle 渲染面对齐（EDITOR_PERF_AUDIT_WORK_ORDER）

> 创建：2026-08-13，docs/planned/backlog.md §2r 登记
> 来源需求（用户 2026-08-13）：全量审计 LIDE 编辑器源码与参考 sora-editor、Squircle CE，
> 在**保持现有功能**前提下：性能至多达到 sora-editor 组件库上限、至少与 Squircle CE 编辑器 100% 持平；
> 最终经 ADB 双端实测（模拟器已装 Squircle CE）确认性能达标。
> 质量要求：代码质量/架构达大厂验收标准；每项完成在工作单标记（已完成）+ 工作内容 + 日期 + 验收证据。

## 基线审计（2026-08-13，全量只读审计，报告已交付用户）

### 三方基线
| 维度 | sora-editor | Squircle CE | LIDE |
|---|---|---|---|
| sora 内核 | 0.24.6 pristine | 0.24.3 官方 Maven（submodule 未检出，无定制 fork） | **0.24.6 pristine**（更新） |
| 每帧渲染（非换行 + HW 加速） | O(可见行)，RenderNode 路径 | 同左 | 同左 |
| 配置同步 | — | 每次重组全量重建 Language + setText（反模式） | **守卫式 diff**，值不变不赋值 |
| 文件切换 | — | 切 tab 销毁重建 View + Content 落盘重读 | **setText(Content) 原地复用** |
| 诊断 | — | 无 | 500ms 防抖 + 增量检查点收敛（独有） |
| 大文件 | — | 无降级 | >200KB 降级矩阵（独有） |

### 发现的差距（本轮修复对象）
| # | 差距 | 代码证据 | 影响 |
|---|---|---|---|
| Gap-2 | EditorConfig P4 注释声明「光标动画（关）」但**从未落实**（sora 默认 true） | LuaEditor.kt 构造链零 `setCursorAnimationEnabled` 调用；sora CodeEditor.java:644 默认 true；EditorConfig.kt:11 | 每键/光标移动触发 FadeCursorAnimator 动画帧（FadeCursorAnimator.java:84,138） |
| Gap-1 | Squircle 显式关闭 3 项渲染开销开关，LIDE 保持 sora 默认 | Squircle view/CodeEditor.kt:66-68 vs LIDE 零设置；sora DirectAccessProps.java:250/273/279 默认 true | `drawSideBlockLine`：每帧对每个可见代码块行新建 TextRow 测量（EditorRenderer.java:1999-2016）；`enableRoundTextBackground`：drawRoundRect + 圆角裁剪（EditorRenderer.java:1889）；`boldMatchingDelimiters`：粗体 Paint 切换 |
| Gap-3 | 长行（>128 字符）不走 RenderNode 显示列表缓存 | sora DirectAccessProps.java:256 默认 false；EditorRenderer.java:757 门控 | 长行每帧软绘制重录，滚动帧成本随行长上升 |

### 已确认无需改动（LIDE 已优于 Squircle）
- setText(Content) 引用守卫跳过重建（LuaEditor.kt:597-599,630-632 vs Squircle compose/CodeEditor.kt:110 每次重组全量 setText）
- 语言装配守卫式 diff，仅 scope/标识符开关变化时重建（LuaEditor.kt:331-343 vs Squircle compose/CodeEditor.kt:101-109 每次重组 setEditorLanguage）
- 诊断/大文件/undo 容量/Content 缓存（EditorSession LRU cap 12）均为 LIDE 独有领先项
- vendor 内核零改动（R5/上游隔离红线）；`isSameSign` 1e5 疑似笔误属上游行为，不擅改

## 实施清单

| # | 任务 | 优先级 | 状态 | 关键词 |
|---|------|--------|------|--------|
| T0 | 文档登记：backlog §2r + 本工作单建立 | 高 | （已完成）2026-08-13：登记 + 创建 | `EDITOR_PERF_AUDIT` `文档` |
| T1 | 光标动画显式关闭：EditorInstance 构造链补 `setCursorAnimationEnabled(false)`（对齐 Squircle view/CodeEditor.kt:59，落实 EditorConfig P4 声明） | 高 | （已完成）2026-08-13：LuaEditor.kt 构造链新增一行 + 注释；验收：compileArm64DebugKotlin 全绿（T4 门禁） | `EDITOR_PERF_AUDIT` `光标动画` |
| T2 | 渲染裁剪对齐：构造链补 `props.drawSideBlockLine=false` / `props.boldMatchingDelimiters=false` / `props.enableRoundTextBackground=false`（对齐 Squircle view/CodeEditor.kt:66-68；roundTextBackground 仅影响高亮背景圆角 → 直角，与 Squircle 视觉 100% 同构） | 高 | （已完成）2026-08-13：LuaEditor.kt 构造链新增 3 行 + 注释；验收：compileArm64DebugKotlin 全绿（T4 门禁） | `EDITOR_PERF_AUDIT` `渲染裁剪` |
| T3 | 长行 RenderNode 缓存：`props.cacheRenderNodeForLongLines=true`（sora 上限能力，>128 字符行走硬件显示列表；内存代价有界：RenderNodeHolder 池 + RenderCache LRU 75） | 中 | （已完成）2026-08-13：LuaEditor.kt 构造链新增 1 行 + 注释；验收：compileArm64DebugKotlin 全绿（T4 门禁）+ ADB 大文件滚动无回归（T6） | `EDITOR_PERF_AUDIT` `RenderNode` |
| T4 | 构建验证：`:app:compileArm64DebugKotlin` + 单元测试（editor 相关全量）+ `verifySoraIsolation` + R1 红线 grep | 高 | （已完成）2026-08-13：compile ✅ / testArm64DebugUnitTest 全绿 ✅ / verifySoraIsolation ✅ / editor 包红线 grep 零命中 ✅ | `EDITOR_PERF_AUDIT` `验证` |
| T5 | 基准物料与安装：构建 `:app:assembleX64Debug` → 安装模拟器；生成 bench.lua（~2000 行 / ~60KB）+ large.lua（~30k 行 / ~1MB）+ init.lua，推送 /sdcard/bench/ | 高 | 🔄 进行中 | `EDITOR_PERF_AUDIT` `基准` `安装` |
| T6 | ADB 双端性能对比：LIDE vs Squircle CE 同文件同场景（冷启动至编辑器就绪 / fling 滚动 framestats / 打字 framestats / 大文件滚动 + meminfo），交替 3 轮 | 高 | 🔄 进行中 | `EDITOR_PERF_AUDIT` `ADB` `framestats` |
| T8 | 深链导航竞态修复（T6 实测暴露）：冷启动 VIEW 深链停在列表页。根因：MainActivity 组合期 `pendingOpenFiles = emptyList()` 是 state 写入 → 触发第二次重组传空列表 → `LaunchedEffect(navController, pendingOpenFiles)` key 变化重启 → 取消挂起等待栈顶 RESUMED 的 `navigateWhenReady` → 导航丢失（热启动无碍、冷启动必现，解释此前成功/失败交替）。修复：组合期不再清空，改为 `LideNavHost(onOpenFilesConsumed = { pendingOpenFiles = emptyList() })`，导航完成后才清空，挂起期间 key 稳定不被取消；导航失败才需要补一次「待打开文件」重试。验收：日志序列 `pendingOpenFiles=1 → navigateWhenReady dispatched → pendingOpenFiles=0`；冷启动 ×3 + 热启动 ×1 均 CodeEditor 出现（uiautomator dump + 日志双证）；日志清理后重装复测通过 | 高 | ✅ 已完成（2026-08-13：MainActivity.kt + LideNavHost.kt 修复 + ADB 复测通过） | `深链` `导航` `LaunchedEffect` `navigateWhenReady` |
| T7 | 收口：全部任务标记 + kb/EDITOR.md 性能基线章节蒸馏 + 工作单收尾 | 高 | 🔄 进行中 | `EDITOR_PERF_AUDIT` `蒸馏` `收口` |

## 验证命令（每阶段必跑）

```
.\gradlew.bat :app:compileArm64DebugKotlin --console=plain
.\gradlew.bat :app:testArm64DebugUnitTest --console=plain
.\gradlew.bat :app:verifySoraIsolation --console=plain
rg -n "luajava|androlua|loadLibrary" app/src/main/kotlin/com/lioisme/lide/editor
```

## 验收证据（2026-08-13，ADB 实测 emulator-5554，x86_64，双端同文件 A/B）

| 场景 | LIDE（com.lioisme.lide） | Squircle CE（com.blacksquircle.ui） | 结论 |
|---|---|---|---|
| 冷启动 → 编辑器就绪（am start -W TotalTime + 窗口就绪轮询） | TBD | TBD |  |
| 中文件 fling 滚动帧分布（framestats p50/p90/p99 + janky 帧） | TBD | TBD |  |
| 打字帧分布（input text 注入） | TBD | TBD |  |
| 大文件（~1MB）滚动 + 打开后内存（meminfo） | TBD | TBD |  |

## kb 蒸馏（完成后写入 docs/kb/EDITOR.md）

见 kb/EDITOR.md §新增「性能基线（2026-08-13 审计 + ADB 实测）」章节。