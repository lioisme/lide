# 项目概览（Knowledge Base）

> **类型**：知识库 · 事实性资料，说明「项目身份 / 模块结构 / 版本信息」
> **核对时间**：2026-08-04（基于工作区实际文件）

---

## 1. 项目身份

| 项 | 值 |
|---|---|
| 项目名 | lide（工作区目录 `lide/`） |
| 定位 | Android 端 Lua 脚本 IDE / 运行时（AndroLua 衍生） |
| 应用 ID | `com.lioisme.lide` |
| 架构 | Kotlin/Java + Jetpack Compose 宿主；AndroLua C 原生引擎做 Lua 编译与运行 |
| 文档入口 | 根 [README.md](../../../README.md)；红线 [AGENTS.md](../../../AGENTS.md)；索引 [docs/README.md](../../README.md) |

## 2. 模块结构（Gradle 多模块，见 settings.gradle.kts）

```
lide/
├── app/        # :app 宿主应用 —— Compose IDE、EngineActivity、打包器（ApkBuilder/ApkSigner）、设置
├── editor/     # :editor 纯编辑器模块 —— 行级词法诊断/补全/高亮/搜索（纯 Kotlin，禁止触碰原生引擎）
├── scripts/    # 脚本统一目录 —— 打包 build-apks.*、示例源 examples-src/ 与 make-examples.ps1、回归 adb_regress.ps1
├── third_party/    # 上游库统一收纳：androlua（:androlua 模块）、sora-editor、patches
└── docs/           # 文档中心（kb / discard / in-progress / planned / log / reference）
```

## 3. 关键版本号（app/build.gradle.kts 为准）

| 项 | 值 |
|---|---|
| namespace / applicationId | `com.lioisme.lide` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| Kotlin / AGP | 2.2.20 / 8.13.2 |
| 原生引擎 | AndroLua C（Lua 5.3.3 魔改，`libluajava.so`），三 ABI x64/arm64/armeabi-v7a；引擎源码统一归档于 `app/src/main/cpp/`，由 AGP `externalNativeBuild`（ndk-build，NDK 29.0.14206865）在 assemble 时直接构建，无 jniLibs 缓存目录（R5 红线） |

## 4. 启动入口

- App 入口：`app/src/main/kotlin/com/lioisme/lide/...MainActivity`（Kotlin 宿主）。
- 运行 Lua：`EngineBridge` → `EngineActivity`（继承 `androlua` 模块的 `LuaActivity`），同一进程。
- 编辑器：`LideApp` → 编辑器主屏挂载 `:editor` 模块能力（TextMate 初始化在后台一次完成）。

> 详细架构（R1~R4 边界、编辑器约定、运行时细节）见权威文档
> [kb/ARCHITECTURE.md](../ARCHITECTURE.md)、[kb/EDITOR.md](../EDITOR.md)、[kb/ENGINE_RUNTIME.md](../ENGINE_RUNTIME.md)。