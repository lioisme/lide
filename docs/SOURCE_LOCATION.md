# 本地源码位置索引（Source Map）

> **本仓库工作副本内已包含全部所需源码，禁止从云端拉取**（`git clone` / 网页复制 / GitHub 下载）。
> 需要任何上游源码（AndroLua Pro、sora-editor、参考工程、社区语料）时，先查本文件，全部在本地。
> 文末的 URL 仅作溯源出处备注，不作为获取途径。

---

## 1. `reference/` — 上游参考基线 / 验证素材（gitignored，本地只读，约 112MB）

> 维护约定见 `reference/README.md`（gitignored 但存在）。禁止修改、禁止 `git add`。

| 路径 | 内容 | 用途 |
|---|---|---|
| `reference/AndroLua_pro-master/` | **AndroLua Pro v4.4.2 纯净官方源码**（master 分支） | 行为对齐唯一参照 |
| ├─ `app/src/main/assets/projectinfo/main.lua` | pro 工程属性页（Lua：权限多选 ListView + 主题反射下拉 + 保存模板） | 工程属性页参照（WP-004） |
| ├─ `app/src/main/assets/projectinfo/layout.aly` | 工程属性页布局 | 同上 |
| ├─ `app/src/main/resources/lua/bin.lua` | pro 打包脚本（L238-240 权限白名单、L324-326 uses-sdk 白名单、L340 `appsdk` 目标 SDK 替换） | 打包链参照 |
| ├─ `app/src/main/resources/lua/permission.lua` | pro 运行时权限表（`requestedPermissions` 反射 `PermissionInfo.loadLabel`） | 权限清单参照 |
| ├─ `app/src/main/java/com/androlua/LuaActivity.java` | 引擎主 Activity（L1508-1513 `theme` 字段 → `android.R.style` 反射 `setTheme`） | 打包产物主题运行时语义 |
| ├─ 其余 `app/src/main/java/`、`app/src/main/assets/` | 引擎 Java 全量、luadoc、布局 helper 等 | 引擎/API 参照 |
| `reference/squircle-ce-master/` | Squircle CE 开源 App（基于 sora-editor 的完整 IDE，多模块 Gradle） | 编辑器开发参照 / UI 对齐标杆 |
| `reference/project/` | 参考工程（pro 已验证可运行）：demo、test、testjson、kleinbottle、DemoProject、probe、builddemo、「Sora editor」、简、石头、看板娘、雷达统计图（含 `libs/*.dex`） | 双端验证 / 探针 / 验收素材 |
| `reference/libs/` | 以 dex 方式分发的库源码：BaiduMTJ、SmartRefreshLayout、umeng | 编译 `.dex` 测试（低频） |
| `reference/代码手册社区/` | 社区帖子语料（`lua_files/` 45 帖 2144 篇 + `lua_manual.db` + `fetch_lua.py`） | community-code-audit 数据源；归纳见 `docs/kb/androlua-community/` |
| `reference/security-model.md` | 安全模型说明（被 `app/src/main/res/xml/androlua_filepaths.xml` 引用） | 文档 |
| `reference/kotlin-logging/` | **kotlin-logging v8.0.5**（KMP 日志门面，commit `0aaa57c` 2026-05-27；androidTarget 内置，`KLoggerAndroid` 直连 `android.util.Log`；`Slf4jLoggerFactory` 默认 / `AndroidNativeLoggerFactory`（`-Dkotlin-logging-to-android-native`）/ `DirectLoggerFactory` + `Appender` 三工厂） | 日志唯一事实源（2026-08-15 用户拍板：日志猫全面改用此库，禁自搓；重构工作单见 `docs/in-progress/KOTLIN_LOGGING_WORK_ORDER.md`） |
| `reference/kotlin-logging-example-android/` | kotlin-logging 官方 Android 示例（v5.0.2 时代 `kotlin-logging-android` 坐标 + `System.setProperty("kotlin-logging-to-android-native","true")` 一次性配置样板） | Android 集成姿势参照 |
| `reference/README.md` | reference 资产清单 + 已去重基线恢复配方 | 索引 |

## 2. `app/vendor/` — 上游构建模块（tracked，只读，参与构建）

| 路径 | 内容 | 关键位置 |
|---|---|---|
| `app/vendor/androlua/` | AndroLua 引擎（并入宿主 dex 的 `:androlua` 模块） | `src/main/java/com/androlua/LuaActivity.java`（主题反射 L1508-1513）；UPSTREAM.md 为改动台账唯一事实源 |
| `app/vendor/sora-editor/` | sora-editor 0.24.6（`editor` + `language-textmate` 两模块，LGPL-2.1） | `editor/src/main/java/io/github/rosemoe/sora/` |

## 3. LIDE 本方实现地图（`app/src/main/kotlin/com/lioisme/lide/`）

