# 任务工作单：打包构建三大能力 — 签名管理 / Lua 编译加密 / 原生库按需通用化

> 状态：🚧 进行中 · 创建 2026-08-07
> 目标：① 设备端打包支持开发者自有签名（档案管理）；② 打包期 Lua 编译范围可选 + 字节码 DES-ECB 加密，运行时自动解密；③「仅携带依赖的原生库」升至通用持久化选项（默认启用）。
> 追加（2026-08-07 用户新规格）：编译/加密改为**独立开关**（默认均开、互不隐含）；签名支持**自定义导入 / 应用内自生成 / 默认内置**三类。
> 验收标准：单测全绿 + `assembleArm64Debug` 绿 + R1 门禁零命中 + 模拟器端到端（自签名安装 / 加密产物运行）。

---

## 1. 现状审计结论（事实核验）

| # | 能力 | 现状代码事实 | 缺口 |
|---|---|---|---|
| 1 | 签名 | `BuildApkScreen.kt:433-442` 硬编码读 `assets/keys/testkey.pk8` + `.x509.pem`；`ApkSigner` 已是官方 apksig（v1+v2+v3，`setMinSdkVersion(24)`），接口收 `PrivateKey+X509Certificate` | 无 keystore 导入、无别名/密码、无档案管理、无持久化 |
| 2 | Lua 编译 | `LuaCompiler`（third_party/androlua，引擎同源 `LloadBuffer`+`dump`）仅编译 `main.lua`，其余 `.lua`/`.aly` 明文注入（`ApkBuilder.kt:226-235`） | 无全量编译、无加密选项、运行时无自动解密 |
| 2' | 加密 API | 引擎 `libcrypt.so` 含 DES-ECB（内置 DK 8 字节密钥）`desencode/decode`、`desdump/desload`；`md5` 模块 | **无打包期加密**；`require` 走 C 侧 `luaL_loadfilex`，Java 拦不住（六处 `L.LloadFile` 仅覆盖 Java 直调） |
| 3 | 原生库按需 | 机制已落地（`ApkBuilder.nativeLibKept` + ABI×依赖二维过滤 + `NativeDependencyAnalyzer` 传递闭包；95 单测绿） | 仅打包页高级卡临时开关（`BuildState.stripNativeLibs=true` 每次进页重建），**未持久化、未入统一设置** |

**架构约束（R1~R5）**：全部实现位于 `:app` 宿主域与引擎 C 源码；`:editor` 零改动（不触碰 R1/R2）。

## 2. 设计决策（用户已确认）

- **签名管理**：打包页签名选择 + 独立 `SigningScreen` 管理页（导入 .jks/.p12、列别名、看指纹、设默认）；默认档案 `debug-testkey`（assets 材料，零迁移）。
- **Lua 加密**：DES-ECB（固定内置密钥 = pro crypt 默认 DK `{0xff,0xdd,0xbb,0x99,0x77,0x55,0x33,0x11}`）+ PKCS7 对齐，自定义 8 字节魔数 `LIDE\x01\x00\x00\x00`；**C 引擎 loader 单点解密**（`luaL_loadfilex` 快路径）——同时覆盖 Java 直调 main 与 Lua `require` 两条加载链。定位：防呆/防误读，非对抗安全。
- **通用化**：签名/加密/编译范围/原生库/默认 ABI 全部持久化入 `SettingsManager` 构建设置域，打包页读默认值、勾选即写回。

---

## 3. 实施任务（T1~T5）

