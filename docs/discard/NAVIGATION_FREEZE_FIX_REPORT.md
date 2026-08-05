# 导航冻结修复报告（Navigation Freeze Fix Report）

> **状态**：✅ 已完成（2026-08-04）
> **目标**：根治 navigation-compose 2.8.5 快速进出界面导致「界面可见但无法点击（波纹有、跳转无）」的冻结问题
> **验收标准**：编辑器 / 设置 / API 浏览器 / 手册各 100 次「1 秒内完成进入+退出」循环，零冻结、零崩溃
> **关联代码**：`app/src/main/kotlin/com/lioisme/lide/ui/navigation/NavigationGate.kt`（新增）、`LideNavHost.kt`（全量收敛）

---

## 1. 现象与复现

- 用户在编辑器 / 设置等界面**快速进出（约 1 秒一次，持续 30~40 次以上）**后，App 界面仍完整显示、按钮波纹动画正常，但**所有点击无任何跳转反应**，只能重启 App 恢复。
- 原始复现节奏：tap → 600ms → back → 600ms，约 40 次循环后冻结。

## 2. 根因（已定案）

### 2.1 直接机制：转场收尾回调丢失

反汇编 `navigation-compose 2.8.5` 的 `NavHostKt$NavHost$33$1`（转场收尾协程）确认：

```java
// NavHostKt$NavHost$33$1.invokeSuspend（节选）
if (transition.currentState == targetState && targetState == currentBackStackEntry) {
    onTransitionComplete();
}
```

- `onTransitionComplete()` 只在 `currentState == targetState == currentBackStackEntry` 三者相等时调用一次。
- **转场进行中（currentState ≠ targetState）发起 pop**：被 pop 的 entry 的 composable 内容仍然留在 AnimatedContent 中（滑出动画），但其 `NavBackStackEntry` 已被销毁、栈状态与转场状态失步 → `onTransitionComplete` 永不满足 → 该内容对应的 BackHandler/点击回调全部失效，但绘制与波纹输入正常 → 表现为「界面可见但死掉」。
- 该失步状态一经产生即**永久性**（无任何后续事件能触发 onTransitionComplete），直到 Activity 重建。

### 2.2 官方契约

navigation 官方文档明确规定：**「仅当当前 destination 的 Lifecycle 为 RESUMED 时才能安全地调用 navigate()/popBackStack()」**（与 `OnBackPressedCallback` 的 `enabled = lifecycle == RESUMED` 模式一致）。2.8.x 版本在转场中途导航存在已知竞态（社区 SO 78646242、issuetracker 347114499）。

## 3. 修复方案（单一漏斗门控，方案 A）

用户决策：**从源头统一解决，遵循官方规范；业务代码零改动；不依赖升级 navigation 版本。**

新增 `NavigationGate.kt`（全部为 `NavHostController` 的顶层扩展函数，与 `LideNavHost.kt` 同目录）：

| 函数 | 行为 |
|------|------|
| `isNavigationSettled()` | 当前 entry `lifecycle.currentState == RESUMED`（转场已完成） |
| `navigateGuarded(route, opts)` | 未 settle 时**静默忽略**（遵守官方「仅 RESUMED 时导航」） |
| `popGuarded(): Boolean` | 未 settle 时返回 false 且不 pop |
| `navigateWhenReady(route, opts)` | 挂起轮询（16ms 间隔，5s 超时）直至 settle 后导航（启动深链 / 非法参数回退用） |
| `popWhenReady()` | 挂起直至 settle 后 pop（非法参数回退用） |

`LideNavHost.kt` 收敛动作：
- **13 处**原生 `navigate` / `popBackStack` 全部替换为 guarded 版本；
- **11 个目的地**全部加 `BackHandler { navController.popGuarded() }`；
- 非法参数回退改 `LaunchedEffect(Unit) { popWhenReady() }`；启动 pending-open / 插件跳转改 `navigateWhenReady`。

效果：转场中任何导航请求一律被门控拦截 → `NavHost$33$1` 的失步条件从构造上不可能发生 → 冻结根除。

## 4. 验证（x64 雷电模拟器，API 34）

环境：LDPlayer14，`com.lioisme.lide` APK 安装 + 测试工程 `TestProj`；脚本 `stress.ps1`（`input tap` / `keyevent 4` + uiautomator dump 逐轮断言，UTF-8 拉取避免乱码误判，进程 PID 全程监控）。

### 4.1 真实节奏验收（1 秒内完成一次进出，即原始复现节奏，步进 500ms）

| 界面 | 路径 | 循环 | 检查点 | 结果 |
|------|------|------|--------|------|
| 编辑器 | 列表→工程卡→back | 100 | 20 | 20/20 通过，0 失败 |
| 设置 | 列表→设置(1136,224)→back | 100 | 20 | 20/20 通过，0 失败 |
| API 浏览器 | 列表→设置→API 浏览器(450,2109)→back×2 | 100 | 20 | 20/20 通过，0 失败 |
| 手册 | 列表→设置→滚动→关于(450,1853)→back×2 | 100 | 20 | 20/20 通过，0 失败 |

- **400 次循环零冻结、零异常**；进程 PID 3899 全程不变；crash buffer 0 条；logcat 无 `FATAL EXCEPTION` / `am_crash` / `am_anr` / `am_proc_died`。
- 修复前同节奏约 30~40 次即冻结，修复后 100 次无任何退化迹象。

### 4.2 超速测试（步进 200ms，快于转场 300ms）——预期行为说明

- 点击落在转场中 → 门控按设计丢弃 → 波纹可见但无跳转（**这是门控生效的正常表现**，非冻结；300ms 转场结束后一切恢复正常）；
- 被丢弃后 back 落在根页面 → Activity 正常退出 → 测试脚本 `am start` 自动拉起。
- 全程 92 次自动重启均为脚本行为：**PID 不变证明 App 从未真正崩溃**；logcat 无任何崩溃证据（此前 grep 到的「350 条 FATAL」全部是 uiautomator dump 的 `RuntimeInit` 噪音）。

## 5. 边界与遗留

- 未升级 navigation-compose（方案 B：升级 2.9.x 待后续按需评估）。
- 工作区另有历史未提交改动（`EditorSession.release()` 等 13 文件，与本次无关），未纳入本次报告。
- 回归范围覆盖 4 个主要界面；工程运行 / 打包链路未受影响（仅导航层改动）。

## 6. 关键证据文件

- `docs/discard/NAVIGATION_FREEZE_FIX_REPORT.md`（本文档）
- 根因反汇编：`%TEMP%\opencode\sora-inspect\NavHost33.txt`
- 测试脚本与日志：`%TEMP%\opencode\ld\stress.ps1`、`editor-500.log`、`settings-500.log`、`api-500.log`、`manual-500.log`
