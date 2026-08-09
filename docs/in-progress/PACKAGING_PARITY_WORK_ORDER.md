# 打包对齐工作单：图标 / 加密范围 / 编译语义 / 运行权限提示（PACKAGING_PARITY_WORK_ORDER）

> **状态**：✅ 代码与门禁全部完成（2026-08-09，含 P9 图标红线修订）；ACC-4 设备验收待做（打包产物侧已有端到端字节级断言）
> **关联**：AGENTS.md「打包构建约定」「ABI 架构契约」「运行时基座契约」；docs/kb/WHITEPAPER.md §打包双开关
> **硬性约束**：改动打包/签名/加密链必须跑 `:app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"`；打包门禁 `verifyMinimalism` / `verifyMinimalismDebug`

---

## 背景与依据

用户三项需求 + 一项运行体验问题，均以 AndroLua Pro 上游基线（本仓 `assets/lua/bin.lua` 398 行 + `console.lua`，与 pro 原版一致）为对齐基准：

1. **打包产物支持显示图标**（pro `bin.lua:252-258`：工程根 `icon.png` → 覆盖模板 `res/drawable/icon.png`，资源 ID 不变，桌面图标即工程图标；无 icon.png 时显示宿主默认图标）。
2. **加密仅限 .lua/.aly**（现状 `ApkBuilder.encodeRel` 对全部工程文件加密，图片资源损坏——用户实测复现；pro 仅对 .lua/.aly 走编译管线、其余原样拷贝）。
3. **编译 = Lua 源码 → 字节码，对齐 pro**（pro `console.lua` `build`/`build_aly`：全部 .lua 编译同名入库；.aly 包装 `local layout=%s\nreturn layout` 后编译并**改名 .lua** 入库；lide 现状 .lua 已对齐、.aly 未编译）。
4. **运行权限拒绝提示移除**：EditorScreen 运行权限门回调弹 Toast「已拒绝 x 项权限，继续运行」，要求静默继续运行（pro 行为为不阻断，但用户要求连提示都不显示）。

审计结论详见：本次审计的完整差异矩阵与逐文件定位（图标缺宿主资源 + 打包器注入；加密范围缺陷 + alyloader 魔数分支缺陷；.aly 编译缺失；dump strip 差异低危）。

---

