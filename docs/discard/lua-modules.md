# Lua 模块详解（Knowledge Base）

> ⚠️ **已过期**：本文描述旧 AndroLuaX 的 `assets/ui` 业务 Lua 模块树；当前宿主为 Kotlin/Compose
> （Lua 仅作脚本语言，宿主框架层不再用 Lua 实现）。现状以 [kb/ENGINE_RUNTIME.md](../kb/ENGINE_RUNTIME.md) 为准。

> **类型**：知识库 · 模块说明书
> **用途**：修改/新增 Lua 业务功能时，了解每个模块的职责、入口、相互依赖
> **核对时间**：2026-08-03 · 基于实际文件内容

---

## 1. 模块加载机制

Lua 层模块按 `package.path` 查找。`assets/ui/main.lua:1` 注册了加载路径：

```lua
package.path = activity.getLuaPath("ui/?.lua;")..activity.getLuaPath("ui/lua/?.lua;")..
               activity.getLuaPath("ui/?/init.lua;")..package.path
```

即：`ui/x.lua`、`ui/lua/x.lua`、`ui/x/init.lua` 三种形态均可 require。主界面通过 `require "ui.main"` 被启动脚本加载。

## 2. 新 UI 模块（assets/ui/，现行主框架）

### 2.1 `ui/main.lua` — 主界面（项目列表，300 行）

**入口**：`activity.newActivity("ui/main")`（由 assets/main.lua 调用）

| 函数 | 行号 | 职责 |
|---|---|---|
| `dp2px(dp)` | 43 | dp→px 换算 |
| 工具栏设置 | 44–62 | MaterialToolbar + 设置齿轮按钮（读 `ic_settings.png`） |
| `onItemClick` | 71 | 点击项目 → 打开 |
| `onItemLongClick` | 77 | 长按 → 操作菜单 |
| `onRefresh` | 84 | 下拉刷新 |
| `scanProjects()` | 90 | 扫描 `AppProjects` 目录，收集 init.lua 项目 |
| `normalise(p)` | 96 | 路径规范化 |
| `getInfo(path)` | 99 | 读取项目 init.lua 字段（appname/packagename/appver）→ Lua table（**替代原 Project.kt**） |
| `loadProjects()` | 145 | 构建列表适配器并渲染（**替代原 ProjectAdapter.kt**） |
| `openProject(proj)` | 172 | 打开项目（newActivity 到编辑器） |
| `showProjectActions(proj)` | 176 | BottomSheet 操作菜单：运行/属性/打包/Dex 配置 |
| `onKeyDown(e)` | 287 | 返回键二次确认退出 |
| `onCreate(s)` | 298 | 主题初始化 + 加载布局（loadlayout(layout.projectList)） |

**依赖**：`ui.strings`、`ui.icons`、`old.javaapi.android`、`old.javaapi.androidXAndMaterialClasses`、`old.layout`、`old.lua.BottomSheet`、`autotheme`、`bin`。

### 2.2 `ui/icons.lua` — 图标加载器（38 行）

- `icons.load(name)`：从 `assets/icons/<name>.xml` 读 VectorDrawable，带内存缓存，失败打 Log 返回 nil。
- `icons.loadResource(name)`：失败时回退 `R.drawable.icon`。
- **用途**：审计整改 Phase 3 的产物，替代 `Material3R.drawable.*` 业务图标引用。

### 2.3 `ui/strings.lua` — 业务字符串（11 条）

审计整改 Phase 2 的产物。定义：`create_project`、`no_projects`、`no_projects_hint`、`new_project_title`、`project_name_hint`、`package_name_hint`、`cancel`、`confirm`、`action_run`、`action_properties`、`action_package`。
使用模块模式：`local M = {} ... return M`。

### 2.4 `ui/settings/main.lua` — 设置界面

- 获取 Material 主题色（colorPrimary/colorSurface/... 共 8 个 attr）。
- 构建设置项 UI（MaterialTextView/MaterialCardView/MaterialDivider）。
- 依赖 `autotheme`（动态主题）、`ui.icons`。

### 2.5 `ui/dexmanager/main.lua` — Dex 管理器（新增）

- 复用系统库 `dex_manager`（resources/lua/dex_manager.lua）。
- Material 组件：FAB（新建 dex 包）、TextInputLayout、MaterialTextView。
- 功能：管理 dex 文件集合，供运行时动态加载。

### 2.6 `ui/project_dex_config/main.lua` — 项目 Dex 配置（新增）

- 单个项目的 Dex 启用/禁用配置面板（MaterialSwitch / MaterialButton / MaterialCardView）。
- 数据来源同 `dex_manager` 系统库。

## 3. 旧 UI 模块（assets/ui/old/，归档参考）

> **警告**：`main.lua` 启动时**不会删除** old/（assets 只增不删），但旧版本遗留的运行时代码会被 `assets/main.lua` 清理（staleDirs 列表）。old/ 中仍被现行代码引用的文件：

| 模块 | 引用方 | 说明 |
|---|---|---|
| `old/javaapi/android.lua` + `androidXAndMaterialClasses.lua` | ui/main.lua:20-21 | Java 类名清单（IDE 补全/导入用） |
| `old/layout.lua` | ui/main.lua:28 | 布局表定义（projectList/projectItem/project） |
| `old/lua/BottomSheet.lua` | ui/main.lua:177 | 项目操作 BottomSheet 弹层 |

其余（editor/help/logcat/package/plugin/project/projectinfo/layouthelper/luadoc 等）为历史功能，按需参考实现，**不要在旧目录添加新功能**。

## 4. 系统库（app/src/main/resources/lua/，脚本分发标准库）

> 构建打包时会随脚本分发（build_all.ps1 处理）。任何用户脚本 require 的都是这份。

| 模块 | 功能摘要 |
|---|---|
| `import.lua` | 类导入（import "android.app.*"） |
| `http.lua` | 同步/异步 HTTP（get/post/download/upload） |
| `socket.lua` + `socket/url.lua` `headers.lua` `tp.lua` | LuaSocket 网络（TCP/UDP/HTTP/URL 解析） |
| `json.lua` / `xml.lua` / `yaml(仅 native lib)` | 数据序列化 |
| `base64.lua` / `hex.lua` / `md5(仅 native)` | 编解码 |
| `dex_manager.lua` | **Dex 动态管理（被新 UI 复用）** |
| `permission.lua` | 运行时权限请求封装 |
| `autotheme.lua` | 自动主题（深色模式检测） |
| `console.lua` / `logcat.lua` | 日志输出与 logcat 查看 |
| `bin.lua` | 二进制读写工具（被 ui/main.lua require） |
| `su.lua` | root 命令执行 |
| `bmob.lua` / `ftp.lua` / `smtp.lua` | 后端/传输 |
| `androidx.lua` | AndroidX 类映射 |
| `check.lua` / `test.lua` / `options.lua` | 开发辅助 |

## 5. androlua 运行时内置库（androlua/src/main/resources/lua/）

| 模块 | 职责 |
|---|---|
| `import.lua` | 运行时基础导入（随 jar 分发） |
| `loadlayout.lua` | 布局表 → View 构建 |
| `loadbitmap.lua` | 图片加载 |
| `loadmenu.lua` | 菜单加载 |

---

> 相关文档：[code-map.md](code-map.md) | [project-overview.md](../kb/project/project-overview.md)