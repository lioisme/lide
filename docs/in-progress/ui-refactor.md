# UI 重构（进行中）

> **状态**：🚧 进行中
> **目标**：将旧版 Lua IDE 界面重构为新的 Material3 `assets/ui/` 框架，清理/归档旧模块
> **负责人**：本仓库维护者
> **开始时间**：2026-07-27（commit 6a45551 已推进一轮）
> **关联代码**：`app/src/main/assets/`（见下）

---

## 1. 已完成（首次重构落地，commit 6a45551）

- ✅ 新 `assets/ui/main.lua` 重写项目列表主界面（替换旧 Project*Kotlin 职责）
- ✅ 新 `assets/ui/icons.lua` 图标加载模块
- ✅ 新 `assets/ui/strings.lua` 业务字符串模块
- ✅ 新 `assets/ui/settings/main.lua` 设置界面
- ✅ 新 `assets/ui/dexmanager/main.lua` Dex 管理器（新功能）
- ✅ 新 `assets/ui/project_dex_config/main.lua` 项目 Dex 配置（新功能）
- ✅ 旧 UI 模块整体归档至 `assets/ui/old/`
- ✅ 19 个业务图标从 `res/drawable/` 迁至 `assets/icons/`
- ✅ 启动脚本 `assets/main.lua` 增加旧遗留数据清理逻辑

## 2. 当前结构中的遗留耦合（需注意）

新 `ui/main.lua` 仍 require 旧目录下的 3 个模块（**这是刻意的阶段性依赖**）：

| 旧模块 | 被引位置 | 作用 | 后续方向 |
|---|---|---|---|
| `old/javaapi/android.lua` + `androidXAndMaterialClasses.lua` | `ui/main.lua:20-21` | Java 类名清单（IDE 补全/导包） | 视需要保留或迁移 |
| `old/layout.lua` | `ui/main.lua:28` | `layout.projectList/projectItem/project` 布局表 | 迁移至新 `ui/` 或按需重构 |
| `old/lua/BottomSheet.lua` | `ui/main.lua:177` | 长按操作菜单弹层 | 迁移至 `ui/` |

## 3. 待办（本任务剩余）

| # | 事项 | 优先级 | 状态 |
|---|---|---|---|
| R1 | 真机/模拟器构建验证 `./gradlew :app:assembleDebug` | 高 | ⏳ 未执行 |
| R2 | 验证 Dex 管理器、项目 Dex 配置两新界面功能 | 高 | ⏳ 未执行 |
| R3 | 评估 old/ 剩余依赖（javaapi/layout/BottomSheet）是否迁移至新 ui/ 层级 | 中 | 💡 建议 |
| R4 | 清理 `assets/ui/old/` 无用代码（editor/help/logcat/layouthelper 等历史实现） | 低 | 💡 建议远期清理 |
| R5 | git 分支管理（建议 `refactor/ui` 分支）并拆分提交 | 低 | 💡 建议 |

## 4. 验收标准

- [ ] `assembleDebug` 构建通过
- [ ] 主界面：项目列表显示、下拉刷新、打开项目、长按菜单均正常
- [ ] settings / dexmanager / project_dex_config 三界面可进入且功能正常
- [ ] 旧 `ui/old/` 引用保持可用（若 T3 决定保留依赖）

---

> 关联：[lua-modules.md](../kb/project/lua-modules.md) | [TASKS.md](../TASKS.md)