## 任务清单

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| P1 | 移除运行权限拒绝 Toast 提示（EditorScreen.kt 回调 + 删除 `run_permission_denied_hint` 双语字符串） | ✅（已完成） | 2026-08-09：EditorScreen.kt:207-221 回调改为静默 `doRun()`（拒绝不阻断）；注释同步；双语字符串资源删除、引用清零。验收：grep 零命中。 |
| P2 | 加密范围修复：`ApkBuilder.encodeRel` 仅对 `.lua`/`.aly` 加密，其余文件原样 | ✅（已完成） | 2026-08-09：encodeRel 重构为返回 `Pair<入库相对路径, ByteArray>`，加密/编译判定共用一个扩展名谓词（`.lua`/`.aly`，ignoreCase，单一事实源）；图片/字体/数据等一律原样入库。验收：单测「非 Lua 文件不加密」+ 端到端真实产物断言（assets/lua/pic.png 原样 8 字节）。 |
| P3 | `loadlayout.lua` alyloader 魔数分支修复：识别 `LIDE\x01\0\0\0` 加密魔数 → 走 `loadfile`（C 端解密），仅纯文本走 loadstring | ✅（已完成） | 2026-08-09：loadlayout.lua alyloader 新增 LIDE 魔数分支（`string.sub(s,1,4)=="LIDE"` → `return assert(loadfile(path)), path`），\27Lua 分支与文本分支保留。验收：端到端 .aly 改名 layout.lua 解密断言通过。 |
| P4 | `.aly` 编译对齐 pro：compileLua 开时包装 `local layout=…\nreturn layout` → 编译字节码 → 改名 `.lua` 入库（pro `gsub("aly$","lua")` 同款） | ✅（已完成） | 2026-08-09：三态包装策略——编译开：包装文本 → LuaCompiler 编译字节码 → `dropLast(4)+".lua"` 改名入库（曾漏 dropLast 丢点，已修）；编译关+加密开：包装文本加密入库（C 端 `luaL_loadfilex` 解密后 `loadbufferx` 无 return 前缀能力，裸表文本会语法错误）；编译关+加密关：裸表文本原样保留 .aly 名（alyloader 文本分支加 `return ` 加载）。验收：真实 luajava 编译字节码头 \x1BLua + 解密回环断言通过。 |
| P5 | 图标支持：①宿主新增 `res/drawable/icon.png` 默认图标 + manifest `android:icon="@drawable/icon"`；②`ApkBuilder` 装配工程 `icon.png` → 覆盖产物 `res/drawable/icon.png`（原样、不编译不加密） | ✅（已完成） | 2026-08-09：宿主 icon.png（96x96 蓝底白 L，397B，PS System.Drawing 生成）+ manifest application 加 `android:icon`；ApkBuilder `projectIcon`（key equals "icon.png" ignoreCase）装配时覆盖产物 `res/drawable/icon.png` 条目数据。关键发现：aapt2 36 的 arsc 不内嵌 PNG——drawable/icon Res_value 为 TYPE_STRING（data=372）引用全局字符串池 "res/drawable/icon.png"，即 arsc 引用 zip 文件路径，覆盖条目数据即生效、arsc/manifest 零改动；assets/lua/icon.png 由工程注入循环自动写入（双份语义同 pro）。验收：端到端真实产物 manifest 含 @drawable/icon + 图标覆盖断言通过。 |
| P6 | 单测：ApkBuilderTest 新增图片原样 / .aly 加密+改名 / 图标注入覆盖用例；编译 + build 单测 + verifyMinimalismDebug | ✅（已完成） | 2026-08-09：syntheticHostApk 增加 `res/drawable/icon.png`(stored)；新增 7 用例（非 Lua 不加密 / .aly 编译改名+字节码头 / 双关原样 / 编译关加密开包装文本可解密为 `local layout=…\nreturn layout` / .aly 加密改名带 LIDE 魔数 / 工程 icon 覆盖+双份 / 无 icon 保持宿主）。另建一次性端到端验证（真实宿主 app-arm64-debug.apk → ApkBuilder 真实编译：manifest 图标保留、图片不加密、.aly 改名加密、图标覆盖、main.lua 解密字节码头），跑完已删（不入库）。验收：build.* 全量 83 tests 全绿；verifyMinimalismDebug 通过。 |
| P9 | 用户红线修订：**LIDE 本身暂时无图标**；打包产物图标与 pro 一致（工程 icon.png 覆盖生效，无则模板默认图标）。实测「自定义 icon.png 仍显示 LIDE 图标」根因：宿主 manifest 带 icon 时产物继承宿主图标 + 设备/宿主版本陈旧或 Launcher 缓存。改法：①宿主 manifest 移除 `android:icon`（自身显示系统默认）；②宿主保留 `res/drawable/icon.png` 作模板默认图标资源（自身不引用不显示）；③新增 `ArscLookup` 解析宿主 arsc（ResTable_header 12B 起、package typeStrings/keyStrings 相对偏移 @268/@276、type chunk entry 紧凑布局按 esize 前进）定位 drawable/icon 资源 ID；④`ManifestEdit.applicationIconId` + `rebuildApplication` 注入 `android:icon`（TYPE_REFERENCE，模板已有 icon 属性则原样保留不重复注入）；⑤arsc 缺失/解析失败安全降级不注入 | ✅（已完成） | 2026-08-09：ApkBuilder 装配传入 `ArscLookup.drawableIconId(宿主 arsc)`；工程 icon.png 覆盖产物条目逻辑不变（pro bin.lua 同款）。验收：AXmlManifestTest 新增注入用例 + 模板已有 icon 不重复注入用例；ApkBuilderTest 无回归（合成宿主伪 arsc 走降级）；端到端（真实宿主）：宿主 manifest 无 icon ✓、arsc 解析 ID 高 24 位 0x7f ✓、产物恰一个 icon 属性（TYPE_REFERENCE = 真实 ID）✓、无工程 icon 时模板默认图标入产物（PNG 签名）✓、工程 icon 覆盖 + 双份 ✓、垃圾 arsc 降级 null ✓（临时测试类跑完已删）；build.* 全量单测 + verifyMinimalismDebug 全绿。 |

