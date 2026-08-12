# 死代码/死依赖/死资源全量清理工作单（DEAD_CODE_CLEANUP）

> **登记**：2026-08-12，docs/planned/backlog.md §2o
> **来源需求**（用户 2026-08-12）：全量审计项目代码，清理所有死代码、死依赖、死资源；
> 列出清单并按合理开发顺序自驱动完成；每项完成标记「（已完成）」+ 工作内容 + 验收证据。
> **审计方法**：4 路并行审计（资源/资产、ui/install/project/build、gradle/scripts、
> editor/apibrowser/engine + apibrowser/install 成员级二轮），全部结论经全仓 grep 实证
> （app/src main + test + build.gradle.kts + scripts/，排除 app/vendor 与 cpp/）。
> **用户拍板**（2026-08-12）：
> ① AGENTS.md 明文契约但零调用 API（isSameAs/replaceText/isKnownFile）→ 删除 + 文档同步；
> ② 文件树占位（Toast stub + 字符串 + 4 manager 包装 + 3 session 方法）→ 全部删除，功能落地时重建；
> ③ Shizuku/Dhizuku uninstallPackage（AIDL 权限能力）→ **保留**；
> ④ assets/lua 用户工具库 + LuaSocket 套件 → **保留**（用户脚本 require 面）；
> ⑤ testImplementation(:androlua) → 删除 + AGENTS.md R1 形态同步。
> **质量要求**：代码质量/架构达大厂验收标准；每项完成即验证（compile/test/lint/ktlint/门禁）。

## 审计基线（全量清单，2026-08-12）

### A. 死代码（主源码）

| # | 位置 | 内容 | 处置 |
|---|---|---|---|
| A1 | editor/EditorContent.kt:24 | `isSameAs()` | 删除 |
| A2 | editor/EditorController.kt:48 | `isBound()` | 删除 |
| A3 | editor/EditorController.kt:214 | `searchHasQuery()` | 删除 |
| A4 | editor/LanguageDetector.kt:316 | `isKnownFile()` | 删除 |
| A5 | editor/LuaFonts.kt:132 | `externalCount()` | 删除 |
| A6 | session/EditorSession.kt:185,202,232 | `replaceText`/`replaceAllText`/`reload` | 删除 |
| A7 | session/EditorSession.kt:301,311 | `removeTab`/`renameTab` | 删除 |
| A8 | project/ProjectManager.kt:306-330 | 文件树 4 包装 | 删除 |
| A9 | install/InstallerServices.kt:67 + root/RootServiceManager.kt:69 | `refreshAll`/`refreshStatus` | 删除 |
| A10 | ui/theme/ThemeCatalog.kt:105 | `resolveDark()` | 删除 |
| A11 | install/InstallerCoordinator.kt:36 | `resolveBackend()` | 删除 |
| A12 | editor/LuaDiagnostics.kt:27 | `checkSyntax()` | 删除 |
| A13 | build/AXmlManifest.kt:127,172,179 | `readPackageName`/`readLauncherActivity`/`readExported` | 删除 |
| A14 | EditorScreen.kt:442-447 + strings | 文件树占位菜单 + `menu_file_tree`/`file_tree_coming_soon` | 删除 |
| A15 | BuildOpts.kt:96-97 + SettingsManager + BuildApkDialog | `outputDir`/`outputNaming` 惰性配置链 | 删除 |
| A16 | app/src/main/kotlin/com/lioisme/lide/util/ | 空目录 | 删除 |

### B. 死依赖

| # | 内容 | 处置 |
|---|---|---|
| B1 | `dev.rikka.hidden:stub:4.4.0`（compileOnly） | 删除（AAR 字节实证零引用） |
| B2 | proguard-rules.pro:92 `-dontwarn dev.rikka.hidden.**` | 删除 |
| B3 | `testImplementation(project(":androlua"))` | 删除 + AGENTS.md 同步 |

### C. 死资源

| # | 内容 | 处置 |
|---|---|---|
| C1 | assets/lua/：bin.lua / bmob.lua / autotheme.lua / check.lua / test.lua（≈21KB） | 删除 |
| C2 | 9 工具库 + 10 LuaSocket 套件 | 保留（用户拍板） |
| C3 | assets/keys/ 与 test/resources/keys/ 字节重复 | 保留（测试必需） |

### D. 死脚本/垃圾/文档漂移

| # | 内容 | 处置 |
|---|---|---|
| D1 | scripts/build-apks.bat（孤儿薄包装） | 删除 |
| D2 | 根 _t3all.log（gitignored 陈旧日志） | 删除 |
| D3 | 根 build/（gradle 输出） | 可选清空 |
| D4 | scripts/adb_regress.ps1:85 陈旧 `com.lioisme.lide/.androlua.engine.EngineActivity` | 修正为 `com.lioisme.lide.engine.EngineActivity` |

