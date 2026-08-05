lide 是 AndroLua 衍生项目：Kotlin + Jetpack Compose 实现的宿主 IDE（`:app`，位于 `app/`），
内置 sora 编辑器模块（`:editor`），以 AndroLua C 原生引擎源码（`app/src/main/cpp/`）为
Lua 编译与运行底座（由 AGP `externalNativeBuild` 在 `assemble` 时直接从源码构建并打包进 APK），
工程格式兼容 AndroLua `.alp` 规范。

> 进入任何开发工作前，先读 [AGENTS.md](AGENTS.md)（评审硬门槛与架构边界）与
> [docs/README.md](docs/README.md)（文档中心入口）。

---

## 目录结构

| 目录 | 角色 |
|---|---|
| `app/` | **宿主应用**（`:app`）：Compose IDE、EngineActivity、打包器、设置持久化 |
| `editor/` | **编辑器模块**（`:editor`）：纯 Kotlin 文本能力（行级诊断/补全/高亮/搜索），不触碰原生引擎 |
| `build-scripts/` | 内置示例源码包（examples-src/，构建时生成 `.alp`）；`make-examples.ps1` 兼容脚本 |
| `scripts/` | 开发脚本：双端回归 `adb_regress.ps1` |
| `third_party/` | **上游库统一收纳**（只读，后续新增上游库一律入此）：`androlua/`（Gradle 模块 `:androlua`）、`patches/`（material3 SearchBar 实验补丁） |
| `docs/` | **文档中心**：进度台账（`PROGRESS.md`）、白皮书（`WHITEPAPER.md`）、知识库（`kb/`）、完成/进行中/计划（`completed|in-progress|planned`）、日志（`log/`）、参考基线（`reference/`，gitignore 排除、不提交、只读） |
| `gradle/` `gradlew*` `settings/build.gradle` `build-apks.*` | Gradle 工程骨架 + 根级构建脚本 |

## 常用入口

| 目的 | 位置 |
|---|---|
| 评审红线 / 硬规则 | [AGENTS.md](AGENTS.md) |
| 文档总索引（谁都看，先读它） | [docs/README.md](docs/README.md) |
| 知识库（是什么/为什么/在哪改） | [docs/kb](docs/kb/README.md) |
| 任务看板（做到哪了） | [docs/TASKS.md](docs/TASKS.md) |
| 引擎工程账单（WP 台账） | [docs/PROGRESS.md](docs/PROGRESS.md) |
| 产品架构白皮书 | [docs/WHITEPAPER.md](docs/WHITEPAPER.md) |

## 常用命令

```powershell
.\gradlew.bat :app:compileArm64DebugKotlin   # Kotlin 编译检查
.\gradlew.bat :app:assembleArm64Debug        # 完整构建（arm64；原生引擎由 AGP 从 app/src/main/cpp/ 自动构建）
.\build-apks.ps1                             # 4 个变体 APK（x64/arm64 × Debug/Release）
```

## 二进制策略

- **仓库禁止任何二进制原生产物**：原生库一律以源码形式存在于 `app/src/main/cpp/`（顶层 `Android.mk` +
  `Application.mk` 为 ndk-build 定义，`live2d/` 为独立模块；C/Kotlin 宿主不携带 `.so`/`.dex`/`.jar`/`.zip`）。
- 原生引擎由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从 `app/src/main/cpp/` 源码构建并打进 APK，
  无 jniLibs 缓存目录、无 rebuild 脚本（旧 `scripts/rebuild_engine.*` 已废弃删除）。NDK 版本 `29.0.14206865`。
- 例外：`gradle/wrapper/gradle-wrapper.jar`（Gradle 官方引导，非宿主产物）；`.alp` 作为工程包可直接存在（构建时由 `:app:generateExamples` 从 `build-scripts/examples-src/` 生成）。

## 许可证

- 本仓库基于 [GPL-3.0](LICENSE) 发布（含 vendored AndroLua C 引擎与 Java 运行时源码，皆 GPL 派生）。
- ⚠️ `app/keystore.jks` + `app/keystore.properties` 为**项目本地自签测试密钥**（密码随仓库公开），仅用于本机/CI 构建签名一致；**对外发布必须更换**真实签名密钥，且不得入库。

## 约定

- 上游库与参考基线只读：`third_party/`（`androlua`、`patches`）、`docs/reference/`（不提交）。
- 新增上游库统一放 `third_party/<库名>/`，并登记到 [settings.gradle.kts](settings.gradle.kts)（模块型库用 `projectDir` 指向）。
- 新增文档一律落入 `docs/completed|in-progress|planned|kb`，并在索引登记，禁止在 `docs/` 根直接新增 `.md`。