| P10 | 用户红线反转（P9 修订）：**LIDE 宿主自身使用矢量设计图标**；产物图标机制不变（模板自带 icon 原样保留 → 工程 icon.png 覆盖条目数据） | ✅（已完成） | 2026-08-09：宿主 manifest 重新声明 `android:icon="@drawable/icon"`（`res/drawable/icon.png` 由用户矢量 SVG 渲染 512×512 PNG，rsvg-convert + lanczos，内容居中透明方底）；AXmlManifest/ApkBuilder 注释、AGENTS.md 打包图标段、WHITEPAPER 两处同步为「模板自带 icon 原样保留 + ArscLookup 仅对无 icon 模板兜底」。验收：build.* 全量单测 + verifyMinimalism 全绿；AXmlManifestTest 双路径（注入 / 模板已有不重复注入）无回归。 |

## 实现要点（已核验，防回归）

- **AXmlManifest 图标属性（P9/P10）**：模板自带 `android:icon`（宿主自带的 @drawable/icon）时 attrBytes 原样保留、不重复注入（防双属性，AXmlManifestTest 断言）；模板无 icon 属性时才经 `applicationIconId` 注入 `android:icon`（TYPE_REFERENCE 资源引用，`ArscLookup` 解析宿主 arsc 的真实 ID，不写死）。
- **HostPolicy 零改动**：`res/` 树归 `RUNTIME_CORE` 原样保留（ApkBuilderTest 已断言）。
- **.aly 改名冲突**：改名后与工程既有 `.lua` 同名时按现有 `seen` 语义（先写者胜）；.aly 编译失败抛 `BuildException`（与 .lua 编译失败同口径）。
- **icon.png 双份语义**（pro 同款）：工程 icon.png 既入 `assets/lua/icon.png`（工程文件原样注入）又覆盖 `res/drawable/icon.png`（桌面图标）。
- **运行端 C 解密**（lauxlib.c `luaL_loadfilex` 魔数探测）已覆盖 .lua 与 .aly 改名后的 .lua 字节码；alyloader 文本分支为唯一绕开点（P3 修复）。
- **加密判定与编译判定共用一个扩展名谓词**：`.lua`/`.aly`（ignoreCase），单一事实源，杜绝再次漂移。

## 验收标准

- ACC-1：`.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` 全绿（含新增用例）。✅ 2026-08-09：83 tests 全绿。
- ACC-2：`.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 零错误。✅ 2026-08-09：零错误。
- ACC-3：`.\gradlew.bat :app:verifyMinimalismDebug` 通过（图标资源不触发任何精简断言）。✅ 2026-08-09：通过（引擎面 35 类唯一存在）。
- ACC-4：真机/模拟器：带 icon.png 工程打包安装 → 桌面图标 = 工程图标；「编译+加密」打包 → 图片资源正常、main.lua/layout.aly 正常运行；运行工程无权限拒绝提示。🔄 待设备验收（端到端字节级断言已覆盖打包产物侧）。
- ACC-5：文档治理：任务逐项标记「（已完成）」+ 备注工作内容/日期/验收证据；完成后蒸馏进 docs/kb。⬜ 蒸馏待 P9 收尾。
