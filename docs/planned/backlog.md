# 计划中任务 & 技术债清单（Backlog）

> **状态**：📋 计划中
> **用途**：登记未启动的任务、已知技术债、遗留风险
> **新任务登记处**：任何新任务先写在这里，带关键词便于 Agent 检索
> **2026-08-05 清理**：TD1（keystore.properties 提取）、TD2（targetSdk 36）、TD6（jetifier 移除）、TD7（nonTransitiveRClass=true 已开）、F4（ui/old/ 已删除）、wiki/ 与 build_all.ps1 引用（均已删除）从清单移除。

---

## 1. 技术债（来自 Java→Kotlin 迁移附录 A，已对照代码复核）

| ID | 问题 | 位置 | 严重度 | 处置建议 |
|---|---|---|---|---|
| TD3 | `LuaDialog.kt:61` 的 `cancel()` 调 `super.dismiss()` 而非 `cancel()` | `androlua/.../LuaDialog.kt` | 低 | 确认为有意行为则加注释留档 |
| TD4 | 对话框 listItemLayout 字段仅构造期使用 | `LuaDialog.kt` 构造函数 | 低 | 内联或删除 |
| TD5 | Manifest 权限声明过多（大量系统权限） | `app/src/main/AndroidManifest.xml` | 低 | 审计并移除未用权限（先做运行时行为确认） |

## 2. 功能/质量待办（Agent 认领用）

| # | 任务 | 优先级 | 关键词 |
|---|---|---|---|
| F1 | **补全 Kotlin 公有 API 的 KDoc 文档注释**（3 个 .kt 文件 + 全部 companion 成员） | 中 | `KDoc` `LuaDialog` `LuaMaterialDialog` `AlertControllerBridge` |
| F2 | 审计 Phase 4 构建验证：`.\gradlew.bat :app:assembleArm64Debug` + `assert` 打包断言脚本检查 | 高 | `assembleDebug`、验收 |
| F3 | 设备功能实测（Gate 3 场景：启动/项目列表/下拉刷新/打开/长按/返回） | 高 | 验收、设备测试 |
| F5 | 考虑新 UI 依赖方向：`main.lua` 中 `AlertDialogBuilder = MaterialAlertDialogBuilder` 的全局替换完整度检查 | 低 | 全局变量、Material3 |
| F6 | **R8 收缩 + baseline profile**（V2 报告阶段 A 遗留） | 中 | `R8` `baseline profile` `ProGuard` |
| F7 | **性能优化阶段 C**：诊断 tooltip、原生 SnippetController、增量保存 | 中 | `SnippetController`、增量保存、诊断 tooltip |
| F8 | **编辑器功能完善至 sora-editor 原生上限**（历史任务单 EDITOR_FEATURE_COMPLETION_WORK_ORDER，已蒸馏）：P0 设置中心 21 项全集（通用设置页仅留入口）/ P1 原生 SnippetController·诊断 tooltip·多错误聚合·折叠验证·文本操作增强·整词搜索·标识符补全·迷你地图 / P2 R8+baseline profile·降级矩阵扩展·基准脚本 / P3 R1 grep 入 CI | 高 | `编辑器设置`、`SnippetController`、`诊断 tooltip`、`迷你地图`、`baseline profile` |

## 3. 风险与备忘

| 项 | 说明 |
|---|---|
| **keystore 敏感** | `app/src/main/assets/keys/` 含签名密钥与平台密钥，勿提交泄露；`app/keystore.properties`（gitignored）为 release/debug 签名路径唯一来源，不得硬编码回 build.gradle。 |
| **构建环境** | Windows 下用 `.\gradlew.bat :app:assembleArm64Debug`；NDK 固定 `29.0.14206865`（AGP `externalNativeBuild` ndk-build）。 |
| **编辑器性能红线** | 编辑器 API 调用保持主线程；补全/诊断/事件回调必须 `try/catch(Throwable)` 降级（AGENTS.md）。 |

---

> 新任务登记格式建议：`### [F8] 任务标题` + 优先级 + 关联文件 + 验收标准
> 关联：根目录 `README.md`（docs 入口为 `docs/README.md`）
