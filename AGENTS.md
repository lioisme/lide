# AGENTS.md

本项目为「纯编辑器模块 + 宿主运行时」双层架构。以下规则是评审硬门槛，违反视为严重缺陷。

## 架构边界（R1–R4）

- **R1** `:editor`（包 `com.lioisme.delua.editor`）禁止出现 `com.androlua`、`com.luajava`、
  `System.loadLibrary`、jniLibs；其 `build.gradle.kts` 禁止依赖 `:androlua`。
  编辑器进程内的一切 Lint / 诊断 / 补全 / 高亮逻辑必须是纯 Kotlin/Java，禁止触碰 `libluajava.so`。
- **R2** Lua 编译与运行能力属于宿主 `:app`（`EngineBridge` → `EngineActivity`，同进程运行）。
  编辑器只做文本级能力（行级词法诊断、补全、高亮、搜索），不做运行时语义。
- **R3** 新增编辑器功能默认放 `:editor`；宿主专属逻辑（文件、权限、运行时、设置持久化）放 `:app`。
- **R4** `:editor` 不得读取 `:app` 的任何状态或类型；宿主依赖一律通过参数 / 接口注入。

验证命令（必须零命中）：

```
rg -n "luajava|androlua|loadLibrary" editor/src
```

## 构建与验证

- Kotlin 编译检查：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain`
- 完整构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`

## 编辑器关键约定

- 初始化顺序（官方 demo 顺序，勿改）：`colorScheme` → `setEditorLanguage` → `setText`。
- TextMate 运行时只经 `TextMateInitializer`（进程级一次性）初始化，且不得在 UI 线程调用。
- 文本快照可在后台线程取（`Content.toString` 内部持有 `ReadWriteLock`），
  但编辑器 API 调用（`setText` / `diagnostics` 赋值 / `subscribeEvent` 回调）保持主线程。
- 自动保存：快照与写盘均在 `Dispatchers.IO`；离开编辑器时经 `onRelease` 兜底保存。
- 大文件（> 200 KB）：关闭括号高亮 / 当前块 / 自动换行，并跳过行级诊断。
- 补全、诊断、事件回调必须 `try/catch(Throwable)` 降级，不得让异常逃逸进 sora 主循环。

## /libs 机制约定（工程 /libs 支持，详见 docs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.mddocs/completed/LIBS_SUPPORT_WORK_ORDER.md