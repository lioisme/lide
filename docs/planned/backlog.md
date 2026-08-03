# 计划中任务 & 技术债清单（Backlog）

> **状态**：📋 计划中
> **用途**：登记未启动的任务、已知技术债、遗留风险
> **新任务登记处**：任何新任务先写在这里，带关键词便于 Agent 检索

---

## 1. 技术债（来自 Java→Kotlin 迁移附录 A，已对照代码复核）

| ID | 问题 | 位置 | 严重度 | 处置建议 |
|---|---|---|---|---|
| TD1 | **硬编码 Windows 签名路径** `F:\my\programming\Android\签名\Jesse205.keystore` | `app/build.gradle:9` | 中 | 提取到 `local.properties` / CI 环境变量；`build_all.ps1` 已用临时 keystore 绕开 |
| TD2 | targetSdkVersion 29（因存储权限设计） | `app/build.gradle:22` | 中 | 评估 33+ 与 `MANAGE_EXTERNAL_STORAGE` 方案后再动，**勿盲目提升** |
| TD3 | `LuaDialog.kt:61` 的 `cancel()` 调 `super.dismiss()` 而非 `cancel()` | `androlua/.../LuaDialog.kt` | 低 | 确认为有意行为则加注释留档 |
| TD4 | 对话框 listItemLayout 字段仅构造期使用 | `LuaDialog.kt` 构造函数 | 低 | 内联或删除 |
| TD5 | Manifest 权限声明过多（大量系统权限） | `app/src/main/AndroidManifest.xml` | 低 | 审计并移除未用权限（先做运行时行为确认） |
| TD6 | `android.enableJetifier=true` | `gradle.properties` | 低 | AGP 8.x 下评估是否仍需 |
| TD7 | `android.nonTransitiveRClass=false` | `gradle.properties` | 低 | 考虑开启提升构建速度 |

## 2. 功能/质量待办（Agent 认领用）

| # | 任务 | 优先级 | 关键词 |
|---|---|---|---|
| F1 | **补全 Kotlin 公有 API 的 KDoc 文档注释**（3 个 .kt 文件 + 全部 companion 成员） | 中 | `KDoc` `LuaDialog` `LuaMaterialDialog` `AlertControllerBridge` |
| F2 | 审计 Phase 4 构建验证：`./gradlew :app:assembleDebug` + `assert` 打包断言脚本检查 | 高 | `assembleDebug`、验收 |
| F3 | 设备功能实测（Gate 3 场景：启动/项目列表/下拉刷新/打开/长按/返回） | 高 | 验收、设备测试 |
| F4 | 决定并执行 `old/` 依赖的取舍（见 ui-refactor R3） | 中 | `ui/old/`、重构 |
| F5 | 考虑新 UI 依赖方向：`main.lua` 中 `AlertDialogBuilder = MaterialAlertDialogBuilder` 的全局替换完整度检查 | 低 | 全局变量、Material3 |

## 3. 风险与备忘

| 项 | 说明 |
|---|---|
| **wiki/ 只读** | `wiki/AndroLuaX-main/` 为上游 Java 基线，任何 Agent 不得修改。 |
| **keystore 敏感** | `app/src/main/assets/keys/` 含签名密钥与平台密钥，勿提交泄露；release 签名硬编码路径属已知风险（TD1）。 |
| **构建环境** | Windows 下用 `build_all.ps1`（自动临时 keystore），Linux/macOS 用 `build_all.sh`。 |
| **targetSdk 29 红线** | 与存储权限体验相关，改动需充分验证（见 architecture.md §3）。 |

---

> 新任务登记格式建议：`### [F6] 任务标题` + 优先级 + 关联文件 + 验收标准
> 关联：[tasks/README.md](../README.md)