### 工程属性页（pro projectinfo 平移 + 2026-08-11 整改）
| 需求 | 文件 | 说明 |
|---|---|---|
| 工程属性页（改名/分组/主题下拉/SDK 下拉/权限弹窗） | `ui/screen/ProjectInfoScreen.kt` | 页面本体；读写 init.lua 经 `project/ProjectManager` + `project/LuaConfigParser` |
| 主题反射目录（android.R.style `Theme_*` + 别名 + 深色推断） | `ui/theme/ThemeCatalog.kt` | 2026-08-11 新增 |
| 共享权限搜索选择器 | `ui/screen/SearchablePermissionPicker.kt` | 2026-08-11 新增；工程属性页弹窗 + 工程权限页共用 |
| 权限清单（51 项宿主声明全覆盖 + 别名 + 分组） | `ui/screen/PermissionCatalog.kt` | 唯一事实源（P4b） |
| 工程权限页 | `ui/screen/PermissionScreen.kt` | 宿主运行时页（`HostPermissionScreen`）不动；工程页（`ProjectPermissionScreen`）接入搜索 |
| 工程信息入口 | `ui/screen/ProjectListScreen.kt`（L393-425 长按底单）、`ui/screen/EditorScreen.kt`（⋮ 菜单）、`ui/navigation/LideNavHost.kt`（`Routes.PROJECT_INFO`） | 文案统一「工程属性」 |
| init.lua 字段读写 | `project/LuaConfigParser.kt`、`project/ProjectManager.kt`（`readInitContent`/`writeInitFields`/`loadSdkVersions`）、`project/PermissionResolver.kt` | B-1 域服务收敛，UI 不自摸文件 |

### 打包链（SDK 覆盖 / 权限白名单 / 直接构建）
| 主题 | 文件 | 说明 |
|---|---|---|
| 二进制 manifest 补丁（uses-sdk 双形态读写、权限增删、launcher 重定向） | `build/AXmlManifest.kt` | `ManifestEdit.minSdkVersion`/`targetSdkVersion`（null=保持宿主） |
| 打包装配 | `build/ApkBuilder.kt` | `ProjectInput`（`minSdkVersion`/`targetSdkVersion`/`declaredPermissions`）；权限白名单 = 声明 ∩ 宿主 + 自声明（L190） |
| 构建 APK 直接构建（无配置对话框） | `ui/screen/BuildApkDialog.kt` | 2026-08-11 由 BuildPackagingDialog 重构；配置一律以设置页持久化 `BuildOpts` + 工程属性 init.lua 为准 |
| 打包通用选项持久化 | `ui/theme/SettingsManager.kt`（`lide_build` 域）↔ `build/BuildOpts.kt` | 签名/ABI 偏好/编译加密/安装方式/输出目录 |
| SDK 档位 / 主题 / 权限相关单测 | `app/src/test/kotlin/com/lioisme/lide/build/AXmlManifestTest.kt`、`.../ui/theme/ThemeCatalogTest.kt`、`.../ui/screen/PermissionCatalogTest.kt` | 打包链改动必跑 `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` |

### 运行时消费
| 主题 | 文件 | 说明 |
|---|---|---|
| 工程 theme 深色跟随（WP-014） | `ui/screen/EditorScreen.kt` L112-126（`produceState` → `ThemeCatalog.resolveDark`） | 未知值回落宿主主题 |
| 打包产物主题生效 | `app/vendor/androlua/.../LuaActivity.java` L1508-1513 | 反射 `android.R.style`，只认 `Theme_*` 名 |

## 4. 红线约定

1. 上表所有路径均为**本地已有**，改相关功能前先读本地；禁止以任何方式从云端拉取替代。
2. `reference/` 与 `app/vendor/` 只读（`docs/README.md` 维护约定第 4 条）；vendor 改动台账以 `app/vendor/androlua/UPSTREAM.md` 为准。
3. 文末 URL 仅溯源备注（不构成「拉取许可」）。

## 5. 溯源出处（备注，非获取途径）

- AndroLua Pro v4.4.2（master 分支）：https://github.com/nirenr/AndroLuaPro
- sora-editor v0.24.6：https://github.com/Rosemoe/sora-editor （tag `v0.24.6`）
- Squircle CE：https://github.com/microsoft/Squircle-CE （本地 `reference/squircle-ce-master/`）
- komi-store：本地 `reference/komi-store-main/`（未列入上表时以 `reference/README.md` 为准）
- kotlin-logging v8.0.5：https://github.com/oshai/kotlin-logging （本地 `reference/kotlin-logging/`；2026-08-15 直连 GitHub 被重置，经 `ghfast.top` 镜像克隆）
- kotlin-logging-example-android：https://github.com/MicroUtils/kotlin-logging-example-android （本地 `reference/kotlin-logging-example-android/`；同上经镜像克隆）
