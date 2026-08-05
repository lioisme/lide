# 上游基线资产治理审计报告（reference 数据湖）

> 任务：对 `docs/reference/`（上游基线项目源代码与社区语料）做去重、分类、整理、归纳、归档全处理。
> 状态：**（已完成）** 2026-08-05 · 处置方向：用户拍板「文件分类去重，更新全部文档引用与 git 追踪，不留兼容/包袱」。

## 一、结论先行

`docs/reference/` 是**有意为之、gitignored（本地只读）的上游基线数据湖**（约 139 MB / 6115 文件），
但存在三处结构性问题需要治理：目录职责矛盾、索引不完整、与 `third_party/`/`app/src/main/cpp/`
存在同源双份（其中一份是构建工作副本，另一份是纯净比对基准——非真冗余，不能盲目删除）。

## 二、资产盘点（按用途分类）

| 资产 | 规模 | 用途 | 与构建体系关系 |
|---|---|---|---|
| `androlua/AndroLua_pro-master/` | 15.7 MB / 1135 | 纯净上游基线（v4.4.2，逐字节比对基准） | ↔ `third_party/androlua/`（工作副本，已修改，DIFFERS）；jni 600 文件 ↔ `app/src/main/cpp/` 506 文件（引擎源码移植来源） |
| `sora-editor-main/` | 11.2 MB / 824 | 纯净上游基线（0.24.6 完整树） | ↔ `third_party/sora-editor/`（vendored 最小构建，DIFFERS） |
| `squircle-ce-master/` | 10.4 MB / 1242 | 编辑器参考应用（sora 实际应用范式） | 无（纯参考） |
| `project/` | 12.3 MB / 245 | 参考工程/验收素材（含 `libs/*.dex`） | 无（androlua-project-validation 校验依据） |
| `libs/` | 75.1 MB / 523 | dex 分发库源码（BaiduMTJ/SmartRefreshLayout/umeng） | 可 d8/dx 编译进工程 libs/ 测试，低频 |
| `代码手册社区/` | 14.5 MB / 2146 | 社区语料原始库（45 类 lua_files + lua_manual.db + fetch_lua.py） | 已归纳 → `docs/kb/androlua-community/`（2171 文件/1.4 MB 提炼语料）；community-code-audit 数据源 |
| `README.md` + `security-model.md` | — | 索引 + 安全模型说明 | — |

## 三、审计发现的问题

1. **目录职责矛盾**：`docs/reference/README.md` 写「新增上游库源码入 `libs/`」，而根 README 与
   `settings.gradle.kts` 声明「上游库统一入 `third_party/`（只读）」——两条约定冲突，`libs/` 定位需澄清。
2. **索引不完整**：`security-model.md` 存在但未登记进索引；`squircle-ce-master` 在索引用例处
   与目录名大小写不一致；「缺失项（luaj-master、probe/builddemo）」未说明补齐动作与影响。
3. **同源双份**（设计如此，非缺陷，但需文档化）：androlua / sora-editor 在 reference 与 third_party
   各一份；jni 源码与 `app/src/main/cpp/` 同源。reference 侧是纯净基准，third_party/cpp 侧是工作副本。
4. **引用面**：21 个被跟踪文件引用 `docs/reference/`（另 9 个 discard 历史报告为只读记录），
   移动路径需同步修正（`.opencode/skills/`、`docs/kb/*` 7 处、`docs/in-progress/*` 3 处、
   `docs/planned/*` 1 处、根 README、`third_party/README.md`、`app/.../androlua_filepaths.xml` 注释等）。
5. **gitignore 现状**：`docs/reference/` 整目录忽略（`# Downloaded reference projects (never committed)`），
   且有 `参考资料/` 防重建守卫——本次治理不得破坏该守卫语义。

## 四、处理方案（去重 / 分类 / 整理 / 归纳 / 归档）

