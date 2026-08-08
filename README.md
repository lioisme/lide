lide 是 AndroLua 衍生项目：Kotlin + Jetpack Compose 实现的宿主 IDE（`:app`，位于 `app/`），
编辑器代码在 `com.lioisme.lide.editor` 包（2026-08-08 独立 `:editor` 模块已并单），
内置 sora 编辑器源码模块（`:sora-editor`，vendored 于 `app/vendor/`），以 AndroLua C 原生
引擎源码（`app/src/main/cpp/`）为 Lua 编译与运行底座（由 AGP `externalNativeBuild` 在
`assemble` 时直接从源码构建并打包进 APK），工程格式兼容 AndroLua `.alp` 规范。

> 进入任何开发工作前，先读 [AGENTS.md](AGENTS.md)（评审硬门槛与架构边界）与
> [docs/README.md](docs/README.md)（文档中心入口）。

---

## 目录结构

| 目录 | 角色 |
|---|---|
| `app/` | **宿主应用**（`:app`）：Compose IDE、编辑器包（`com.lioisme.lide.editor`）、EngineActivity、打包器、设置持久化；引擎 C 源码（`app/src/main/cpp/`）、上游模块（`app/vendor/`：`:androlua`、`:sora-editor`） |
| `scripts/` | **脚本统一目录**：内置示例源（`examples-src/`，构建时由 `:app:generateExamples` 生成 `.alp`）、双端回归 `adb_regress.ps1`、打包矩阵 `build-apks.ps1`、上游全树差异门禁 `verify-upstream-pristine.*` |
| `docs/` | **文档中心**：白皮书（`WHITEPAPER.md`）、知识库（`kb/`，唯一事实来源）、进行中/计划中（`in-progress|planned`）、社区语料库（`kb/androlua-community/`）、参考基线（`reference/`，gitignore 排除、不提交、只读） |
| `gradle/` `gradlew*` `settings/build.gradle` | Gradle 工程骨架 + 根级构建脚本 |

## 常用入口

| 目的 | 位置 |
|---|---|
| 评审红线 / 硬规则 | [AGENTS.md](AGENTS.md) |
| 文档总索引（谁都看，先读它） | [docs/README.md](docs/README.md) |
| 知识库（是什么/为什么/在哪改） | [docs/kb](docs/kb/README.md) |
| 任务与技术债（做什么） | [docs/planned/backlog.md](docs/planned/backlog.md) |
| 进行中工作（做到哪） | [docs/in-progress](docs/in-progress/) |
| 产品架构白皮书 | [docs/WHITEPAPER.md](docs/WHITEPAPER.md) |

## 常用命令

```powershell
.\gradlew.bat :app:compileArm64DebugKotlin   # Kotlin 编译检查
.\gradlew.bat :app:assembleArm64Debug        # 完整构建（arm64；原生引擎由 AGP 从 app/src/main/cpp/ 自动构建）
.\scripts\build-apks.ps1                       # 6 个变体 APK（x64/arm64/universal × Debug/Release）
```

## 二进制策略

- **仓库禁止任何二进制原生产物**：原生库一律以源码形式存在于 `app/src/main/cpp/`（顶层 `Android.mk` +
  `Application.mk` 为 ndk-build 定义，`live2d/` 为独立模块；C/Kotlin 宿主不携带 `.so`/`.dex`/`.jar`/`.zip`）。
- 原生引擎由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从 `app/src/main/cpp/` 源码构建并打进 APK，
  无 jniLibs 缓存目录、无 rebuild 脚本（旧 `scripts/rebuild_engine.*` 已废弃删除）。NDK 版本 `29.0.14206865`。
- 例外：`gradle/wrapper/gradle-wrapper.jar`（Gradle 官方引导，非宿主产物）；`.alp` 作为工程包可直接存在（构建时由 `:app:generateExamples` 从 `scripts/examples-src/` 生成）。

## 许可证

- 本仓库基于 [GPL-3.0](LICENSE) 发布（含 vendored AndroLua C 引擎与 Java 运行时源码，皆 GPL 派生）。
- ⚠️ 签名密钥治理（2026-08-09）：`app/keystore.jks` + `app/keystore.properties` **不入库**（gitignored，
  模板见 `app/keystore.properties.example`，CI 由 secrets 注入）；缺失时 release 回落 debug 签名。
  **对外发布必须更换**真实签名密钥。

## 约定

- 上游库与参考基线只读：`app/vendor/`（`:androlua`、`:sora-editor`，构建模块）、`reference/`（参考基线 / 验收素材 / 社区语料，不提交）。
- 新增上游库统一放 `app/vendor/<库名>/`，并登记到 [settings.gradle.kts](settings.gradle.kts)（模块型库用 `projectDir` 指向）。
- 新增文档一律落入 `docs/in-progress|planned|kb`，禁止在 `docs/` 根直接新增 `.md`（`README.md`、`WHITEPAPER.md` 除外）。