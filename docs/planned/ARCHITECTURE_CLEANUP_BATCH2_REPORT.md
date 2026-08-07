# 架构审计第二轮实施批次（P1~P7）完成报告

> **状态**：（已完成 2026-08-07） · **日期**：2026-08-07
> **目标**：在 [ARCHITECTURE_CLEANUP_AUDIT_REPORT.md](ARCHITECTURE_CLEANUP_AUDIT_REPORT.md) Phase A~D 基础之上的
> 第二轮收口批次：签名链路现代化、TextMate 服务收敛、常量单一事实源、宿主真实缺陷、去重与死代码清理。
> **验收**：`:app:testArm64DebugUnitTest` 68 通过 + `:editor:testDebugUnitTest` 29 通过 +
> `:androlua:testDebugUnitTest` 48 通过（合计 145，0 失败）；`:app:assembleArm64Debug` BUILD SUCCESSFUL；
> R1 门禁零命中；无 .so/.dex/.zip/.jar（除 gradle-wrapper）入库。

---

## P1：ApkSigner 现代化 + V1 验签适配（（已完成）2026-08-07）

- ApkSigner 重写为 apksig 8.13.2 薄封装（`KeyConfig.Jca`），消除自研 RSA 私钥推导的弃用警告路径；
  删除 `V2Signer.kt` / `Base64Compat.kt` / `Base64CompatTest.kt`。
- **关键事实**：apksig 的 PKCS7 **无 authenticatedAttributes**（反编译 `ApkSigningBlockUtils.generatePkcs7DerEncodedMessage`
  确认），signerInfo 子项 tags=`[0x2, 0x30, 0x30, 0x30, 0x4]`；签名 = `SHA256withRSA` over 原始 SF 字节。
- `V1Verifier` 适配双形态：6 项含 `0xA0` → 旧自研（重建 SET OF Attribute DER + messageDigest=sha256(sf)）；
  5 项无 attrs → apksig（sigInput=SF 原文）。MANIFEST/SF 支持 72 字符折行续行（折叠文本 + 原始 section 双摘要）。
- 验收证据：`ApkSignerTest` / `ApkBuilderTest` 全绿；调试插桩与诊断测试清理完毕。

## P1b：ZipUtils 收敛（（已完成）2026-08-07）

- 重写 `ZipUtils`：删除 `CountingOutputStream` / zipalign 相关残留；新 API
  `listEntryNames` / `listAbis` / `listNativeLibEntries` / `listNativeLibNames` / `writeEntry`（4 参）；
  ApkBuilder / BuildApkScreen / NativeDependencyAnalyzer 全部适配。

## P2：TextMateRuntime 单一服务（（已完成）2026-08-07）

- 删除 `TextMateInitializer.kt`；`TextMateRuntime` 整体收编 `runInit`：
  `AsyncIncrementalAnalyzeManager.setUseShallowCopyByDefault(true)`、AssetsFileResolver、
  themeRegistry（quietlight/darcula）、`LanguageDefinitionReader.read(LANGUAGES_JSON)` + 批量 loadGrammars。
- 状态机补齐：`catch(Throwable)` → `RuntimeState.Failed`（此前初始化失败被吞、不可达）；
  `retry` 复位后重新 preload；`preload` 幂等（initStarted + synchronized + 单 daemon 线程 "TextMatePreload"）。
- 验收：`:editor:testDebugUnitTest` 全绿；R1 门禁零命中。

## P3：常量与边界词判定单一事实源（（已完成）2026-08-07）

- `"source.lua"` 收敛至 `LanguageDetector.LUA_SCOPE`（新增 const）；EditorLanguage（删除 companion LUA_SCOPE）、
  LanguageKeywordRegistry、LuaEditor（删除 `DEFAULT_SCOPE`）统一引用。
- 四处边界词判定收敛为 `Char.isIdentifierChar()`（LuaDiagnostics.kt 顶层 internal 扩展：
  `isLetterOrDigit() || '_'`），LuaDiagnostics 扫描器 / EditorLanguage 补全前缀 / EditorTextOps.toggleCase 共用。
- `EditorViewPool.createLanguage(scope, config, collectIdentifiers, editor)` 抽取，
  acquire 与 applyConfig 重建共用同一装配（重建路径 collectIdentifiers 显式传入，语义不变）。

## P3b：FileBrowser 双重振动修复（（已完成）2026-08-07）

- 删除 `FileBrowserRow` onLongClick 内手动 `performHapticFeedback(LongPress)`——
  `combinedClickable`/`detectTapGestures` 已自动触发长按 haptic，原实现振动两次；
  连带清理 LocalHapticFeedback / HapticFeedbackType 导入。EditorTabs 拖拽 haptic 刻意保留。

## P4：宿主真实缺陷修复（（已完成）2026-08-07）

- `MainActivity.resolveFilePath`：content:// 不再查已废弃的 `_data` 列，改经 `openInputStream`
  物化到 cacheDir 临时文件（`_display_name` 提供扩展名），SAF 文档可正确打开。