### E. 已核查非死（保留）

- profileinstaller（baseline-prof.txt:67 消费）；verifyMinimalismDebug（AGENTS.md 文档化 dev 门禁）；
  x64/universal flavor；全部 res/textmate/theme/fonts/strings（461+461 键零死）；
  ZipUtil vs ZipUtils（不同职责）；ProjectEntryOps（MainActivity:178 + renameProject 使用）；
  uninstallPackage×2 + AIDL（用户拍板保留）；ThemeCatalog.options（ProjectInfoScreen 使用）。

## 任务清单

### 阶段 0 登记

- [x] **T0 文档登记**：backlog §2o 条目 + 本工作单建立（已完成 2026-08-12；backlog 8 条目 D1–D8，含文件树重建备忘）

### 阶段 1 死代码删除（A 系）

- [x] **T1 editor 包死代码**（A1–A5）：EditorContent.isSameAs / EditorController.isBound /
      searchHasQuery / LanguageDetector.isKnownFile / LuaFonts.externalCount
      （已完成 2026-08-12：5 函数 + KDoc 契约句同步删除；grep 全仓零残留——
      仅 EditorSettingsScreen 同名局部变量，无关）
- [x] **T2 EditorSession 死方法**（A6–A7）：replaceText / replaceAllText / reload /
      removeTab / renameTab + EditorSessionTest reload 用例同步
      （已完成 2026-08-12：5 方法 + KDoc 删除；EditorSessionTest「reload 先落盘脏内容再重读磁盘」
      用例删除；grep 零残留——仅 cpp/sqlite3.c 无关符号）

- [x] **T3 ProjectManager 文件树包装**（A8）：4 包装删除 + isValidEntryName 包装复查 +
      ProjectEntryOps 调用面收缩
      （已完成 2026-08-12：删 4 包装 + private rootDir；isValidEntryName 包装**保留**
      ——ProjectListScreen:555 重命名对话框真实消费；ProjectEntryOps 收缩至实际使用面
      ——createFile/createDirectory/renameEntry/deleteEntry/isProjectAnchor/targetName
      删除，保留 isValidEntryName/dedupeEntryName/isInsideRoot + 无用的 Log/TAG/import；
      ProjectEntryOpsTest 收缩至 3 组用例，文件树用例删除并注释重建路径；grep 零残留）
- [ ] **T4 install 死方法**（A9）：InstallerServices.refreshAll + RootServiceManager.refreshStatus
- [ ] **T4 install 死方法**（A9）：InstallerServices.refreshAll + RootServiceManager.refreshStatus
- [ ] **T5 测试死面清理**（A10–A13）：ThemeCatalog.resolveDark / InstallerCoordinator.resolveBackend /
      LuaDiagnostics.checkSyntax / AXmlManifest read*3 + 测试迁移（ThemeCatalogTest /
      InstallerBackendTest / LuaDiagnosticsTest / ApkBuilderTest）
- [ ] **T6 文件树占位删除**（A14 + A16）：菜单项 + 2 字符串（中英）+ util/ 空目录
- [ ] **T7 BuildOpts 惰性配置删除**（A15）：outputDir/outputNaming + SettingsManager 键 +
      BuildApkDialog 分支 + BuildPackagingTest 同步

### 阶段 2 死依赖删除（B 系）

- [ ] **T8 依赖删除**：dev.rikka.hidden:stub + proguard dontwarn + testImplementation(:androlua)

### 阶段 3 死资源删除（C 系）

- [ ] **T9 死 lua 资产删除**（C1）：bin/bmob/autotheme/check/test 5 文件

### 阶段 4 脚本与垃圾清理（D 系）

- [ ] **T10 脚本清理**：build-apks.bat + _t3all.log + adb_regress.ps1 路径修正

### 阶段 5 文档同步

- [ ] **T11 文档同步**：AGENTS.md（isSameAs/isKnownFile/replaceText 契约句、:androlua 依赖形态、
      文件树段落）+ docs/kb/EDITOR.md（reload/WP-005 段落）+ backlog 备忘（文件树重建、
      A15 删除）+ lint-baseline.xml 刷新（重跑 lint 生成）

### 阶段 6 全量验证

- [ ] **T12 全量验证**：compile + testArm64DebugUnitTest + lintArm64Debug + ktlintCheck +
      verifyMinimalismDebug + verifyVendorIsolation + verifySoraIsolation + assembleArm64Debug

### 阶段 7 收口

- [ ] **T13 收口**：结论蒸馏进 docs/kb/ + 删除本工作单
