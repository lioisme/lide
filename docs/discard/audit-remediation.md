# 代码审计与违规整改（已完成）

> **状态**：✅ 已完成（Phase 1–3 已落地；Phase 4 完整性验证待设备实测）
> **文档版本**：2.0（2026-08-03 复核更新）
> **审计日期**：2026-07-27  
> **审计范围**：全量 Kotlin 源代码（`.kt`）与 Android 资源文件（`res/`）  
> **审计依据**：`docs/knowledge/architecture.md` 开发约定 + `wiki/AndroLuaX-main/` 参考基线  
> **项目架构**：Lua 为主力开发语言，Kotlin/res/ 为底层支撑，禁止业务功能侵入底层

## 0. 执行结果复核（2026-08-03 对照工作区）

| 整改项 | 计划 | 实际状态 |
|---|---|---|
| 删除 4 个业务 Kotlin 文件 | Phase 1 | ✅ 已删除（`app/src/main/java/` 仅剩 `LuaMaterialDialog.kt`） |
| 删除 3 个业务布局 | Phase 1 | ✅ 已删除（`res/` 下无 `layout/` 目录） |
| 业务字符串迁移 Lua | Phase 2 | ✅ `assets/ui/strings.lua` 已建（11 条），`strings.xml` 仅剩 `app_name` |
| 图标迁移 assets | Phase 3 | ✅ 19 个图标在 `assets/icons/`，`ui/icons.lua` 已建 |
| 构建/功能验证 | Phase 4 | ⏳ 未执行设备实测（需 `./gradlew :app:assembleDebug` + 真机） |

> 本文档保留原始完整整改计划与验收标准，供回归验证时参考。

---

## 目录

