# 任务单：编辑器全语言支持（38 种 TextMate 文法）

> **状态**：✅ 已完成（2026-08-06 立项；2026-08-06 落地并模拟器验证）
> **关联**：`docs/kb/EDITOR.md`、AGENTS.md R1~R5、`docs/planned/EDITOR_FEATURE_COMPLETION_WORK_ORDER.md`（F8 延续）
> **验收门禁**：§6（编译 / R1~R5 红线 / APK 资产 / 设备矩阵）

---

## 1. 执行摘要

- **目标**：编辑器从「仅 Lua 高亮」升级为全部 38 种 TextMate 文法支持（高亮 + 补全），
  修复 Lua 高亮回归，并把语言资产 / 目录 / 关键字表全部搬入 `:editor`（拆分 readiness）。
- **用户决策（2026-08-06）**：
  - D1 支持全部 38 种语言（不做子集）。
  - D2 资产全部进 `:editor`（`editor/src/main/assets/textmate/`），`:app` 不再持有语法资产。
  - D3 keyword completion 只做 sora 原生支持（`setCompleterKeywords`）+ Lua 片段优先，无专属 analyzer。
  - D4 不使用 treesitter（需 `.so`，与 R5 冲突）；0.24.6 供应商仅有 `editor` + `language-textmate`。
- **落地面**：
  - 新增 `LanguageCatalog` / `LanguageSpec` / `LanguageDetector` / `LanguageKeywordRegistry` / `EditorLanguage`（替换 `LuaLanguage`）。
  - `TextMateInitializer` 从 `LanguageCatalog` 全量注册文法；`LuaEditor` 按文件 scope 装配语言，
    Lua 专属能力（词法诊断 / 片段补全）仅对 `source.lua` 生效。
  - 引擎供应链声明：39 词动机将来地 2.47MB grammar → APK；linguistics 均为静态文本、可变。

---

## 2. 报告元信息

| 项 | 内容 |
|---|---|
| 报告日期 | 2026-08-06 |
| 审计对象 | `:editor`（`LuaEditor` / `TextMateInitializer` / 新语言目录类）、`:app` 接线（`EditorComponents` / `EditorScreen`）、资产布局 |
| 验收方法 | Kotlin 编译 + APK 资产清单核对 + 模拟器（x64 emulator-5554）像素级 token 颜色验证 |
| 核心红线 | AGENTS.md R1~R5、初始化顺序（colorScheme → setEditorLanguage → setText）、主线程契约、try/catch(Throwable) 降级、大文件降级（>200KB） |

---

## 3. 资产与目录（R3/R4：纯 `:editor` 能力）

- 资产根：`editor/src/main/assets/textmate/`（40 个 `languages/*` 目录 + `quietlight.json` / `darcula.json` + `languages.json`），共 81 个文件、约 2.48 MB。
- `LanguageCatalog.all`（39 条，与 `languages.json` 一一对应）：name → scopeName → grammar / languageConfiguration 相对路径。
- `LanguageDetector`：扩展名（含点前缀）→ scope 映射，精确文件名优先（`Makefile` / `Dockerfile` 等），约 280 条。
- `LanguageKeywordRegistry`：scope → 关键字数组（Lua 完整 + Java/Kotlin/JS/TS/Python/C/C++/C#/CSS/HTML/Shell/SQL/Go/Rust/PHP/Ruby/Dart/Zig），未登记语言仅启用标识符补全。
- `ApkBuilder.HOST_ASSET_EXCLUDED`（`assets/textmate/`）不受影响：宿主打包工程 APK 时仍排除内置资产，避免重复。

---

## 4. 实现变更

| 文件 | 变更 |
|---|---|
| `editor/.../LanguageCatalog.kt` | 新增：39 条语言目录（唯一入口） |
| `editor/.../LanguageSpec.kt` | 新增：name / scopeName / grammar / languageConfiguration |
| `editor/.../LanguageDetector.kt` | 新增：文件名 → scope 映射 |
| `editor/.../LanguageKeywordRegistry.kt` | 新增：静态关键字表（先声明 `LUA_KEYWORDS` 再建 registry，规避初始化顺序） |
| `editor/.../EditorLanguage.kt` | 新增：通用 TextMate 语言类（替换 `LuaLanguage`）；Lua 额外叠加 `LuaSnippetManager` 片段补全 |
| `editor/.../TextMateInitializer.kt` | `languages{}.build().single()` 全量注册 39 文法；任一文法失败仅记日志 |
| `app/proguard-rules.pro` | **新增**：Gson model 类（tm4e language-configuration / reader）+ Joni/JCodings 整体 keep，修复 release（R8）下 grammar 全部加载失败 |
| `editor/.../LuaEditor.kt` | `fileName: String?` 参数 → `LanguageDetector` 解析 scope（basename 规范化）；`acquire`/`applyConfig` 带 scope；词法诊断仅 `source.lua`；语言重建守卫 `lang.scopeName != resolvedScope` |
| `app/.../EditorComponents.kt` | `LuaEditor(fileName = selectedFile)` |
| `editor/.../EditorConfig.kt` | 文档注释更新（`EditorLanguage.configure`） |
| 删除 `editor/.../LuaLanguage.kt` | 由 `EditorLanguage.kt` 取代 |
| `app/src/main/assets/textmate/` | 删除（资产唯一归属 `:editor`） |

---

## 5. 验证记录

### 5.1 构建

