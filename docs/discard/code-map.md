# 代码地图（Knowledge Base）

> ⚠️ **已过期**：本文撰写于旧 AndroLuaX 宿主形态（`assets/ui/*`、`wiki/` 基线、`build_all.ps1`）。
> 当前三项模式的模块地图与工具链见根 [README.md](../../README.md)、[kb/ENGINE_RUNTIME.md](../kb/ENGINE_RUNTIME.md)；以下内容仅作历史参考。

> **类型**：知识库 · 索引性资料
> **用途**：快速定位「某个功能/文件在哪里」，明确每个目录职责
> **核对时间**：2026-08-03 · 全量文件与工作区一致

---

## 1. 根目录

| 路径 | 职责 |
|---|---|
| `build.gradle` | 根构建：AGP 8.1.1 + kotlin 1.9.22 插件声明 |
| `settings.gradle` | 模块注册（`:app`,`:androlua`）+ 阿里云镜像仓库 |
| `gradle/libs.versions.toml` | **依赖/版本唯一来源**（版本目录） |
| `gradle.properties` | AndroidX 开关、JVM 参数、Jetifier |
| `build_all.ps1` / `build_all.sh` | 一键构建脚本（自动生成临时 keystore、备份改动、构建 APK） |
| `LICENSE` / `licenses/` | 项目与依赖许可证 |
| `logo.svg` | 项目 logo |

## 2. Lua 业务层 `app/src/main/assets/`

### 2.1 根级文件

| 文件 | 职责 |
|---|---|
| `main.lua` | 启动引导：清理旧版本遗留目录 → 刷新新 UI → 跳转 ui/main（**勿删 12 个 staleDirs 清理逻辑**） |
| `init.lua` | 应用级初始化 |
| `easygo.json` | 配置（EasyGo） |
| `keys/` | 签名密钥（keystore.ks、platform.* 、testkey.*） |
| `luadoc/` | 离线 Lua 手册（glossary.html / manual.html / logo.gif） |
| `*.dex` | 运行时 dex（mao.dex、sign.dex） |

### 2.2 新 UI 框架 `assets/ui/`（现行）

| 文件 | 职责 |
|---|---|
| `main.lua` | **主界面**：项目扫描、列表加载、下拉刷新、打开/长按操作菜单、返回处理（300 行） |
| `strings.lua` | 业务字符串集中定义（审计 Phase 2 产物，替代 res/values/strings.xml 业务文案） |
| `icons.lua` | 图标加载器：从 `assets/icons/` 读 VectorDrawable，带缓存 + 加载失败回退（`loadResource`） |
| `settings/main.lua` | 设置界面（Material 主题色获取、设置项 UI） |
| `dexmanager/main.lua` | **Dex 管理器**（新增功能：复用 `dex_manager` 系统库，FAB/输入框/列表） |
| `project_dex_config/main.lua` | **项目 Dex 配置**（新增功能：MaterialSwitch/Button/Card 构建配置面板） |

### 2.3 旧 UI 归档 `assets/ui/old/`（只读参考，由主入口清理）

> 这些是审计/重构前的旧实现，已归档。**新功能不要在 old/ 里加**。
> 但部分功能仍被引用：`ui/main.lua:20-21` require `old/javaapi/android`、`old/javaapi/androidXAndMaterialClasses`；`:28` require `old/layout`；`:177` require `old/lua/BottomSheet`。

| 目录 | 职责 |
|---|---|
| `old/editor/` | 旧代码编辑器 |
| `old/help.lua` / `old/luadoc.lua` | 旧帮助/Lua 文档页 |
| `old/javaapi/` | Java API 类清单（android.lua + androidXAndMaterialClasses.lua）**仍被引用** |
| `old/layout.lua` | 布局模板定义（projectList/projectItem/project）**仍被引用** |
| `old/lua/` | 通用 Lua 组件（AboutDialog/Appbar/BottomSheet/DialogHelper/res/themeutil）**BottomSheet 仍被引用** |
| `old/layouthelper/` | 旧布局辅助框架（loadlayout2/3、xml2table） |
| `old/logcat/` | 旧日志查看 |
| `old/package/` `old/plugin/` `old/project/` `old/projectinfo*/` | 旧打包/插件/项目管理界面 |
| `old/projectactions/` | 旧项目操作菜单 |

