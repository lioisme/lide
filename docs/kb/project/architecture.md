# 架构与合规红线（Knowledge Base）

> **类型**：知识库 · 规则性资料
> **用途**：判断某个改动该改哪一层、哪些改动被禁止
> **核对时间**：2026-08-03

---

## 1. 三层架构（源自 README 开发约定 + 实际代码）

```
┌──────────────────────────────────────────────┐
│  Lua 层（业务逻辑 · 主力开发语言）              │
│  app/src/main/assets/（ui/、ui/old/）          │
│  app/src/main/resources/lua/（系统库）         │
│  androlua/src/main/resources/lua/（运行时库）  │
│  → 业务逻辑 · UI 布局 · 事件处理 · 数据管理     │
├──────────────────────────────────────────────┤
│  Kotlin 层（底层支撑，仅 3 个文件）             │
│  app/src/main/java/com/jesse205/androluax/     │
│  androlua/src/main/java/com/androlua/          │
│  androlua/src/main/java/androidx/              │
│  → 仅限：基础设施桥接 · JNI 封装 · 对话框封装   │
├──────────────────────────────────────────────┤
│  Res 层（底层支撑）                            │
│  app/src/main/res/（颜色/主题/样式/系统配置）   │
│  → 仅限：主题定义 · 系统配置 · 通用样式 · 图标   │
└──────────────────────────────────────────────┘
```

**核心原则（README §1 原话）**：本项目以 Lua 语言为主力开发语言，Kotlin、Android 资源文件（res/）等为底层支撑。**通常情况下，禁止改动 Kotlin（.kt）、资源文件（res/）、构建脚本（.gradle）及其他底层代码。绝大多数功能需求仅通过修改 Lua 层（assets/ 和 resources/lua/ 下的 .lua 文件）即可实现。**

## 2. 合规判定矩阵

| 代码/资源 | 允许范围 | 禁止范围 |
|---|---|---|
| Kotlin 类 | Lua 运行时桥接、JNI 封装、系统级 API 包装（如对话框） | 业务数据模型、业务逻辑、业务 UI、业务 Activity |
| Res 布局 | 系统级 UI（Welcome 屏幕）、主题预览 | 业务列表界面、业务对话框、业务列表项模板 |
| Res 字符串 | 应用元数据（`app_name`）、系统提示 | 业务菜单文案、状态提示、操作描述 |
| Res 可绘制对象 | 应用图标、主题装饰图形 | 业务操作图标（已迁至 `assets/icons/`） |
| AndroidManifest | Activity/Service/权限声明（框架类） | 业务 Activity 注册 |
| Build 配置 | 依赖声明、SDK 版本、构建参数 | 业务插件、自定义业务构建逻辑 |

## 3. 红线规则（严重违规，必须无条件整改）

1. 在 Kotlin 层实现业务 UI（Activity/Fragment/Adapter/View 渲染业务数据）
2. 在 Kotlin 层实现业务数据模型（data class/POJO 代表业务领域概念）
3. 在 Res 层定义业务布局（layout/*.xml 描述业务界面）
4. 在 Res 层定义业务字符串（strings.xml 含业务文案）
5. 在 Res 层放置业务图标（应放 `assets/icons/` 由 Lua 加载）

**当前合规状态**：✅ 全部清零（审计整改已完成，见 tasks/completed/audit-remediation.md）

## 4. 依赖方向规则

```
Lua 层  ──require──▶ Lua 系统库（resources/lua/）
  │                      │
  ├──bindClass──▶ Java/Kotlin 类（含 jar 内 com.androlua.*）
  ├──read──▶ res 资源（R.string / R.drawable / R.attr）
  └──read──▶ assets/（icons/*.xml 等）

允许：Lua → Kotlin / Lua → Res / Lua → assets
禁止：Res → Lua（资源不依赖 Lua 运行时）
```

## 5. 开发注意事项（继承自 README）

1. **targetSdk 不得提升到 29 以上**：AndroLua+ 设计问题，会导致存储权限不被自动授予，用户体验差（`app/build.gradle:22` 注释同源）。
2. AndroidX 尽可能全量添加，但**排除无用依赖、与 Kotlin/RxJava 相关的依赖**（仅保留 kotlin-stdlib 供 3 个桥接文件使用）。
3. **部分依赖无法在低版本 Android 上使用**（见 wiki README 依赖兼容性表：car/uwb/health-connect 等）。

## 6. 代码质量约束（Java→Kotlin 迁移遗留约定）

- Kotlin 文件遵循 Android Kotlin Style Guide：属性 camelCase 无 m 前缀、companion object 放静态成员、`use{}` 管理 Closeable、禁 `!!`。
- 全部公有 API 应补 KDoc（计划中，见 backlog.md）。
- Lua 模块使用 `local M = {} ... return M` 模块模式，禁止污染全局 namespace。

---

> 相关文档：[project-overview.md](project-overview.md) | [code-map.md](code-map.md)