- `.\gradlew.bat :editor:compileDebugKotlin`：✅ 通过（修复 `LUA_KEYWORDS` 初始化顺序 + DSL `.build().single()` 两处编译错误）。
- `.\gradlew.bat :app:compileArm64DebugKotlin`：✅ 通过。
- `.\gradlew.bat :app:assembleArm64Debug`：✅ 通过（NDK 29.0.14206865 ndk-build 正常）。

### 5.2 红线复核（AGENTS.md 验证命令）

- `rg "luajava|androlua|loadLibrary" editor/src`：零命中（R1 ✅）。
- `git ls-files '*.so' '*.jar' '*.dex' '*.zip'`：无新增二进制（R5 ✅）。

### 5.3 APK 资产清单

- `app-arm64-debug.apk` 内 `assets/textmate/` 共 **81 项**：39 个 `languages/*/syntaxes/*.tmLanguage.json` + 39 个 `language-configuration.json` + 2 主题 + `languages.json`（逐一核对全存在）。

### 5.4 设备验证（模拟器 emulator-5554，安装 arm64 debug APK）

- 启动 `TextMateInitializer: init total 1135ms`，无 "grammar load failed" 日志。
- 打开工程 `TestProj`，依次打开 `sample.py` / `sample.lua`（sample2.lua）/ `sample.kt`：
  截图像素统计显示**多 token 颜色**（暗色主题 darcula 调色板）：
  - Lua `sample2.lua`：`#CC7832`（keyword）、`#6CCDDF`（string/support）、`#FFC66D`（number）✅
  - Python `sample.py`：多色 token ✅
  - Kotlin `sample.kt`：`#CC7832` 等 4+ 色 ✅
  - 早前「Lua 单色」判定为截图裁剪区域偏差（裁剪区落在编辑器文本行之外），非回归。
- 说明：模拟器当前为深色主题，验证以 darcula 调色板为准；quietlight 主题切换路径（`LaunchedEffect(isDarkTheme, initReady)` → `ThemeRegistry.setTheme`）代码未动，保持原行为。

### 5.5 真机验证（CPH2415 arm64-v8a / SDK 35，安装 arm64 release APK）+ R8 修复

- **问题**：用户真机无任何语法高亮。根因 = **release 构建（`isMinifyEnabled = true`）下 R8 破坏 TextMate 运行时**：
  - `LanguageConfiguration`（language-config JSON 的 Gson model 类）被 R8 class-merging 改写为抽象类，
    Gson 报 `Abstract classes can't be instantiated! ... Class name: ea.h`（mapping 反查确认），
    39 个 grammar 在 `TextMateInitializer` 全部 `grammar load failed`；debug（未混淆）APK 正常，故模拟器验证未暴露。
  - 修复后残余：Joni 正则引擎 `org.joni.ast.QuantifierNode` `<clinit>` NPE / `NoClassDefFoundError`
    （go / json / latex / php 等含 pattern 的 language-config 触发）。
- **修复（`app/proguard-rules.pro` 追加）**：
  - `-keep class org.eclipse.tm4e.languageconfiguration.internal.model.** { *; }`（Gson model 类，防 class-merging + 字段名混淆）
  - `-keep class io.github.rosemoe.sora.langs.textmate.registry.reader.** { *; }`（`LanguageDefinitionList` 容器类）
  - `-keep class org.joni.** { *; }` + `-keep class org.jcodings.** { *; }`（Joni 正则引擎整体 keep）
  - 备注：曾尝试在 `language-textmate` 模块声明 `consumerProguardFiles`，AGP 8.13 Kotlin DSL 下该引用无法解析，
    故规则统一放宿主 `app/proguard-rules.pro`（与 luajava/androlua 同文件，release 构建已引用）。
- **验收**（真机 5fb6fdc9，`adb -s 5fb6fdc9`，重新构建安装 `app-arm64-release.apk`）：
  - `TextMateInitializer: init total 354ms`，**零** `grammar load failed`（修复前 39/39 全挂 → 修复后 0/39）。
  - 打开 demo 工程 `main.lua`：像素采样 `#CC7832`（keyword 378px）/ `#6A8759`（comment 215px）/ `#FFC66D`（number）✅
  - 打开 `__smoke.py`：`#CC7832` / `#6A8759` / `#FFC66D` / `#6897BB`（builtin）多色 ✅（多语言检测 + 高亮在 R8 混淆构建下正常）
  - mapping.txt 复核：`LanguageConfiguration` / `LanguageDefinitionReader` 类名未混淆（keep 生效）。

---

## 6. 验收门禁

| 门禁 | 结果 |
|---|---|
| 编译（editor + app arm64） | ✅ |
| R1（:editor 无 luajava/androlua/loadLibrary） | ✅ |
| R5（无二进制入库） | ✅ |
| APK 含全部 39 文法资产 | ✅ |
| 设备：Lua 高亮多色 | ✅ |
| 设备：Python / Kotlin 高亮多色 | ✅ |
| release（R8）构建：grammar 加载零失败 + 真机多色高亮 | ✅（§5.5） |
| 补全（Lua 片段 + 各语言关键字） | 逻辑走 `setCompleterKeywords`（sora 原生），设备交互验收推迟至后续综合复测 |

---

## 7. 遗留与后续

- 全语言补全的设备交互验收（关键字表弹出）与 39 语言逐一视觉巡检可并入后续综合设备复测（`EDITOR_FEATURE_COMPLETION_WORK_ORDER` §8.3 推迟项同批执行）。
- 本任务文档在设备交互验收闭环后 `git mv` 至 `docs/discard/`，并同步 `docs/kb/README.md` §2 / `docs/TASKS.md` / `docs/discard/README.md` §3。