- `LideApp.bootstrapAssets`：`Thread{...}` 改为 `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 协程
  （launch 三项提取；scope 随 Application 生命周期，与 EditorSession 模式一致）。
- `LideNavHost` 导航参数编码：`URLEncoder/URLDecoder` → `Uri.encode/Url.decode`。
  **根因**：navigation-compose 内部经 `Uri.getPathSegments()/getQueryParameters()` 已做一次
  percent 解码，URLDecoder 的 `+`→空格语义会破坏含字面 `+` 的工程名/路径（如 `a+b` → `a b`）。
- `LocaleManager.wrap`：`Configuration.setLocale`（API 33 弃用）→ `setLocales(LocaleList)`（minSdk 24 即可用）。

## P4b：三处去重（（已完成）2026-08-07）

- init.lua 模板：`ProjectManager.initLuaTemplate(appName, packageName)` 单一事实源，
  `createProject` 与 `MainActivity.adoptStandaloneFile` 共用。
- 宿主权限表：`PermissionCatalog.hostDeclaredPermissions(context)` 唯一实现，
  EditorScreen（原内联 produceState 查询）与 PermissionScreen（原私有函数）共用。
- BackHandler：新增 `backGuarded(navController){}` 包装（系统返回键统一过 `popGuarded` 门控），
  消除 12 处逐目的地 `BackHandler(enabled=true){ popGuarded() }` 重复。

## P5：androlua 清理（（已完成）2026-08-07）

- `third_party/androlua/build.gradle.kts`：删除死声明 `implementation(fileTree("libs"))`
  （目录仅 .gitkeep）；删除无引用的 `androidx.lifecycle:lifecycle-common` / `androidx.core`
  （保留 androidx.annotation——vendor Java 在用）。
- 删除死代码 `AlpManager.import(uri: Uri)` 重载与 `resolveNameFromUri`——
  URI 物化已由 `AlpImportCoordinator.copyUriToTemp` 承担，全仓调用点均为临时文件路径。
- 删除 `scripts/make-examples.ps1`（已被 Gradle `:app:generateExamples` 取代，app/build.gradle.kts 注释自证），
  README / kb 三处引用同步更新。

## P5b：上游治理项落地（（已完成）2026-08-07）

- `AXmlManifest.readPool`：`flags & 0x400`（styled string pool）显式抛 `AXmlException`
  （此前静默解析出错乱字符串，审计 §4.5 记录项）。
- 新增 `EngineContract`（:androlua）：`EXTRA_PROJECT_DIR` / `EXTRA_MAIN_FILE` / `EXTRA_SCRIPT_ARGS` /
  `ENGINE_ACTIVITY` 单一事实源；`EngineBridge` 改消费之（删除原 companion 常量）；
  `EngineActivity.java` 读侧改走契约键；新增 `EngineContractTest` 固化两侧一致（防止跨模块字面量漂移回归）。
- 测试迁移：删除 `:app` 误置副本 `project/LuaConfigParserTest.kt`（用例并入 :androlua 权威测试，
  补 13 个迁移用例：parse/stringField null/array/注释/字符串/嵌套表/多行数组/字节级保真/空数组/裸标识符/函数调用）；
  `AlpNamingTest` / `ZipUtilTest` 由 :app 测试源 `git mv` 至 :androlua（被测类归属模块）。

## P6：全量验证（（已完成）2026-08-07）

- 编译：`:app:compileArm64DebugKotlin`、`:androlua:compileDebugKotlin`、`:editor:compileDebugKotlin` 全绿。
- 单测：`:app:testArm64DebugUnitTest` 68 通过 / `:editor:testDebugUnitTest` 29 通过 /
  `:androlua:testDebugUnitTest` 48 通过，**145 测试 0 失败**。
- 完整构建：`:app:assembleArm64Debug` BUILD SUCCESSFUL（含 externalNativeBuild ndk-build）。
- R1 门禁：`luajava|androlua|loadLibrary` 在 `editor/src` 零命中；
  `git ls-files '*.so' '*.dex' '*.zip'` 为空；`*.jar` 仅 gradle-wrapper.jar。

## P7：文档登记（（已完成）2026-08-07）

- 本报告登记 `docs/kb/README.md` §2.3 计划中矩阵；`docs/TASKS.md` 同步（见条目备注）。
- 遗留项（不在本单收口，另立任务）：C-1 navigation-compose 2.9.x 升级与 NavigationGate 拆除
  （需真机压测）；C-2 SearchBar 官方行为边界验证后拆除红线状态机；D-3 防御性 catch 清理
  （~59 处边界收敛，改动面广需逐点审计语义）；§4.4 的 MainActivity 产物模式 BuildConfig variant、
  指纹感知文件增删、文本哨兵 Result 化、测试钥随包分发、组合期副作用 Channel 化。
