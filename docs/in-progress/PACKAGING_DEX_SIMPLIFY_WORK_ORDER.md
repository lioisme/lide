# 打包 dex 直用宿主（移除打包专用 dex 设计）

> **状态**：🚧 进行中（2026-08-08 立项）
> **目标**：移除「专门给 androlua 打包构建 APK 时提供 dex」的设计；打包产物直接复用 LIDE 宿主 APK 根目录全量 dex（IDE 类 + 引擎类），即使含工程用不上的内容。
> **用户红线**：① 可接受被打包产物有多余内容；② 不接受 LIDE 本体有多余内容（两份 androlua java 类，一份本体运行、一份打包构建）；③ 不接受魔改 LIDE 构建流程。
> **验收标准**：
> - 打包产物 dex = 宿主根目录全部 `classes*.dex` 原样保留（含引擎 dex，多 dex 无需重编号），不再做「引擎单 dex 替换」。
> - `assets/engine.dex.idx` 标记仅作「引擎存在性」判定（manifest 回落 `LuaApplication` + 移除 IDE MainActivity 的门禁），不再作为 dex 选取依据；标记自身任何产物一律不带。
> - LIDE 宿主构建流程零改动：`runtime` buildType / `engineDexExtract` / `injectEngineDex` / `verifyMinimalism` 宿主侧断言全部不变（引擎类在宿主 APK 根目录仍唯一一份）。
> - 打包单测全绿 + `verifyMinimalism` 门禁通过。
> - **关联代码路径**：`app/src/main/kotlin/com/lioisme/lide/build/ApkBuilder.kt`、`app/src/test/kotlin/com/lioisme/lide/build/ApkBuilderTest.kt`、`app/build.gradle.kts`（注释）、根 `AGENTS.md`。

## 背景与决策

2026-08-08 P0-6 修订后（引擎 dex 单份化，`:androlua` 改 compileOnly），宿主 APK 根目录 dex 形态为：
`classes.dex`（仅 IDE 代码）+ `classesN.dex`（引擎类，唯一一份，由 `engineDexExtract` + `injectEngineDex<Variant>` 注入）。

遗留的「专门给打包提供 dex」设计 = `ApkBuilder` 的 **dex 替换模式**：读 `assets/engine.dex.idx` 标记
→ 定位根目录引擎 dex → 宿主 `classes*.dex` 条目全部替换为该引擎 dex（产物唯一 `classes.dex`，
无 IDE 类）→ manifest 回落 `LuaApplication` + 移除 IDE MainActivity；标记/条目缺失时兜底宿主自身
（pro 模板语义）。

用户决策（2026-08-08，P0-7）：**删除 dex 替换模式**。打包产物直接复用宿主全量根 dex（= 现
「兜底宿主自身」路径的语义，且引擎类必在根目录），接受产物携带 IDE 类等多余内容；宿主侧
任何构建机制不动。

判定：设计合适且工作量小——引擎类在宿主已单份（前提满足），仅需删打包器侧约 30 行装配逻辑；
产物带 IDE 类无行为副作用（产物启动 `LuaApplication` + 引擎页 `EngineActivity`，IDE 类只是 dex
字节、从不实例化，Compose 等库不会加载），且比「引擎单 dex」更稳健（无类链接遗漏风险）。

## 现状核对（证据）

- `ApkBuilder.kt:148-156`：标记解析 + 引擎 dex 条目定位（`runtimeEntry`）。
- `ApkBuilder.kt:209-210`：`applicationName` / `removeActivities` 以 `runtimeEntry != null` 为门禁。
- `ApkBuilder.kt:263-265`：dex 替换跳过段（`runtimeEntry != null` 时剔除全部宿主 `classes*.dex`）。
- `ApkBuilder.kt:313-316`：末尾注入引擎 dex 为产物唯一 `classes.dex`。
- `HostPolicy.kt:12`：根级 dex（非 `assets/`）归 `RUNTIME_CORE` → 删除跳过段后自然原样保留。
- `ApkBuilderTest.kt:385-489`：4 个 P0 dex 用例断言「替换语义」。

## 任务分解

| # | 任务 | 验收 | 状态 |
|---|------|------|------|
| T1 | `ApkBuilder.kt` 移除 dex 替换模式：删标记解析（改存在性判定 `enginePresent`）、删 dex 替换跳过段、删引擎 dex 注入段；manifest 回落门禁改 `enginePresent`；KDoc 同步改写（类注释 P0 段 + 常量注释） | `classes*.dex` 全部原样入产物；产物不带标记；`:app:compileArm64DebugKotlin` 绿 | ☐ |
| T2 | `ApkBuilderTest.kt` 改写 P0 dex 用例为「全量保留」语义（含非法标记/无标记兜底路径）；不新增无关行为 | 打包单测全绿 | ☐ |
| T3 | 文档契约同步：`AGENTS.md` 运行时基座契约「打包器」条款、`app/build.gradle.kts` P0 注释、`proguard-rules.pro` 相关注释 | 事实来源唯一、无残留「dex 替换模式」表述 | ☐ |
| T4 | 全链验证 + 归档：`:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` + `:app:verifyMinimalism` 全绿；任务文档标「（已完成）」并 `git mv` 至 `docs/discard/`，同步 kb README §2 / TASKS.md / discard README §3 | 门禁全绿 + 归档三处同步 | ☐ |

## 验证命令

- `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- `.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*" --console=plain`
- `.\gradlew.bat :app:verifyMinimalism --console=plain`（宿主侧断言：引擎 dex 根目录单份 + 标记存在——必须零改动仍绿，证明宿主构建未动）

## 权衡（均已接受）

- 产物体积：多携带 release IDE 类 dex（压缩后数 MB）。
- 产物携带完整 IDE 类：逆向分析面略大，不影响行为。
- 多 dex 无需重编号：宿主已按 `classesN.dex` 规范编号，ART 自动加载；工程 `libs/*.dex` 走运行时 `LuaDexLoader`（W^X `InMemoryDexClassLoader`），不参与 dex 合并。
- `embedRuntimeDex=false`（宿主无引擎 dex）边缘：`enginePresent=false` → 不回落 `LuaApplication`、不移除 MainActivity，产物保持宿主形态（现状兜底语义，不阻断运行期打包）。
