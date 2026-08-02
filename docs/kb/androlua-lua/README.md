# AndroLua Lua 工程开发知识库

> 本目录是 **Lua 工程侧**（非 delua 工程侧）开发知识的持久化仓库：
> 在开发 / 移植 / 验证任何 androlua Lua 工程时发现的一切**能力、已知问题、方法论、环境事实**，
> 必须在此落库（一个文件夹、多篇文档、按主题分文件）。
>
> 与 `docs/kb/`（delua 工程知识库）的区别：本库只记录"写 Lua 工程"这一侧的事实与经验，
> delua / pro 引擎侧差异结论仍按原流程进 `docs/completed/` 与 `docs/kb/`。

---

## 文档结构

```
docs/kb/androlua-lua/
├── README.md        ← 本文件：索引 + 维护约定
├── ENVIRONMENT.md   ← 运行环境与配置：模拟器、双端 app、ADB 命令、工程路径、构建
├── FEATURES.md      ← 能力清单：import、loadlayout、线程定时器、libs 机制、原生库、缺失能力
├── PITFALLS.md      ← 已知问题与规避：缺陷现象、根因、规避/判定方法（含双端一致报错案例）
├── TECHNIQUES.md    ← 方法论与最佳实践：骨架、探针、诊断、libs 编译、最佳实践
└── SYNTAX_MANUAL.md ← AndroLua 语法手册（pro 4.4.2 + Lua 5.3 依据，源码静态审计，未经实测）
```

| 文件 | 记录什么 | 谁写入 |
|------|---------|--------|
| `ENVIRONMENT.md` | 运行环境事实：设备、包名、路径、命令（只改当环境变化时） | 首次配置/环境变更 |
| `FEATURES.md` | 可用 API/机制能力清单，`import "xxx"` 能力边界，pro 独有缺失项 | 每次验证新特性后 |
| `PITFALLS.md` | 已确认问题：现象 + 根因 + 规避/判定方法 | 每次确认新问题后 |
| `TECHNIQUES.md` | 方法论、最佳实践、可用代码片段、双端验证技巧 | 每次产出可复用方法论后 |
| `SYNTAX_MANUAL.md` | 语法规范参考：语言/标准库/luajava 互操作/宿主环境/布局/异步/import/模块/已知问题 | 源码审计 + 手册核对后（未经实测，仅供参考） |

## 维护约定（硬要求）

1. **每轮开发结束必须落库**：无论成果是能力确认、问题发现、方法论产出还是环境变化，
   在收尾（SKILL Phase 6 归档）前把发现写入对应文档；没有新发现也要检查一遍是否有遗漏。
2. 条目格式：**现象/事实 → 根因 → 规避或判定方法 → 验证命令**，可附最小代码片段；注明来源（实测/参考工程/审计报告）。
3. 只记录**已确认**的事实：双端实测验证过的标记 `✅双端一致`；单端确认的标记 `⚠仅pro` / `⚠仅delua`；推测性内容标记 `未确认` 并尽快验证补正。
4. 新增/修改条目后，在 `README.md` 的变更登记中追加一行（日期 + 主题 + 文件）。
5. 若某问题/能力已无价值（被修复或废弃），条目保留并标注 `已废弃/已修复`，不删除历史（保留证据链）。
6. 参考工程目录 `docs/reference/project/` 与库源码 `docs/reference/libs/` 是活样本，引用时写相对路径。
7. **可信度分级**：仅经源码静态审计、未经设备实测的结论，必须在文档头部标注"未经测试，仅供参考"，
   且与实测冲突时以实测为准（见 `SYNTAX_MANUAL.md` 头部的可信度声明）。

## 变更登记

| 日期 | 主题 | 文件 |
|------|------|------|
| 2026-08-01 | 知识库初始化（种子内容来自双端实测 + 审计报告） | 全部 |
| 2026-08-01 | 新增 AndroLua 语法手册（luajava.c / com.luajava / com.androlua 源码审计 + Lua 5.3 手册核对，未经实测） | SYNTAX_MANUAL.md |
| 2026-08-01 | 术语规范化：坑→已知问题与规避、特性→能力清单、方法→方法论与最佳实践；修复 PITFALLS.md 编码损坏；SYNTAX_MANUAL.md 增加未经实测声明 | 全部 |
| 2026-08-02 | Live2D 看板娘原生引擎验证收尾：GLES2 回调 gl 参数损坏 / 触摸坐标负存与双重变换 / flickAvailable 重置（PITFALLS 24-26）；原生 .so 直调 + 矩阵/手势复刻方法论（TECHNIQUES 8，dex 实证公式） | PITFALLS.md, TECHNIQUES.md |
| 2026-08-02 | 架构审计：引擎 .so 携带 Java JNI 桥残留（jp_live2d_sample_Live2DNative）已安全恢复（删 jni 桥 + Android.mk + mOwnDrag 死字段，重建 .so 并回归）；红线：引擎模块只允许 luaopen_* 入口（PITFALLS 27） | PITFALLS.md |
| 2026-08-02 | 运行环境全量对齐终审：L1 脚本（check.lua CRLF 修复、逐字节一致）/ L2 原生库（lfs/lsqlite3 误移除教训：源码树 Android.mk2 不等于官方 APK 打包，以设备 APK 为准；PITFALLS 28）/ L3 Java（LuaAccessibilityService 补 stopScreenshot，PITFALLS 29）/ L4 宿主；x64 双端实测 test 探针 81 项 diff 全为白名单、demo/DemoProject/testjson/kleinbottle 全一致 | PITFALLS.md, README.md |