| # | 任务 | 涉及文件 | 验收证据 | 状态 |
|---|---|---|---|---|
| T1 | 签名管理 | `build/Signing{Models,Manager}.kt`、`build/SigningCrypto.kt`、`ui/screen/SigningScreen.kt`、`BuildApkScreen.kt`(签名卡+接入)、`LideNavHost.kt`、strings(res/values{,-zh}) | 单测 + 编译通过；构建用导入档案 | ✅（已完成） |
| T2 | Lua 编译加密 | `build/LuaCrypto.kt`、`app/src/main/cpp/lua/lide_des.{c,h}`(+`Android.mk`)、`lauxlib.c`（`luaL_loadfilex` 快路径）、`ApkBuilder.kt`（compileAll/encrypt 管线）、`BuildApkScreen.kt`（选项） | `LuaCryptoTest` 7/7 绿+交叉 KAT；ASm two ABIs NDK 构建通过 | ✅（已完成） |
| T3 | 打包选项持久化 | `SettingsManager.kt`（build 域）、`ui/screen/BuildOptionsScreen.kt`、`Routes.BUILD_OPTIONS/SIGNING`、`SettingsScreen.kt`(通用分区)、`BuildApkScreen.kt`(读/写) | 编译通过；route + 设置入口可用 | ✅（已完成） |
| T4 | 文档同步 | `WHITEPAPER.md §5.5/阶段五`、`AGENTS.md`（打包签名/加密约定） | 链接与矩阵核对 | ✅（已完成） |
| T5 | 全量验证 | 单测 + assemble + R1 门禁 + ktlint + 模拟器 | 单测全绿、双 ABI NDK + assemble 通过、R1 门禁通过；模拟器 loopback 已验证（DES 加密 main 运行输出 `lua: DES-LOOPBACK-OK`） | ✅（已完成） |

> 完成记录（2026-08-07）：
> - T1：`SigningManager`(`loadMaterial`/`profile`)、`SigningScreen`（内置/导入双模式）、打包页签名卡 + `Routes.SIGNING`，随 `:app:compileArm64DebugKotlin` / `:app:assembleArm64Debug` 全绿。
> - T2：打包端 `LuaCrypto.kt`（MAGIC `LIDE\x01\0\0\0` + DES/ECB/NoPadding + 手工 PKCS7）；运行端 `lide_des.c` 解码钩入 `luaL_loadfilex`（非 MAGIC 走原路径零回归）；`ApkBuilder` 经 `compileAllLua`/`encryptBytecode` 注入。验证：`LuaCryptoTest` 7/7（含 C→Java 交叉已知答案 `01 02 03 04 05 06 07 00` → `DA 39 94 7C 37 F7 84 63 A2 A8 37 91 27 0E 91 CB`）；gcc 宿主 FIPS 向量 KAT 全过；`externalNativeBuildArm64/X64Debug` BUILD SUCCESSFUL。
> - T3：`SettingsManager` build 域（`lide_build` prefs + `StateFlow<BuildOpts>`），`BuildOptionsScreen`（编译范围/加密/ABI/默认签名），设置页 Build 入口，`LideApp.onCreate` 注入 `SigningManager.init`。
> - T5：单元 `com.lioisme.lide.build.*` 全绿；R1 门禁通过（editor/src 无 luajava/androlua/loadLibrary；git ls-files 无 .so/.dex/.zip/非引导 .jar）；模拟器 loopback 通过（2026-08-07）：x64 安装 → 推入 `desloopback` 工程（main.lua 由 `LuaCrypto.encrypt` 加密为 128B 二进制，魔数 `4C 49 44 45 01 00 00 00`）→ `adb root` 启动 `com.lioisme.lide/com.androlua.LuaActivity`(VIEW file) → 原生 `luaL_loadfilex`→`lide_des` 解密 → logcat `lua: DES-LOOPBACK-OK`，0 崩溃行；证据 `%TEMP%\opencode\des_loopback.log/.png`。
> - 追加规格（2026-08-07，用户新需求）：① 编译/加密独立开关：`BuildOpts.compileLua/encryptBytecode`（默认均 true），`SettingsManager` 键改为 `compile_lua`/`encrypt_lua`（旧键不再读写，升级落新默认）；`ApkBuilder.encodeRel` 独立两态管线（编译开→字节码、关→源码文本；加密开→DES 加密、关→原文）；`BuildOptionsScreen`/`BuildApkScreen` 去掉「仅 main/全部」作用域单选与加密强开编译联动。② 自生成签名：`SigningCrypto.generate` 零依赖手写 X.509 v3 自签证书 DER（RSA-2048 + PKCS12；version 必须 `[0] EXPLICIT`(A0) 包装，裸 INTEGER 会被 OpenJDK 误读为 serial → `algid parse error`），`SigningManager.generate`（密码 <6 位自动 12 位随机），`SigningScreen`「生成新签名」对话框。③ 验证：`SigningCryptoTest` 6/6（PKCS12 回环/平台解析自校验/v1 签名校验）、`ApkBuilderTest` 15/15（编译开全字节码/编译关全明文/加密独立于编译两组 KAT/覆盖断言补 `compileLua=false,encryptBytecode=false`）、`LuaCryptoTest` 7/7；build.* 共 43 项全绿，`assembleArm64Debug` BUILD SUCCESSFUL。
> - T4（2026-08-07）：`docs/WHITEPAPER.md §5.5`（签名子系统：三类档案 + apksig + 打包双开关）与「阶段五」更新；`AGENTS.md` 新增「打包构建约定」节（双开关独立性/偏好键/自签 version EXPLICIT 陷阱/加密格式唯一事实源/改动必跑 build.* 单测）；`docs/kb/README.md §2` 与 `docs/TASKS.md` 矩阵同步。
> - R8 签名缺陷修复（2026-08-07）：**症状**——release 宿主（`isMinifyEnabled=true`）打包必败 `构建失败：Failed to sign using signer "CERT"`，debug 宿主正常；**根因**——apksig 的 ASN.1 编码器（`Asn1DerEncoder`）对模型类做注解驱动反射（`@Asn1Field`/`@Asn1Choice` + `getDeclaredFields`），R8 混淆裁剪删改模型类字段后反射空转抛 `Asn1EncodingException`，被 `V1SchemeSigner` **无 cause 包装**成 `Failed to encode signature block`（定位经 javap 反编译 `apksig-8.13.2.jar` + 反混淆栈 `p5.c.f→p5.c.e` + debug/release 宿主 A/B 判别）；**修复**——`app/proguard-rules.pro` 追加 `-keep class com.android.apksig.** { *; }`；**验收证据**——`:app:assembleUniversalRelease` BUILD SUCCESSFUL → x64 模拟器安装 universal-release 宿主 → 打包 E2EProj `done res=Success`，产物 `com.lioisme.lide.E2EProj-1.0.apk`（16,077,025 B）经 `apksigner verify` v2+v3 Verified（AOSP testkey）。