1. [架构原则与合规标准](#1-架构原则与合规标准)
2. [违规总览](#2-违规总览)
3. [详细违规清单](#3-详细违规清单)
4. [整改工作清单](#4-整改工作清单)
5. [回滚预案](#5-回滚预案)
6. [验收标准](#6-验收标准)
7. [附录 A：Lua 层等效代码对照索引](#7-附录-a-lua-层等效代码对照索引)
8. [附录 B：文件依赖关系图](#8-附录-b-文件依赖关系图)

---

## 1 架构原则与合规标准

### 1.1 项目分层架构

```
┌──────────────────────────────────────────────────┐
│                     Lua 层                        │
│  (assets/ + resources/lua/ 下的 .lua 文件)        │
│  业务逻辑 · UI 布局 · 事件处理 · 数据管理          │
├──────────────────────────────────────────────────┤
│               Kotlin 层（底层支撑）                 │
│  (app/src/main/java/ 下的 .kt 文件)               │
│  仅限：基础设施桥接 · JNI 封装 · Lua 运行时绑定    │
├──────────────────────────────────────────────────┤
│               Res 层（底层支撑）                    │
│  (res/ 下的 XML/PNG 资源)                         │
│  仅限：主题定义 · 系统配置 · 通用样式 · 应用图标    │
└──────────────────────────────────────────────────┘
```

### 1.2 合规判定矩阵

| 代码/资源 | 允许范围 | 禁止范围 | 判定依据 |
|---|---|---|---|
| **Kotlin 类** | Lua 运行时桥接、JNI 封装、系统级 API 包装 | 业务数据模型、业务逻辑、业务 UI 组件、业务 Activity/Fragment | `README.md` §1 |
| **Res 布局** | 系统级 UI（Welcome 屏幕）、主题预览布局 | 业务列表界面、业务对话框、业务列表项模板 | `README.md` §1 |
| **Res 字符串** | 应用元数据（`app_name`）、系统提示 | 业务菜单文案、状态提示、操作描述 | `README.md` §1 |
| **Res 可绘制对象** | 应用图标（`icon.png`）、主题装饰图形 | 业务操作图标、业务属性图标、业务分类图标 | `README.md` §1 |
| **Res 颜色/样式/主题** | Material Design 主题定义、颜色系统、样式系统 | 允许（属于底层主题系统） | — |
| **AndroidManifest** | Activity/Service/权限声明 | 不允许声明业务 Activity（如 `ProjectListActivity`） | `README.md` §1 |
| **Build 配置** | 依赖声明、SDK 版本、构建参数 | 不允许添加业务插件或自定义业务构建逻辑 | `README.md` §1 |

### 1.3 红线规则

以下行为视为**严重违规**，必须无条件整改：

1. **在 Kotlin 层实现业务 UI**：任何 `Activity`、`Fragment`、`Adapter`、`View` 子类若渲染业务数据或处理业务交互，即违规。
2. **在 Kotlin 层实现业务数据模型**：任何 `data class`、`POJO`、`entity` 若代表业务领域概念（Project、User、Task 等），即违规。
3. **在 Res 层定义业务布局**：任何 `layout/*.xml` 若描述业务界面（列表、详情、表单、对话框），即违规。
4. **在 Res 层定义业务字符串**：任何 `values/strings.xml` 中若包含业务文案（功能名称、操作提示、状态消息），即违规。

---

## 2 违规总览

| 违规域 | 严重违规 | 中等违规 | 轻微违规 | 合计 |
|---|---|---|---|---|
| Kotlin 层 (`app/src/main/java/`) | 4 文件 | 0 | 0 | **4** |
| Res 层布局 (`layout/`) | 3 文件 | 0 | 0 | **3** |
| Res 层字符串 (`values/strings.xml`) | 0 | 1 文件（15 条） | 0 | **1** |
| Res 层图标 (`drawable/`) | 0 | 0 | 19 文件 | **19** |
| **总计** | **7** | **1** | **19** | **27** |

### 2.1 违规文件完整列表

| # | 文件路径 | 类别 | 严重度 | 说明 |
|---|---|---|---|---|
| 1 | `app/.../androluax/Project.kt` | Kotlin 业务模型 | 🔴 严重 | 业务 data class，wiki 无此文件 |
| 2 | `app/.../androluax/ProjectScanner.kt` | Kotlin 业务逻辑 | 🔴 严重 | 项目扫描解析逻辑，wiki 无此文件 |
| 3 | `app/.../androluax/ProjectAdapter.kt` | Kotlin 业务 UI | 🔴 严重 | RecyclerView 适配器，wiki 无此文件 |
| 4 | `app/.../androluax/ProjectListActivity.kt` | Kotlin 业务 Activity | 🔴 严重 | 完整项目管理 Activity，wiki 无此文件 |
| 5 | `res/layout/activity_project_list.xml` | Res 业务布局 | 🔴 严重 | 项目列表 UI，wiki 无 `layout/` 目录 |
| 6 | `res/layout/dialog_create_project.xml` | Res 业务布局 | 🔴 严重 | 创建项目对话框，wiki 无此文件 |
| 7 | `res/layout/item_project.xml` | Res 业务布局 | 🔴 严重 | 项目列表项模板，wiki 无此文件 |
| 8 | `res/values/strings.xml` | Res 业务字符串 | 🟡 中等 | 15 条业务字符串，wiki 无此文件 |
| 9–27 | `res/drawable/ic_*.xml`（19 个） | Res 业务图标 | 🟢 轻微 | 业务向量图标，wiki 无这些文件 |

---

## 3 详细违规清单

### 3.1 `Project.kt` — 严重违规

**文件**：`app/src/main/java/com/jesse205/androluax/Project.kt`（9 行）

**违规描述**：定义了业务数据模型 `Project`（name, packageName, version, path, iconPath），属于典型业务实体。

```kotlin
data class Project(
    val name: String,
    val packageName: String,
    val version: String,
    val path: String,
    val iconPath: String?
)
```

**Lua 层等效实现**：`assets/ui/main.lua:99–113` 的 `getInfo()` 函数返回同构的 Lua table：

```lua
local function getInfo(path)
    local env = {}
    loadfile(path .. "/init.lua", "bt", env)()
    return {
        name = env.appname or File(path).getName(),
        package = env.packagename or "",
        version = env.appver or "1.0",
        path = path,
    }
end
```

### 3.2 `ProjectScanner.kt` — 严重违规

**文件**：`app/src/main/java/com/jesse205/androluax/ProjectScanner.kt`（53 行）

**违规描述**：实现项目目录扫描、`init.lua` 文件解析、字段提取逻辑（appname / packagename / appver）。

| 违规方法 | 行号 | 违规原因 |
|---|---|---|
| `scanProjects()` | 9–20 | 扫描业务数据目录 |
| `getProjectDir()` | 22–33 | 定位业务存储路径 |
| `parseProject()` | 35–47 | 解析业务配置文件 |
| `extractField()` | 49–52 | 提取配置字段 |

**Lua 层等效实现**：`assets/ui/main.lua:87–140` 的 `scanProjects()` 函数实现完全相同的功能。

### 3.3 `ProjectAdapter.kt` — 严重违规

**文件**：`app/src/main/java/com/jesse205/androluax/ProjectAdapter.kt`（97 行）

**违规描述**：RecyclerView.Adapter 实现项目列表的业务 UI 渲染。

| 违规功能 | 行号 | 说明 |
|---|---|---|
| 列表项图标加载 | 84–94 | 加载项目 icon.png 或默认图标 |
| 项目名称显示 | 80 | `tvName.text = project.name` |
| 包名显示 | 81 | `tvPackage.text = project.packageName` |
| 点击事件分发 | 37–39 | 业务点击处理 |
| 长按手势实现 | 41–69 | 自定义长按识别 + 事件回调 |

**Lua 层等效实现**：`assets/ui/main.lua:142–167` 的 `loadProjects()` 使用 `LuaAdapter` + `layout.projectItem` 完成相同渲染。

### 3.4 `ProjectListActivity.kt` — 严重违规

**文件**：`app/src/main/java/com/jesse205/androluax/ProjectListActivity.kt`（278 行）

**违规描述**：完整的业务 Activity，包含存储权限请求、项目扫描与合并、项目创建、模板生成、Activity 导航等功能。

| 违规功能模块 | 行号 | 违规等级 |
|---|---|---|
| 运行时权限请求（存储） | 69–106 | 🔴 严重 |
| 项目扫描加载（AsyncTask） | 108–130 | 🔴 严重 |
| 遗留项目路径扫描（`/sdcard/androlua/project/`） | 132–153 | 🔴 严重 |
| 项目打开导航（`com.androlua.Main`） | 160–165 | 🔴 严重 |
| 长按操作菜单触发 | 167–181 | 🔴 严重 |
| 创建项目对话框展示 | 183–200 | 🔴 严重 |
| 项目目录创建与模板文件生成（init.lua / main.lua / layout.aly） | 202–277 | 🔴 严重 |

**关键风险**：
- **Manifest 未注册**：`AndroidManifest.xml` 中无 `ProjectListActivity` 声明（对比 `wiki/` 版本完全一致）。此 Activity 可能是**死代码**，不会被系统启动，但亦可能通过 Lua 的 `newActivity()` 反射调用。
- **重复实现**：全部功能在 `assets/ui/main.lua:142–295` 中已有 Lua 层实现，Kotlin 版本构成代码冗余。

### 3.5–3.7 业务布局文件 — 严重违规

| 文件 | 行数 | Lua 等效定义 |
|---|---|---|
| `res/layout/activity_project_list.xml` | 75 | `assets/ui/old/layout.lua:27–52`（layout.projectList） |
| `res/layout/dialog_create_project.xml` | 43 | 应由 Lua MaterialAlertDialogBuilder 动态构建 |
| `res/layout/item_project.xml` | 60 | `assets/ui/old/layout.lua:178–207`（layout.projectItem） |

**说明**：三个 XML 布局文件在 `wiki/` 参考项目中完全不存在（wiki 无 `layout/` 目录），全部属于新增的业务 UI。Lua 层的 `old/layout.lua` 已定义等效的 `projectList` 和 `projectItem` 布局模板。

### 3.8 `values/strings.xml` — 中等违规

**文件**：`app/src/main/res/values/strings.xml`（15 行）

**违规字符串清单**：

| 资源 ID | 中文值 | 用途 | 违规理由 |
|---|---|---|---|
| `app_name` | AndroLuaX | 应用名称 | ⚠️ 边界：可视为元数据 |
| `create_project` | 创建工程 | FAB 内容描述 | ✅ 业务文案 |
| `no_projects` | 暂无工程 | 空状态提示 | ✅ 业务文案 |
| `no_projects_hint` | 点击右下角按钮创建第一个工程 | 空状态引导 | ✅ 业务文案 |
| `new_project_title` | 新建工程 | 对话框标题 | ✅ 业务文案 |
| `project_name_hint` | 工程名称 | 输入框 hint | ✅ 业务文案 |
| `package_name_hint` | 包名 | 输入框 hint | ✅ 业务文案 |
| `cancel` | 取消 | 操作按钮 | ⚠️ 边界：通用 |
| `confirm` | 确定 | 操作按钮 | ⚠️ 边界：通用 |
| `action_run` | 运行 | 操作菜单项 | ✅ 业务文案 |
| `action_properties` | 属性 | 操作菜单项 | ✅ 业务文案 |
| `action_package` | 打包 | 操作菜单项 | ✅ 业务文案 |

**Lua 层引用（整改后）**：上述业务文案已迁移到 `assets/ui/strings.lua`，由 `ui/main.lua:191/198/205` 通过 `strings.action_*` 引用，`res/values/strings.xml` 仅保留 `app_name`。

### 3.9 `drawable/ic_*.xml`（19 个）— 轻微违规

**违规清单**：

| # | 文件名 | 用途 | 在 Lua 中被引用？ |
|---|---|---|---|
| 1 | `ic_add.xml` | 新建项目 FAB 图标 | ❌ |
| 2 | `ic_alpha_a_box_outline.xml` | 未知预留 | ❌ |
| 3 | `ic_app_channel.xml` | 项目配置-渠道 | ❌ |
| 4 | `ic_app_key.xml` | 项目配置-Key | ❌ |
| 5 | `ic_appcode.xml` | 项目配置-应用代码 | ❌ |
| 6 | `ic_appname.xml` | 项目配置-应用名称 | ❌ |
| 7 | `ic_appsdk.xml` | 项目配置-SDK | ❌ |
| 8 | `ic_appver.xml` | 项目配置-版本 | ❌ |
| 9 | `ic_code_json.xml` | JSON 标识 | ❌ |
| 10 | `ic_description.xml` | 描述标识 | ❌ |
| 11 | `ic_developer.xml` | 开发者标识 | ❌ |
| 12 | `ic_file.xml` | 文件标识 | ❌ |
| 13 | `ic_file_document.xml` | 文件文档图标 | ✅ `ui/main.lua:195` |
| 14 | `ic_folder.xml` | 文件夹标识 | ❌ |
| 15 | `ic_language_lua.xml` | Lua 语言图标 | ✅ `ui/main.lua:188` |
| 16 | `ic_language_markdown.xml` | Markdown 标识 | ❌ |
| 17 | `ic_package_variant_closed.xml` | 打包图标 | ✅ `ui/main.lua:202、209` |
| 18 | `ic_packagename.xml` | 包名标识 | ❌ |
| 19 | `ic_path_pattern.xml` | 路径模式标识 | ❌ |

---

## 4 整改工作清单

### 4.1 Phase 1：核心清理（预计耗时：30 分钟）

**目标**：移除 Kotlin 层业务代码和 res 层业务布局。

| 步骤 | 操作 | 命令/指令 | 前置条件 | 产出物 |
|---|---|---|---|---|
| 1.1 | 删除 `Project.kt` | `git rm app/src/main/java/com/jesse205/androluax/Project.kt` | 确认 Lua 侧 `ui/main.lua:getInfo()` 覆盖功能 | 文件移除 |
| 1.2 | 删除 `ProjectScanner.kt` | `git rm app/src/main/java/com/jesse205/androluax/ProjectScanner.kt` | 确认 Lua 侧 `ui/main.lua:scanProjects()` 覆盖功能 | 文件移除 |
| 1.3 | 删除 `ProjectAdapter.kt` | `git rm app/src/main/java/com/jesse205/androluax/ProjectAdapter.kt` | 确认 Lua 侧 `ui/main.lua:loadProjects()` 的 LuaAdapter 覆盖功能 | 文件移除 |
| 1.4 | 删除 `ProjectListActivity.kt` | `git rm app/src/main/java/com/jesse205/androluax/ProjectListActivity.kt` | 确认 Manifest 中无注册引用 + Lua 侧功能完整 | 文件移除 |
| 1.5 | 删除 `layout/activity_project_list.xml` | `git rm app/src/main/res/layout/activity_project_list.xml` | 确认 Lua 侧 `layout.projectList` 覆盖功能 | 文件移除 |
| 1.6 | 删除 `layout/dialog_create_project.xml` | `git rm app/src/main/res/layout/dialog_create_project.xml` | — | 文件移除 |
| 1.7 | 删除 `layout/item_project.xml` | `git rm app/src/main/res/layout/item_project.xml` | 确认 Lua 侧 `layout.projectItem` 覆盖功能 | 文件移除 |
| 1.8 | 清理 `R.*` 残留引用 | 全局搜索引用的 `R.layout.*`、`R.id.*`、`R.string.*` 确认无误 | 步骤 1.1–1.7 完成后 | 零引用残留 |
| 1.9 | 构建验证 | `./gradlew :app:assembleDebug` | 语法无残留 | APK 产出 |

**验证检查点（Phase 1）**：
- ✅ 项目列表显示正常（Lua 侧 `ui/main.lua:loadProjects()`）
- ✅ 下拉刷新正常（Lua 侧 `projectPanel.setOnRefreshListener`）
- ✅ 打开项目功能正常（Lua 侧 `openProject()`）
- ✅ 长按操作菜单正常（Lua 侧 `showProjectActions()`）

### 4.2 Phase 2：字符串迁移（预计耗时：30 分钟）

**目标**：移除 `values/strings.xml` 中的业务字符串，将字符串定义迁移至 Lua 层。

| 步骤 | 操作 | 详情 | 风险等级 |
|---|---|---|---|
| 2.1 | 在 `assets/ui/strings.lua` 中定义业务字符串常量 | 创建新文件 `assets/ui/strings.lua`，定义：<br>`local M = {}`<br>`M.action_run = "运行"`<br>`M.action_properties = "属性"`<br>`M.action_package = "打包"`<br>`return M` | 🟢 低 |
| 2.2 | 更新 `ui/main.lua` 中的字符串引用 | 将 `activity.getString(Material3R.string.action_run)` 替换为 `strings.action_run` | 🟡 中 |
| 2.3 | 删除 `res/values/strings.xml` 中的业务字符串 | 保留 `app_name`，移除其他 14 条字符串 | 🟢 低 |
| 2.4 | 更新所有可能引用业务字符串的其他 Lua 文件 | 全局搜索 `Material3R.string.*`，全部替换为 Lua 常量引用 | 🟡 中 |
| 2.5 | 构建验证 | `./gradlew :app:assembleDebug` | — |

**注意事项**：
- `app_name` 字符串建议保留在 `strings.xml` 中，属于应用元数据而非业务文案
- 若其他 Lua 模块（`old/projectinfo`、`old/editor` 等）也引用了 `R.string.*`，须一并在 Phase 2 中清理

### 4.3 Phase 3：图标资源迁移（预计耗时：45 分钟）

**目标**：将 19 个业务矢量图标从 `res/drawable/` 迁移至 `assets/icons/`。

| 步骤 | 操作 | 详情 | 风险等级 |
|---|---|---|---|
| 3.1 | 创建目标目录 | `mkdir -p app/src/main/assets/icons` | 🟢 低 |
| 3.2 | 复制 19 个 XML 图标至 `assets/icons/` | `cp app/src/main/res/drawable/ic_*.xml app/src/main/assets/icons/` | 🟢 低 |
| 3.3 | 从 `res/drawable/` 删除 19 个 XML 文件 | `git rm app/src/main/res/drawable/ic_*.xml` （排除非违规的 `icon.png` 和 `welcome.xml`） | 🟢 低 |
| 3.4 | 创建 Lua 图标加载模块 `assets/ui/icons.lua` | 提供 `loadIcon(name)` 函数，从 assets 读取 SVG 向量并转为 BitmapDrawable | 🔴 高 |
| 3.5 | 更新 `ui/main.lua` 中的 `Material3R.drawable.*` 引用 | 替换为 `icons.load("ic_language_lua")` 等 Lua 调用 | 🟡 中 |
| 3.6 | 更新 `old/projectinfo`、`old/editor` 等可能引用 `R.drawable.*` 的 Lua 文件 | 全局搜索 `Material3R.drawable.*` 或 `R.drawable.*` | 🟡 中 |
| 3.7 | 构建验证 | `./gradlew :app:assembleDebug` | — |

**图标加载 Lua 模块参考实现（`assets/ui/icons.lua`）**：

```lua
-- icons.lua — 从 assets 加载图标
local icons = {}

function icons.load(name)
    local path = "icons/" .. name .. ".xml"
    local InputStream = luajava.bindClass("java.io.InputStream")
    local ByteArrayOutputStream = luajava.bindClass("java.io.ByteArrayOutputStream")
    local VectorDrawableCompat = luajava.bindClass("androidx.vectordrawable.graphics.drawable.VectorDrawableCompat")

    local stream = activity.getAssets().open(path)
    local buffer = ByteArrayOutputStream()
    local buf = luajava.newArray("byte", 1024)
    local len
    len = stream.read(buf)
    while len > 0 do
        buffer.write(buf, 0, len)
        len = stream.read(buf)
    end
    stream.close()
    local data = buffer.toByteArray()

    local XmlPullParser = luajava.bindClass("android.util.Xml")
    local parser = XmlPullParser.newPullParser()
    parser.setInput(luajava.newInstance("java.io.ByteArrayInputStream", data), "UTF-8")

    local drawable = VectorDrawableCompat.createFromXml(activity.getResources(), parser)
    return drawable
end

return icons
```

### 4.4 Phase 4：完整性验证（预计耗时：30 分钟）

| 步骤 | 验证项 | 方法 | 预期结果 |
|---|---|---|---|
| 4.1 | 编译验证 | `./gradlew clean :app:assembleDebug` | BUILD SUCCESSFUL |
| 4.2 | 代码审计复检 | 全局搜索 `com.jesse205.androluax` 包路径 | 仅剩 `LuaMaterialDialog.kt`（合规） |
| 4.3 | 业务功能 - 项目列表 | 启动应用，查看项目列表 | 项目列表正常显示 |
| 4.4 | 业务功能 - 下拉刷新 | 下拉刷新操作 | 项目列表刷新正常 |
| 4.5 | 业务功能 - 打开项目 | 点击项目项 | 正常跳转编辑器 |
| 4.6 | 业务功能 - 长按菜单 | 长按项目项 | 操作菜单正常显示 |
| 4.7 | 资源审计 | `git diff HEAD` 检查无违规文件残留 | 仅合规文件变更 |
| 4.8 | Dead Code 审计 | 全局搜索 `ProjectListActivity`、`ProjectAdapter`, `ProjectScanner`, `Project(` | 零编译引用 |

### 4.5 注意事项清单

#### 4.5.1 操作注意事项

| # | 注意事项 | 关联步骤 | 说明 |
|---|---|---|---|
| 1 | **必须先确认 Lua 侧功能完整性**，再删除 Kotlin 文件 | Phase 1 | 在删除每个 Kotlin 文件前，手动执行对应的 Lua 功能路径，确保 Lua 层可正常工作 |
| 2 | **禁止直接删除 `app_name` 字符串** | Phase 2, Step 2.3 | `app_name` 属于应用元数据，需保留在 `strings.xml` 中 |
| 3 | **图标迁移必须逐步替换** | Phase 3, Step 3.5–3.6 | 不能批量替换所有 Lua 引用，应逐文件、逐行替换并测试 |
| 4 | **Kotlin 文件删除前需检查反射调用** | Phase 1 | 搜索 `LuaHelper`、`luajava.bindClass` 中是否引用了 `com.jesse205.androluax.*` 类，避免运行时 ClassNotFoundException |
| 5 | **布局文件名与 R.id 的绑定关系** | Phase 1 | 删除 XML 布局文件前，确认没有其他非业务代码（如 `LuaMaterialDialog`）引用了这些布局 ID |

#### 4.5.2 架构注意事项

| # | 注意事项 | 说明 |
|---|---|---|
| 6 | **LuaActivity 作为唯一业务 Activity** | 所有业务 UI 应通过 `com.androlua.LuaActivity` 或 `com.androlua.LuaActivityX` 承载，不应新增独立 Activity |
| 7 | **禁止新增 Kotlin 业务文件** | 任何新增的业务功能应通过 Lua 层实现。如需新增 Kotlin 文件，必须属于 Lua 运行时基础设施范畴 |
| 8 | **Res 资源仅限主题/系统级** | 新增资源前先自问：这个资源是为"业务功能"服务的，还是为"系统/主题"服务的？ |
| 9 | **Lua 层引用 res 资源的依赖方向** | 允许 Lua → res（Lua 代码读取 Android 资源），但禁止 res → Lua（资源不应依赖于 Lua 运行时） |

#### 4.5.3 代码质量注意事项

| # | 注意事项 | 说明 |
|---|---|---|
| 10 | **Lua 层的 `strings.lua` 应使用 `local` 作用域** | 避免污染全局 namespace，应使用 module return 模式 |
| 11 | **图标加载模块应考虑空保护** | 当 assets 中图标文件缺失时，应返回默认占位图标而非崩溃 |
| 12 | **编译前必须执行 `clean`** | `./gradlew clean` 确保无缓存残留引用 |
| 13 | **使用 Git 分支管理整改过程** | 建议创建 `fix/cleanup-business-code` 分支，每个 Phase 单独 commit |
| 14 | **保留 wiki 参考项目作为唯一合规基线** | 整改结束后，`wiki/AndroLuaX-main/` 不应被修改，始终作为合规判断的黄金参考 |

---

## 5 回滚预案

### 5.1 按 Phase 回滚

| Phase | 回滚命令 | 影响范围 |
|---|---|---|
| Phase 1 | `git checkout HEAD~1 -- app/src/main/java/.../*.kt app/src/main/res/layout/*.xml` | 恢复 4 个 Kotlin 文件 + 3 个布局文件 |
| Phase 2 | `git checkout HEAD~1 -- app/src/main/res/values/strings.xml assets/ui/strings.lua assets/ui/main.lua` | 恢复 strings.xml + Lua 字符串模块 |
| Phase 3 | `git checkout HEAD~1 -- app/src/main/res/drawable/ assets/icons/ assets/ui/icons.lua` | 恢复图标文件 |

### 5.2 全量回滚

```bash
git checkout main
git branch -D fix/cleanup-business-code
```

---

## 6 验收标准

### 6.1 构建验收（Gate 1）

```bash
./gradlew clean :app:assembleDebug
# 期望：BUILD SUCCESSFUL in Xs
# 禁止出现：Unresolved reference: R.layout.* / Unresolved reference: Project / etc.
```

### 6.2 二进制验收（Gate 2）

```bash
# 确认 APK 中不包含违规类的字节码
javap -cp app/build/intermediates/javac/debug/classes/com/jesse205/androluax/* 2>/dev/null
# 期望：仅输出 LuaMaterialDialog (无 Project, ProjectAdapter, ProjectScanner, ProjectListActivity)
```

### 6.3 功能验收（Gate 3）

| 场景 | 操作 | 预期结果 |
|---|---|---|
| 应用启动 | 点击桌面图标 | 进入项目列表界面，标题显示"工程列表" |
| 项目列表 | 等待加载 | 显示已有的 Lua 项目（按名称排序） |
| 下拉刷新 | 下拉列表 | 列表刷新，显示更新后的项目 |
| 打开项目 | 点击项目 | 跳转至编辑器 UI |
| 长按项目 | 长按项目项 | 显示 BottomSheet 操作菜单（运行/属性/打包/Dex 配置） |
| 返回退出 | 按两次返回键 | 提示"再按一次返回"；再次按下退出 |

### 6.4 代码审计验收（Gate 4）

```bash
# 确认无违规引用
grep -r "com\.jesse205\.androluax\.Project" app/src/main/java/ --include="*.kt"
grep -r "R\.layout\.activity_project_list" app/src/ --include="*.kt" --include="*.lua"
grep -r "R\.layout\.dialog_create_project" app/src/ --include="*.kt" --include="*.lua"
grep -r "R\.layout\.item_project" app/src/ --include="*.kt" --include="*.lua"
# 以上全部应返回空
```

---

## 7 附录 A：Lua 层等效代码对照索引

> **注（2026-08-03）**：`ui/main.lua` 在审计后已重构（新版 300 行），下表行号为审计当时版本。重构后函数位置：`scanProjects()`→:90、`getInfo()`→:99、`loadProjects()`→:145、`openProject()`→:172、`showProjectActions()`→:176。

| Kotlin 违规文件 | Lua 等效文件 | Lua 函数/定义 | 重构后行号 |
|---|---|---|---|
| `Project.kt` | `assets/ui/main.lua` | `getInfo()` 函数 | :99 |
| `ProjectScanner.kt` | `assets/ui/main.lua` | `scanProjects()` 函数 | :90 |
| `ProjectScanner.kt` | `assets/ui/main.lua` | `extractField()` 对应 `init.lua` 的 `env.*`字段读取 | :99–137 |
| `ProjectAdapter.kt` | `assets/ui/main.lua` | `loadProjects()` 中的 LuaAdapter | :145 |
| `ProjectListActivity.kt` | `assets/ui/main.lua` | `loadProjects()` + `openProject()` + `showProjectActions()` | :145 / :172 / :176 |
| `activity_project_list.xml` | `assets/ui/old/layout.lua` | `layout.projectList` 定义 | 未变 |
| `item_project.xml` | `assets/ui/old/layout.lua` | `layout.projectItem` 定义 | 未变 |
| `dialog_create_project.xml` | `assets/ui/old/layout.lua` | `layout.project` 定义 | 未变 |

---

## 8 附录 B：文件依赖关系图

```
╔══════════════════════════════════════════════════╗
║           违规文件依赖关系                        ║
╠══════════════════════════════════════════════════╣
║                                                  ║
║  ┌──────────────────────┐                        ║
║  │  ProjectListActivity │───→ ProjectScanner     ║
║  │       (Activity)     │───→ Project            ║
║  │                      │───→ ProjectAdapter     ║
║  └──────┬──────┬───────┘                        ║
║         │      │                                ║
║         │      └──→ R.layout.activity_project_list║
║         │          R.layout.dialog_create_project ║
║         │          R.string.*                    ║
║         │                                        ║
║         └────→ com.androlua.Main (跨模块依赖)     ║
║                com.androlua.LuaActivity           ║
║                com.androlua.LuaApplication        ║
║                                                  ║
║  ┌──────────────────────┐                        ║
║  │   ProjectAdapter     │───→ R.layout.item_project║
║  │    (RecyclerView)    │───→ Project            ║
║  └──────────────────────┘                        ║
║                                                  ║
╚══════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════╗
║           合规 Kotlin 文件（保留）                ║
╠══════════════════════════════════════════════════╣
║                                                  ║
║  androlua/src/main/java/com/androlua/:           ║
║    ├── LuaDialog.kt          ← 对话框基础设施     ║
║    └── ... (其他由 wiki 迁移的 Lua 运行时文件)    ║
║                                                  ║
║  androlua/src/main/java/androidx/:               ║
║    └── AlertControllerBridge.kt ← API 桥接        ║
║                                                  ║
║  app/src/main/java/com/jesse205/androluax/:       ║
║    └── LuaMaterialDialog.kt  ← Material 对话框    ║
║                                                     ║
╚══════════════════════════════════════════════════╝
```

---

> **文档结束**  
> 本文档由代码审计工具自动生成，经人工复核确认。所有违规判定均基于 `README.md` 列明的开发约定和 `wiki/AndroLuaX-main/` 参考基线。