| 维度 | 动作 |
|---|---|
| 去重 | 不删除 reference 侧纯净基线（保留 diff 基准能力）；以「版本 pin + 上游 URL + 工作副本对照表」显式文档化双份关系；libs/ 与 project/ 内重复素材（如重复的 dex 库）合并 |
| 分类 | 维持三类：①纯净上游基线（androlua、sora-editor-main）②参考工程/素材（squircle-ce、project、libs）③社区语料（代码手册社区） |
| 整理 | 重写 `docs/reference/README.md` 索引：补 security-model.md、统一目录名、明确 libs/ 定位（参考素材而非上游库入口）、补缺失项状态 |
| 归纳 | ③类语料的归纳产物已存在于 `docs/kb/androlua-community/`；在索引中标注「原始库 ↔ 归纳库」映射，避免重复归纳 |
| 归档 | 按用户选定方向：A) 保持 `docs/reference/`（整顿索引即可）；B) 迁出 docs/ 至顶层 `reference/`（docs/ 纯文档化，改 21 处引用）；C) B + 删除与 third_party 同源的纯净基线（激进，不建议） |

## 五、验收标准

- 问题 1–3 全部消解（索引完整、矛盾消除、双份关系文档化）。
- 被跟踪引用全部指向新路径（git grep 零失效引用；discard 历史记录除外）。
- `git ls-files` 无 reference 资产入库（R5 保持）；`git check-ignore` 验证忽略规则仍生效。
- 全部动作完成后本报告标记（已完成）并 `git mv` 至 `docs/discard/`。

---

## 六、执行记录（已完成）

1. **分类**：reference 资产分三类——①纯净上游基线（androlua、sora-editor-main）②参考工程/素材（squircle-ce-master、project、libs）③社区语料（代码手册社区）。
2. **去重**：文件级 MD5 扫描零重复；**目录级去重**——删除已完整移植的纯净基线 `androlua/`（15.7 MB）与 `sora-editor-main/`（11.2 MB，共 1959 文件），恢复配方（上游 URL + 版本 pin + 已收纳去向表）写入新索引；许可证随迁 `third_party/androlua/LICENSE*.txt`；删 kb/README 中从未收录的 `luaj-master` 缺失项条目。
3. **归档迁移**：`docs/reference/` → 顶层 `reference/`（gitignored 不变，112.4 MB：squircle-ce-master、project、libs、代码手册社区、security-model.md）；`docs/reference/` 清空删除。
4. **引用更新**（21 个 live 文件 + discard 索引）：`.gitignore`（`reference/` + 防重建守卫）、`.opencode/skills/androlua-project-validation/SKILL.md`（8 处，L1 比对命令改写为恢复配方）、根 README、`androlua_filepaths.xml` 注释、`third_party/README.md`（3 处）、`docs/PROGRESS.md`（2 处）、`docs/kb/` 12 文件、`docs/in-progress/` 3 文件、`docs/planned/` 1 文件、`docs/discard/README.md` 索引（§2 导航 + 清单 39→40 篇）。语义改写：原纯净基线引用一律指向移植后的唯一真源（`third_party/androlua/` + `app/src/main/cpp/` 或 `reference/` 恢复配方）。
5. **验收证据**：
   - `git grep 'docs/reference'` 零失效引用（仅 `.gitignore` 防重建守卫与 1 处「迁出」历史说明 + discard 历史报告，均属有意保留）；
   - `git check-ignore reference/` 生效；`git ls-files` 无 reference 资产（R5 保持）；
   - `reference/` 本地 112.4 MB / 4754 文件就位，`docs/reference/` 已删除；
   - 新增顶层索引 `reference/README.md`（资产清单 + 去重配方 + 维护约定，消除「上游库入 libs/ 还是 third_party/」矛盾）。
6. **提交**：随同第一轮文档治理批次一并 `git add`/`commit`（详见 git log）。

> 历史性说明保留：`docs/discard/` 内 9 份历史报告的旧路径文字不改写（归档记录如实保留），与 live 文档路径分离。
