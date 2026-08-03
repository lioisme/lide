# 项目概览（Knowledge Base）

> **类型**：知识库 · 事实性资料
> **用途**：了解项目身份、模块结构、版本信息、启动入口流程
> **核对时间**：2026-08-03（基于工作区实际文件）

---

## 1. 项目身份

| 项 | 值 |
|---|---|
| 项目名 | delua（git remote: `git@github.com:lioisme/delua.git`） |
| 产品名 | AndroLuaX（应用标签 / `app_name`） |
| 本质 | 反编译 AndroLua+ 后为其添加 AndroidX/Material3 的衍生品：Android 端 Lua 脚本 IDE 与运行时 |
| 能力 | 编写并执行 Lua 脚本；脚本经 `libluajava.so`（JNI）调用 Android API；支持打包 APK、Dex 管理、无障碍服务、网络/文件/系统模块 |
| 文档中心 | [docs/README.md](../../README.md) |

## 2. 模块结构（Gradle 多模块）

```
delua/
├── app/        ← 应用模块（业务层全在此：assets/ + resources/lua/）
├── androlua/   ← 库模块（Lua 运行时桥接：LuaDialog、AlertControllerBridge）
├── wiki/       ← 上游 AndroLuaX 参考基线（只读，Java 版对照）
└── gradle/     ← 版本目录 libs.versions.toml（依赖/插件统一管理）
```

### 2.1 app 模块（`app/build.gradle`）

| 项 | 值 |
|---|---|
| namespace / applicationId | `com.jesse205.androlua.androidX.material3` |
| compileSdk | 34 |
| minSdk | 21 |
| targetSdk | 29（**不建议提升**，存储权限自动授予问题） |
| versionName | `3.4(5.0.18)(Material3)` |
| versionCode | 3499 |
| 签名 | release 使用硬编码本地 keystore（见 TD1 技术债） |
| 依赖 | 全部 AndroidX 组件 + Material 1.11 + FastScroll + FullDraggableDrawer + glide + LuaCustRecyclerAdapter |

### 2.2 androlua 库模块（`androlua/build.gradle`）

| 项 | 值 |
|---|---|
| namespace | `com.androlua` |
| minSdk / targetSdk | 14 / 33 |
| 核心依赖 | `AndroLua+5.0.18_AndroidX.jar`（预编译 Lua-Java 框架）+ `libluajava.so` |
| 职责 | Lua 运行时桥接、对话框基础设施、FileProvider 配置 |

## 3. 启动入口流程（真实代码路径）

```
Launcher Activity: com.androlua.Welcome  (AndroidManifest.xml)
  → 初始化 Lua 运行时 (LuaApplication)
  → 加载 app/src/main/assets/main.lua
      ├── 清理旧版本遗留目录（editor/project/plugin/logcat 等 12 个）
      ├── 刷新 assets 新文件到应用目录（ui/main.lua, ui/settings, ui/dexmanager, ui/icons.lua）
      └── newActivity("ui/main") → assets/ui/main.lua  （项目列表主界面）
          ├── require ui.strings / ui.icons / old.javaapi.* / old.layout
          └── 项目列表 UI：扫描 /sdcard/AppProjects/、下拉刷新、打开、长按操作菜单
```

要点：**assets/ 是只读源，运行时代码在应用私有目录**。`main.lua` 每次启动都会把新 UI 文件从 assets 刷新到运行时目录，确保旧数据不阻塞新代码。

## 4. 语言构成统计（实际文件数）

| 层 | 位置 | 数量 |
|---|---|---|
| Lua 业务层 | `app/src/main/assets/`（ui/ + ui/old/ + 工具文件） | 51 个 .lua/.aly |
| Lua 系统库 | `app/src/main/resources/lua/` | 28 个 .lua（含 socket/ 子目录） |
| Lua 库模块库 | `androlua/src/main/resources/lua/` | 4 个（import/loadbitmap/loadlayout/loadmenu） |
| Kotlin 桥接 | 两模块 `src/main/java/` | 3 个 .kt（Java 已零残留） |
| JNI 原生库 | `app/src/main/jniLibs/` | arm64-v8a 17 个 .so / armeabi-v7a 16 个 .so / armeabi 1 个 |
| 图标资源 | `app/src/main/assets/icons/` | 19 个矢量图标 .xml |
| Res 资源 | `app/src/main/res/` | 仅主题/颜色/系统配置（无业务布局） |

## 5. 关键文件索引

| 文件 | 作用 |
|---|---|
| `app/src/main/assets/main.lua` | 启动引导脚本（清理+刷新+跳转） |
| `app/src/main/assets/ui/main.lua` | 主界面：项目列表（300 行） |
| `app/src/main/assets/ui/icons.lua` | 图标加载模块（从 assets/icons/ 读 VectorDrawable，带缓存） |
| `app/src/main/assets/ui/strings.lua` | 业务字符串集中定义（审计整改 Phase 2 产物） |
| `app/src/main/assets/ui/settings/main.lua` | 设置界面 |
| `app/src/main/assets/ui/dexmanager/main.lua` | Dex 管理界面（新增功能） |
| `app/src/main/assets/ui/project_dex_config/main.lua` | 项目 Dex 配置界面（新增功能） |
| `app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.kt` | Material3 对话框桥接（127 行） |
| `androlua/src/main/java/com/androlua/LuaDialog.kt` | 对话框基础设施（178 行） |
| `androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.kt` | 反射桥接（13 行） |

## 6. 与上游基线的关系

- `wiki/AndroLuaX-main/` = 上游原始 Java 版 AndroLuaX（完整源码副本，**只读参考**）。
- 本项目在基线之上完成：AndroidX/Material3 化、Java→Kotlin 迁移、代码审计整改、UI 重构。
- 合规判定规则：**工作区代码若与 wiki 基线不一致，需能解释原因**（见 architecture.md 红线规则）。

---

> 相关文档：[architecture.md](architecture.md) | [code-map.md](code-map.md) | [lua-modules.md](lua-modules.md)
