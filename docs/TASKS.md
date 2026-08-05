# 任务看板（Task Board）

> **类型**：任务看板 · 状态实时更新
> **用途**：回答「项目做到哪了」「现在该做什么」
> **权威状态矩阵**：[kb/README.md](kb/README.md) §2（本文档是其摘要入口）

---

## 当前进行中

| 任务 | 文档 | 进度 / 状态 |
|---|---|---|
| 对齐 pro 宿主 IDE 功能（19 个 WP） | [in-progress/PARITY_COMPLETION_WORK_ORDER.md](in-progress/PARITY_COMPLETION_WORK_ORDER.md) | **13/19 完成**（WP-001/002/003/004/005/008/013/014/015/016/017/018/019，其中 WP-014 代码落地未真机验收）；未开工 WP-006/007/009/010/011/012 |
| 编辑器性能 V2 收尾 | [discard/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md](discard/EDITOR_PERFORMANCE_AUDIT_V2_REPORT.md) | ✅ 已完成归档（2026-08-05）：阶段 A/B 落地，剩余 R8/阶段 C 入 backlog |
| 编辑器 UI 对齐（Squircle-CE 标杆） | [in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md](in-progress/EDITOR_UI_ALIGNMENT_WORK_ORDER.md) | WP-003~006 ✅ 已完成；WP-001/002/007/008 ⛔ 被取代（抽屉已删除→文件浏览对话框）；WP-009/010 未完成 |
| 编辑器功能完善至 sora 原生上限（F8） | [planned/EDITOR_FEATURE_COMPLETION_WORK_ORDER.md](planned/EDITOR_FEATURE_COMPLETION_WORK_ORDER.md) | P0~P3 ✅ 已完成（设置中心 21 项 / Snippet / 多错误聚合+quickfix / 折叠闭环仅渲染 / 文本操作 / 整词搜索 / R8+baseline profile / 降级矩阵 / perf-check / sora 更新管线+CI+R1 job / 台账）；P4 文档 ✅；**剩设备功能验收 §8.3（模拟器复测推迟）**，通过后归档 discard |
| 编辑器全语言支持（39 种 TextMate 文法） | [planned/EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md](planned/EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER.md) | ✅ 已完成（2026-08-06）：资产迁入 `:editor` / LanguageCatalog+Detector+KeywordRegistry+EditorLanguage 替换 LuaLanguage / TextMateInitializer 全量注册 / LuaEditor 按文件 scope 装配；编译通过、R1~R5 复核过、APK 81 项资产核对、模拟器 Lua 高亮多色验证；全语言补全交互验收并入后续综合复测 |
| 社区代码知识库 | [kb/androlua-community/README.md](kb/androlua-community/README.md) | 2144 篇初筛完成；正负面卡 + 训练 seed 产出 |
| 独立 APK 打包器（WP-014，代码已落地） | [kb/ROADMAP.md](kb/ROADMAP.md) §1 | 代码落地 + 打包单测绿；未真机验收 |

## 已完成（摘要）

已完成文档 33 篇 + 过时文档 8 篇统一丢弃于 [discard/](discard/)（引擎迁移 WP-001~010、导航冻结修复、API 浏览器四件套、仓库硬化、Live2D 迁移、UI 重构/性能/盘点/双端验证等），清单与结论蒸馏去向见 [discard/README.md](discard/README.md) §3，矩阵见 [kb/README.md](kb/README.md) §2。

## 计划中 / 未排期

| 任务 | 文档 | 状态 |
|---|---|---|
| 技术债盘点（TD1~TD7） | [planned/backlog.md](planned/backlog.md) | 进行中（2026-08-05 清理：TD1/2/6/7 已核销，剩 TD3/4/5 + F1~F7） |
| Xposed 模块路线图（M1~M5） | [planned/XPOSED_MODULE_FEASIBILITY_REPORT.md](planned/XPOSED_MODULE_FEASIBILITY_REPORT.md) | 研究定稿，未启动 |
| 引擎性能优化建议 | [planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md](planned/GAME_ENGINE_PERFORMANCE_AUDIT_REPORT.md) | 审计完成，建议未排期（看板娘已原生化，石头未动） |
| 架构清理与重构（补丁式/重复代码收敛） | [planned/ARCHITECTURE_CLEANUP_AUDIT_REPORT.md](planned/ARCHITECTURE_CLEANUP_AUDIT_REPORT.md) | 进行中（2026-08-06：Phase A 全部 + Phase B 全部 + Phase C 部分已完成，见文档完成记录） |
| **LIDE 插件平台（小政府）立项：开放 Plugin SDK + LIDE 聚合 androlua 首个插件** | [planned/PLUGIN_PLATFORM_REPORT.md](planned/PLUGIN_PLATFORM_REPORT.md) | 设计定稿（2026-08-06，LIDE-DESIGN-2026-001），待立项；Phase 0~5 路线图见报告 §16（前审计 LIDE-AUDIT-2026-014 已归档 discard） |

## 维护规则

1. **状态机**：📋 计划中 → 🚧 进行中 → ✅ 已完成（不允许跳级）。
2. 新任务先登记 [planned/backlog.md](planned/backlog.md)；开始做时 `git mv` 到 [in-progress/](in-progress/)；**整份任务文档完成后 `git mv` 到 [discard/](discard/)**。
3. 每篇任务文档头部必须注明：状态、目标、验收标准、关联代码路径。
4. **逐项标记**：每完成一项任务，必须在任务文档对应条目标记「（已完成）」并备注工作内容 + 日期 + 验收证据（构建命令 / 设备验证 / 测试）。
5. 完成判断以**工作区真实代码/构建结果**为准；状态变化当天更新本文档 + 任务文档 + [kb/README.md](kb/README.md) §2 矩阵 + [discard/README.md](discard/README.md) §3 清单。