## 4. 加密格式（LuaProtection / C 解码契约 - 唯一事实源)

```
文件 = MAGIC[8] || DES-ECB(NoPadding)( 字节码 + PKCS#7 填充 )
MAGIC   = 0x4C 0x49 0x44 0x45 0x01 0x00 0x00 0x00  ("LIDE\x01\0\0\0")
KEY     = {0xFF,0xDD,0xBB,0x99,0x77,0x55,0x33,0x11}   (pro crypt DK 低字节序)
padd    = 8 - (len % 8)；pad 字节 = 每字节 padd；len%8==0 → padd=8
解密    : 校验 MAGIC → 逐 8 字节 DES 解块（reverse key schedule）→ 末字节=padLen 校验
```

- 打包端（Kotlin `LuaProtection.encrypt`）用 `javax.crypto.Cipher("DES/ECB/NoPadding")` 手工 PKCS7 补齐；
- 运行端（C `lide_des_crypt`）使用 lcrypt 同款 DES 实现（SB1-8 + PC1 查表 + IP/FP/Round 宏），密钥排程翻转即解密（与 pro `crypt.desdecode` 同构）。

## 5. 边界与风险

- 字节码必须同源引擎编译（`LuaCompiler`），与 `PITFALLS.md §8` 一致；
- 加密仅覆盖**工程 .lua**（`collectProjectFiles` 产物）；宿主引擎 Lua 库（`import.lua` 等）与 `.aly`/资源不加密（保持 `libsloader/package.path` 兼容）；
- 解密追求**无感知**：非 MAGIC 文件走原 `luaL_loadfilex` 全路径（零行为回归）；
- 产物内密钥可提取——文档/UI 明示防呆定位；
- 签名密码明文存私有 prefs（与 pro 一致），Android Keystore 加密放入计划 backlog。

## 6. 文档登记与归档

- 新建本文档并登记 `docs/kb/README.md §2.3`、`docs/TASKS.md`「进行中」；
- 每完成一项在 §3 标「（已完成）」+ 工作内容/日期/验收证据；
- 全部完成后 `git mv docs/discard/`，同步更新 `kb/README.md §2`、`TASKS.md`、`docs/discard/README.md §3`。