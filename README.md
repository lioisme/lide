# lide — Android 端 Lua 脚本 IDE / 运行时

lide 是 AndroLua 衍生项目：Kotlin + Jetpack Compose 实现的宿主 IDE（`:app`，位于 `app/`），
内置 sora 编辑器模块（`:editor`），以 AndroLua C 原生引擎（`third_party/androlua` 运行时 + `third_party/prebuilt/` 原生库）作为
Lua 编译与运行底座，工程格式兼容 AndroLua `.alp` 规范。

> 进入任何开发工作前，先读 [AGENTS.md](AGENTS.md)（评审硬门槛与架构边界）与
> [docs/README.md](docs/README.md)（文档中心入口）。

---

## 目录结构

| 目录 | 角色 |
|---|---|
| `app/` | **宿主应用**（`:app`）：Compose IDE、EngineActivity、打包器、设置持久化 |
| `editor/` | **编辑器模块**（`:editor`）：纯 Kotlin 文本能力（行级诊断/补全/高亮/搜索），不触碰原生引擎 |
| `build-scripts/` | 原生引擎构建工具链（jni/Android.mk、patches/、examples-src/、live2d） |
| `scripts/` | 开发脚本：引擎重建 `rebuild_engine.*`、双端回归 `adb_regress.ps1` |
| `third_party/` | **上游库统一收纳**（只读，后续新增上游库一律入此）：`androlua/`（Gradle 模块 `:androlua`）、`prebuilt/`（Lua 原生库 .so 三 ABI + Lua 模块脚本）、`patches/`（material3 SearchBar 实验补丁） |
| `docs/` | **文档中心**：知识库（`kb/`）、完成/进行中/计划（`completed|in-progress|planned`）、日志（`log/`）、参考基线（`reference/`，gitignore 排除、不提交、只读） |
| `gradle/` `gradlew*` `settings/build.gradle` `build-apks.*` | Gradle 工程骨架 + 根级构建脚本 |

## 常用入口

| 目的 | 位置 |
|---|---|
| 评审红线 / 硬规则 | [AGENTS.md](AGENTS.md) |
| 文档总索引（谁都看，先读它） | [docs/README.md](docs/README.md) |
| 知识库（是什么/为什么/在哪改） | [docs/kb](docs/kb/README.md) |
| 任务看板（做到哪了） | [docs/TASKS.md](docs/TASKS.md) |
| 引擎工程账单（WP 台账） | [PROGRESS.md](PROGRESS.md) |
| 产品架构白皮书 | [WHITEPAPER.md](WHITEPAPER.md) |

## 常用命令

```powershell
.\gradlew.bat :app:compileArm64DebugKotlin   # Kotlin 编译检查
.\gradlew.bat :app:assembleArm64Debug        # 完整构建（arm64）
.\build-apks.ps1                             # 4 个变体 APK（x64/arm64 × Debug/Release）
.\scripts\rebuild_engine.ps1 all            # 从 C 源码重建原生引擎并同步 third_party/prebuilt/ 与 jniLibs/
```

## 约定

- 上游库与参考基线只读：`third_party/`（`androlua`、`prebuilt`、`patches`）、`docs/reference/`（不提交）。
- 新增上游库统一放 `third_party/<库名>/`，并登记到 [settings.gradle.kts](settings.gradle.kts)（模块型库用 `projectDir` 指向）。
- 新增文档一律落入 `docs/completed|in-progress|planned|kb`，并在索引登记，禁止在 `docs/` 根直接新增 `.md`。