## 3. Lua 系统库 `app/src/main/resources/lua/`

> 这些是**运行时标准库**（随脚本分发），与业务 UI 无关，可被任意项目脚本 require。

| 类别 | 文件 |
|---|---|
| 基础 | `import`、`bin`、`check`、`options`、`test`、`console`、`loadmenu` |
| 网络 | `http`、`ftp`、`smtp`、`socket`(+ headers/tp/url)、`bmob` |
| 数据格式 | `json`、`xml`、`base64`、`hex`、`mime`、`mbox`、`ltn12`、`yaml(禁用)` |
| 系统 | `permission`、`su`、`logcat`、`autotheme`、`androidx`、`dex_manager` |
| 安全 | `crypt`（native） |

## 4. Kotlin 桥接层（全部 .java 已迁移为 .kt，零残留）

| 文件 | 行数 | 职责 |
|---|---|---|
| `androlua/src/main/java/com/androlua/LuaDialog.kt` | 178 | 对话框基础设施：extends AlertDialog，Lua 友好的 API（setButton/setItems/单选多选/Item 监听） |
| `androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.kt` | 13 | 反射桥接：访问 AlertController 包私有方法 setHasDecor |
| `app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.kt` | 127 | Material3 版 LuaDialog：companion 提供主题覆盖、MaterialShapeDrawable 背景、Inset 支持 |

**规则**：禁止新增业务 Kotlin 文件；如需新增必须属于 Lua 运行时基础设施。

## 5. JNI 原生库 `app/src/main/jniLibs/`

> 存在 ABI：`armeabi-v7a`（16 个）+ `arm64-v8a`（17 个）。`armeabi/` 仅存 `libcrash_analysis.so`。

| 库 | 功能 |
|---|---|
| `libluajava.so` | Lua↔Java JNI 桥（**核心**，仅 armeabi-v7a 在 app 模块；androlua 模块另有副本） |
| libbson / libyaml / libxml / libcjson / libmime / libzlib / libzip | 数据格式 |
| libcrypt / libmd5 | 加密 |
| libsocket / libluv / libthreads | 网络/事件/线程 |
| libsensor | 传感器 |
| libtcc | C 编译器 |
| libgl / libcanvas / libregex | 图形/正则 |
| libcrash_analysis | 崩溃分析（仅 armeabi） |

## 6. Res 资源层 `app/src/main/res/`

| 目录 | 内容 | 合规状态 |
|---|---|---|
| `values/` | strings（仅 app_name）、colors、colors_google/theme/tokens、styles、theme、attrs | ✅ |
| `values-night/` | 夜间色板 | ✅ |
| `values-v23/v27/v28/v29/` | 分版本主题 | ✅ |
| `drawable/` | **仅** `icon.png`（应用图标）+ `welcome.xml` | ✅（业务图标已迁出） |
| `values-zh-rCN/`、`xml/`、`xml-v24/`、`color/` | 无障碍服务描述与配置 | ✅ |

## 7. androlua 库模块支撑

| 位置 | 内容 |
|---|---|
| `androlua/libs/` | `AndroLua+5.0.18_AndroidX.jar`（预编译框架） |
| `androlua/src/main/jniLibs/` | `libluajava.so`（armeabi-v7a / arm64-v8a） |
| `androlua/src/main/resources/lua/` | 运行时内置库（import/loadbitmap/loadlayout/loadmenu） |
| `androlua/src/main/res/xml/` | FileProvider 路径配置 |

## 8. 只读参考 `wiki/AndroLuaX-main/`

> 上游 AndroLuaX（Java 版）完整源码副本。合规对照的「黄金基线」。
> **禁止修改**。凡与本工作区不一致处 = 本项目的改进点。

---

> 相关文档：[project-overview.md](../kb/project/project-overview.md) | [architecture.md](architecture.md) | [lua-modules.md](lua-modules.md)