# Delua 项目整改工作清单

| 元数据 | 内容 |
|--------|------|
| 文档版本 | 1.0 |
| 创建日期 | 2026-07-31 |
| 项目名称 | Delua |
| 项目代号 | delua |
| 目标包名 | `com.lioisme.delua` |
| 工作目录 | `/storage/emulated/0/AndroLua` |
| 引擎基础 | AndroLua Pro (nirenr) — Lua 5.3.3 |
| 文档状态 | **草案** |

---

## 目录

1. [执行摘要](#1-执行摘要)
2. [阶段划分与依赖关系图](#2-阶段划分与依赖关系图)
3. [工作包详细定义](#3-工作包详细定义)
   - [阶段一：基础整改（P0 阻塞级）](#阶段一基础整改p0-阻塞级)
     - [WP-001 修复原生库构建系统](#wp-001-修复原生库构建系统)
     - [WP-002 重命名包名为 com.lioisme.delua](#wp-002-重命名包名为-comlioismedelua)
   - [阶段二：功能清理（P1 高优先级）](#阶段二功能清理p1-高优先级)
     - [WP-003 移除内置样例工程](#wp-003-移除内置样例工程)
     - [WP-004 修复构建脚本错误处理](#wp-004-修复构建脚本错误处理)
   - [阶段三：配置规范化（P2 中优先级）](#阶段三配置规范化p2-中优先级)
     - [WP-005 清理 ProGuard 规则](#wp-005-清理-proguard-规则)
     - [WP-006 清理不必要的权限声明](#wp-006-清理不必要的权限声明)
   - [阶段四：架构加固（P3 低优先级）](#阶段四架构加固p3-低优先级)
     - [WP-007 EngineBridge 错误处理与容错](#wp-007-enginebridge-错误处理与容错)
     - [WP-008 解耦 Java 运行时模块](#wp-008-解耦-java-运行时模块)
   - [阶段五：工程维护（P4 优化级）](#阶段五工程维护p4-优化级)
     - [WP-009 补齐本地化字符串](#wp-009-补齐本地化字符串)
     - [WP-010 CI 集成自动原生引擎构建](#wp-010-ci-集成自动原生引擎构建)
4. [验收总则](#4-验收总则)
5. [回滚与应急方案](#5-回滚与应急方案)
6. [风险管理](#6-风险管理)

---

## 1. 执行摘要

本文档定义了 Delua 项目从当前状态过渡到完全合规状态所需的所有工作包。共计 **10 个工作包**，分为 **5 个阶段**，按依赖关系和严重程度依次执行。

当前阶段目标：消除 P0（阻塞级）缺陷，使项目达到**可发布基线**。

---

## 2. 阶段划分与依赖关系图

```
阶段一：基础整改 (P0 阻塞)
  │
  ├── WP-001 修复原生库构建系统 ────────────── 无前置依赖
  │         │ 验证：13 个 .so 均正确输出
  │
  └── WP-002 重命名包名为 com.lioisme.delua ── 无前置依赖
            │ 验证：apk 安装后包名正确
            │
            ▼
阶段二：功能清理 (P1 高)
  │
  ├── WP-003 移除内置样例工程 ──────────────── 依赖 WP-002（包路径）
  │         │ 验证：首次启动无样例文件产生
  │
  └── WP-004 修复构建脚本错误处理 ──────────── 依赖 WP-001（构建脚本）
            │ 验证：模块编译失败时输出明确诊断
            │
            ▼
阶段三：配置规范化 (P2 中)
  │
  ├── WP-005 清理 ProGuard 规则 ────────────── 依赖 WP-002（类路径）
  │
  └── WP-006 清理不必要的权限声明 ──────────── 依赖 WP-002（Manifest）
            │
            ▼
阶段四：架构加固 (P3 低)
  │
  ├── WP-007 EngineBridge 错误处理 ─────────── 无前置依赖
  │
  └── WP-008 解耦 Java 运行时模块 ──────────── 无前置依赖
            │
            ▼
阶段五：工程维护 (P4 优化)
  │
  ├── WP-009 补齐本地化字符串 ──────────────── 无前置依赖
  │
  └── WP-010 CI 集成自动引擎构建 ───────────── 依赖 WP-001（构建脚本）
```

---

## 3. 工作包详细定义

---

### 阶段一：基础整改（P0 阻塞级）

---

#### WP-001 修复原生库构建系统

| 属性 | 值 |
|------|-----|
| **标识符** | WP-001 |
| **标题** | 修复原生库构建系统 |
| **严重程度** | **P0 — 阻塞级** |
| **前置依赖** | 无 |
| **预估工时** | 3–5 人日 |
| **风险等级** | 高 |

##### 问题描述

当前 `rebuild_engine.ps1` 脚本运行 `ndk-build` 时，15 个已注册的 NDK 模块中仅有 2 个（`libluajava.so`、`libgl.so`）成功编译并输出。其余 13 个模块因以下原因静默失败：

1. `cjson`、`md5`、`crypt`、`zip`、`xml`、`regex`、`zlib`、`canvas`、`bson`、`luv`、`socket`、`mime`、`sensor` 的 `Android.mk` 中声明 `LOCAL_STATIC_LIBRARIES := luajava`，但 `libluajava.so` 是**共享库**而非静态库，导致链接失败。
2. 构建脚本使用 `Start-Process -Wait -PassThru`，**未捕获 stderr 输出**，编译错误被丢弃。
3. `luv` 模块内嵌其自身完整源码且使用了旧版 API，在 NDK r26+ 上可能无法编译。
4. `zip`（libzip）体量过大（~90 .c 文件），增加了编译失败的概率。
5. `sensor`（luasensor.cpp）使用 C++，需要确认 `APP_STL := c++_static` 正确传递。

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 对三种 ABI（arm64-v8a、armeabi-v7a、x86_64）分别运行 `rebuild_engine.ps1`，每种 ABI 至少输出 14 个 `.so` 文件（含 `libluajava.so`、`libgl.so`） | `Get-ChildItem prebuilt/*/ -Filter *.so \| Measure-Object` 计数 ≥ 14 |
| ACC-002 | 每个 `.so` 文件可通过 `readelf -d` 验证为非 stub 的有效共享库 | `readelf -d libcjson.so` 返回有效动态段 |
| ACC-003 | 编译过程无 error 级别输出；允许 warning 但必须记录日志 | 日志文件不存在 `error:` 或 `fatal` 关键词 |
| ACC-004 | 构建产物总大小增量不超过 6 MB（每个模块约 100–600 KB × 13 × 3 ABI） | 对比 `prebuilt/` 目录前后大小 |
| ACC-005 | 任一模块编译失败时，构建脚本报错并明确指出失败的模块名和错误行 | 脚本输出包含 `FAILED: <module>`

##### 工作量分解

| 步骤 | 描述 | 验证条件 |
|------|------|---------|
| 1.1 | 修改所有受影响 `Android.mk`：`LOCAL_STATIC_LIBRARIES := luajava` → `LOCAL_SHARED_LIBRARIES := luajava` | `grep -r "LOCAL_STATIC_LIBRARIES.*luajava" jni/` 无匹配 |
| 1.2 | 向 `build-scripts/` 添加 `check_ndk_build.ps1` 诊断脚本，逐模块编译并捕获输出 | 脚本运行成功 |
| 1.3 | 重写 `rebuild_engine.ps1` 第 84 行，将 `Start-Process` 替换为直接进程调用并捕获 stdout/stderr | 编译日志保存至 `$BUILD_DIR/build.log` |
| 1.4 | 逐模块编译定位修复：`luv`（如有必要）、`zip`、`sensor` | 每个模块独立编译通过 |
| 1.5 | 全量编译并验证所有 ABI 产物 | 符合 ACC-001 ~ ACC-005 |
| 1.6 | 将新的 `.so` 文件提交至 `prebuilt/` 和 `jniLibs/` | `git status` 确认新文件跟踪 |

##### 失败回滚

若修复过程中引入新的编译失败，应从 `git checkout -- prebuilt/` 恢复已知良好的 `.so` 文件，并记录失败原因。

---

#### WP-002 重命名包名为 com.lioisme.delua

| 属性 | 值 |
|------|-----|
| **标识符** | WP-002 |
| **标题** | 重命名包名为 `com.lioisme.delua` |
| **严重程度** | **P0 — 阻塞级** |
| **前置依赖** | 无 |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

当前所有 Kotlin 和 Java 源文件使用 `com.delua` 包名，与要求的 `com.lioisme.delua` 不符。本任务将全局重命名包名。

##### 范围

需要修改的文件类型：

| 文件类型 | 变更内容 | 数量 |
|---------|---------|------|
| `app/build.gradle.kts` | `namespace`、`applicationId` | 2 处 |
| `androlua/build.gradle.kts` | `namespace` | 1 处 |
| Kotlin 源文件 | `package com.delua.*` → `package com.lioisme.delua.*` | ~35 个文件 |
| Java 源文件（androlua 模块） | `package` 声明（注意：`com.luajava`、`com.androlua`、`com.android.cglib` 保持不动） | 0 个（注意范围） |
| AndroidManifest.xml | `android:name` 中的类全限定名 | 2 个文件 |
| `ProjectManager.kt` | 默认包名字符串 | 1 处 |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 构建生成的 APK 的 `applicationId` 为 `com.lioisme.delua` | `aapt dump badging app/build/outputs/apk/*.apk \| grep package:` |
| ACC-002 | 所有源文件无 `com.delua` 引用（`com.delua.androlua` 同样替换为 `com.lioisme.delua.androlua`） | `grep -r "package com\.delua" app/src androlua/src` 无输出 |
| ACC-003 | 保留 `com.luajava`、`com.androlua`、`com.android.cglib` 不变 | `grep -r "package com\.\(luajava\|androlua\|android\.cglib\)"` 有 70+ 匹配 |
| ACC-004 | 项目可编译通过 | `.\gradlew assembleArm64Debug` 成功 |

##### 工作量分解

| 步骤 | 描述 | 验证条件 |
|------|------|---------|
| 2.1 | 在 IDE 中使用重构功能（Refactor → Rename）更改 `com.delua` → `com.lioisme.delua`（**仅更改 Kotlin 源文件，不更改 Java 源文件**） | 重构预览中排除 `androlua/src/main/java/` |
| 2.2 | 手动更新 `app/build.gradle.kts`：`namespace = "com.lioisme.delua"`、`applicationId = "com.lioisme.delua"` | 文件确认 |
| 2.3 | 手动更新 `androlua/build.gradle.kts`：`namespace = "com.lioisme.delua.androlua"` | 文件确认 |
| 2.4 | 更新 `app/src/main/AndroidManifest.xml` 中引用 `com.delua` 的类路径 | 全文搜索确认无遗留 |
| 2.5 | 更新 `androlua/src/main/AndroidManifest.xml`（如引用旧包名） | 文件确认 |
| 2.6 | `ProjectManager.kt` 第 138 行更新默认 packageName | 文件确认 |
| 2.7 | 全量编译验证 | `.\gradlew assembleArm64Debug` 通过 |

---

### 阶段二：功能清理（P1 高优先级）

---

#### WP-003 移除内置样例工程

| 属性 | 值 |
|------|-----|
| **标识符** | WP-003 |
| **标题** | 移除内置样例工程 |
| **严重程度** | **P1 — 高优先级** |
| **前置依赖** | WP-002（因 `DeluaApp.kt` 包路径变更） |
| **预估工时** | 0.25 人日 |
| **风险等级** | 低 |

##### 问题描述

当前 APK 中打包了 4 个样例工程（`assets/samples/` 目录），首次启动时自动提取到工作目录。需求明确要求"不内置工程"。

##### 范围

| 操作 | 文件/目录 |
|------|----------|
| 删除 | `app/src/main/assets/samples/`（整个目录） |
| 修改 | `app/src/main/kotlin/com/lioisme/delua/DeluaApp.kt`：移除 `extractSamples()` 方法及其调用 |
| 清理 | 引用 `extractSamples()` 的导入语句 |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | `assets/samples/` 目录不存在 | `Test-Path app/src/main/assets/samples` 返回 `False` |
| ACC-002 | `DeluaApp.kt` 中不存在 `extractSamples` 方法 | `Select-String "extractSamples" DeluaApp.kt` 无匹配 |
| ACC-003 | APK 中不包含样例工程文件 | `unzip -l app/build/outputs/apk/*.apk \| grep samples` 无输出 |
| ACC-004 | 首次安装并启动后，`/storage/emulated/0/AndroLua/project/` 目录为空 | 设备上 `ls /storage/emulated/0/AndroLua/project/` 为空 |

---

#### WP-004 修复构建脚本错误处理

| 属性 | 值 |
|------|-----|
| **标识符** | WP-004 |
| **标题** | 修复构建脚本错误处理 |
| **严重程度** | **P1 — 高优先级** |
| **前置依赖** | WP-001（构建脚本上下文） |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

`rebuild_engine.ps1` 使用 `Start-Process` 调用 `ndk-build`，stderr 不可见。当部分模块编译失败时，脚本无法提供诊断信息。

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 构建日志完整保存至 `$BUILD_DIR/build.log` | 文件存在且非空 |
| ACC-002 | 所有编译错误、警告均记录至日志，含文件名和行号 | 日志中每行有 `module/path:line:` 格式 |
| ACC-003 | 脚本退出码区分"完全成功"（0）与"部分失败"（1） | 当任一模块失败时 `$LASTEXITCODE -eq 1` |
| ACC-004 | 脚本在退出前打印汇总：成功模块数 / 总模块数 | 屏幕输出含 `Build summary: N/M modules succeeded` |

##### 工作量分解

| 步骤 | 描述 |
|------|------|
| 4.1 | 将 `Start-Process` 替换为 `& $NDK_BUILD ... 2>&1 \| Tee-Object -FilePath $buildLog` |
| 4.2 | 添加编译后扫描逻辑，通过 `Get-ChildItem "$BUILD_DIR/libs/*/" -Filter "*.so"` 统计产物 |
| 4.3 | 将预期模块列表与产物列表比对，报告缺失模块 |

---

### 阶段三：配置规范化（P2 中优先级）

---

#### WP-005 清理 ProGuard 规则

| 属性 | 值 |
|------|-----|
| **标识符** | WP-005 |
| **标题** | 清理 ProGuard 规则 |
| **严重程度** | **P2 — 中优先级** |
| **前置依赖** | WP-002（类路径变更后） |
| **预估工时** | 0.25 人日 |
| **风险等级** | 低 |

##### 问题描述

`proguard-rules.pro` 中保留规则 `-keep class org.luaj.** { *; }` 引用了已移除的 LuaJ 依赖。需要更新为保留当前运行时的类。

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | `proguard-rules.pro` 无 `org.luaj` 引用 | `Select-String "org.luaj" proguard-rules.pro` 无匹配 |
| ACC-002 | 以下类在 release 构建中不被混淆：`com.luajava.*`、`com.androlua.*` | 反编译 APK 确认类名保持 |
| ACC-003 | release APK 运行 `LuaState._newstate()` JNI 调用正常 | 引擎测试脚本 `require "luajava"` 正常返回 |

---

#### WP-006 清理不必要的权限声明

| 属性 | 值 |
|------|-----|
| **标识符** | WP-006 |
| **标题** | 清理不必要的权限声明 |
| **严重程度** | **P2 — 中优先级** |
| **前置依赖** | WP-002（Manifest 路径变更后） |
| **预估工时** | 0.5 人日 |
| **风险等级** | 中 |

##### 问题描述

`AndroidManifest.xml` 当前声明了 35+ 权限（CAMERA、BLUETOOTH、NFC 等），大部分在 IDE 场景中不必要。多余的权限会引发用户隐私顾虑和应用商店审核问题。

##### 权限保留原则

| 权限类别 | 保留 | 说明 |
|---------|------|------|
| `INTERNET` | ✅ | HTTP 请求、Lua 网络库 |
| `READ/WRITE_EXTERNAL_STORAGE` | ✅ | 工程文件管理 |
| `READ_MEDIA_*` (API 33+) | ✅ | Android 13+ 兼容 |
| `MANAGE_EXTERNAL_STORAGE` | ✅ | Android 11+ 全文件访问 |
| `CAMERA`、`BLUETOOTH`、`NFC` 等 | ❌ 移除 | 应由用户脚本在运行时请求 |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | `AndroidManifest.xml` 中权限声明不超过 10 个 | `Select-String "uses-permission" AndroidManifest.xml \| Measure-Object \| % Count` ≤ 10 |
| ACC-002 | 移除的权限在运行时不被宿主代码隐式依赖 | 编译通过且无 `SecurityException` |

---

### 阶段四：架构加固（P3 低优先级）

---

#### WP-007 EngineBridge 错误处理与容错

| 属性 | 值 |
|------|-----|
| **标识符** | WP-007 |
| **标题** | EngineBridge 错误处理与容错 |
| **严重程度** | **P3 — 低优先级** |
| **前置依赖** | 无 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

当前 `EngineBridge.kt` 在引擎执行脚本时无超时机制、无进程崩溃恢复。若 EngineActivity 崩溃（如 Lua 脚本死循环），用户将看到 Activity 已停止的 ANR 对话框。

##### 改进方案

| 改进项 | 实现方式 |
|--------|---------|
| 执行超时 | `startActivityForResult` + 设定 `Handler.postDelayed` 超时取消 |
| 崩溃检测 | 在 `EngineActivity.onCreate` 中注册 `UncaughtExceptionHandler`，写入 `engine_crash.json` |
| 进度反馈 | 通过 `LocalBroadcastManager` 发送执行状态 |
| 返回解析健壮性 | `engine_result.json` 解析失败时使用默认错误结果 |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 包含死循环的脚本在 30 秒后超时返回，不造成 ANR | 运行 `while true do end`，30s 后返回超时错误 |
| ACC-002 | 引擎进程崩溃后，宿主能检测到并显示友好提示 | 模拟引擎崩溃，宿主显示"引擎异常退出" |
| ACC-003 | `engine_result.json` 文件损坏或不存在时，返回默认 `EngineResult(success=false, errorMessage="未知错误")` | 手动删除 json 文件后调用 `collectResult()` |

---

#### WP-008 解耦 Java 运行时模块

| 属性 | 值 |
|------|-----|
| **标识符** | WP-008 |
| **标题** | 解耦 Java 运行时模块 |
| **严重程度** | **P3 — 低优先级** |
| **前置依赖** | 无 |
| **预估工时** | 3–5 人日 |
| **风险等级** | 中 |

##### 问题描述

`androlua/src/main/java/com/androlua/LuaActivity.java` 达 1808 行，`LuaApplication.java` 达 394 行，混合了引擎初始化、包管理、UI 绘制、事件处理等职责。这种"上帝类"设计降低可维护性。

##### 重构方案

| 新文件 | 职责 | 从原类提取 |
|--------|------|-----------|
| `LuaEngine.kt` | Lua 状态管理、脚本加载、生命周期 | `LuaActivity.java`: runScript, doFile, 状态管理 |
| `LuaPathManager.kt` | 路径解析、cpath/lpath 构造 | `LuaApplication.java`: getLuaPath, luaCpath, luaLpath |
| `LuaPackageManager.kt` | 包/库依赖管理 | `LuaActivity.java`: loadLibs, getLibrarys |
| `LuaFileUtils.kt` | 文件操作、ZIP 处理 | `LuaUtil.java`: zip, unzip, readZip |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 重构后编译通过，无功能回归 | 所有测试工程运行结果与重构前一致 |
| ACC-002 | `LuaActivity.java` ≤ 600 行 | `(Get-Content LuaActivity.java).Count -le 600` |
| ACC-003 | 每个新文件 ≤ 300 行 | 各文件行数检查 |
| ACC-004 | 移除 `LuaActivity.java` 中对 UI 绘制方法的直接依赖（`LuaDrawable`、`Canvas`） | grep 确认不存在 `onDraw`、`draw` 方法 |

---

### 阶段五：工程维护（P4 优化级）

---

#### WP-009 补齐本地化字符串

| 属性 | 值 |
|------|-----|
| **标识符** | WP-009 |
| **标题** | 补齐本地化字符串 |
| **严重程度** | **P4 — 优化级** |
| **前置依赖** | 无 |
| **预估工时** | 0.5 人日 |
| **风险等级** | 低 |

##### 问题描述

`values/strings.xml`（147 条）与 `values-zh/strings.xml`（132 条）之间存在 15 条不匹配。中文翻译不完整。

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 中文字符串条目数量 ≥ 英文条目数 | `grep -c 'name=' values-zh/strings.xml` ≥ `grep -c 'name=' values/strings.xml` |
| ACC-002 | 每个中文条目翻译完整，无语义丢失 | 逐条人工审核 |
| ACC-003 | 新增 UI 元素的字符串同步在中文资源文件中添加占位翻译 | Review 未翻译条目列表 |

---

#### WP-010 CI 集成自动原生引擎构建

| 属性 | 值 |
|------|-----|
| **标识符** | WP-010 |
| **标题** | CI 集成自动原生引擎构建 |
| **严重程度** | **P4 — 优化级** |
| **前置依赖** | WP-001 |
| **预估工时** | 1 人日 |
| **风险等级** | 低 |

##### 问题描述

当前 `build.yml` 中存在 `rebuild-engine` 作业但标记为 `workflow_dispatch`（手动触发）。这意味着任何 JNI C 代码变更需要开发者手动重建——这是一个脆弱环节。

##### 改进方案

| 变更 | 描述 |
|------|------|
| 触发条件 | 当 `scripts/`、`build-scripts/` 或 `prebuilt/` 变更时自动触发 |
| 缓存 | 缓存 NDK 下载和编译产物（`actions/cache`） |
| 产物管理 | 自动将新的 `.so` 文件提交至 prebuilt/（或作为 CI artifact 发布） |

##### 验收标准

| # | 标准 | 验证方法 |
|---|------|---------|
| ACC-001 | 当 `scripts/rebuild_engine.*` 变更时触发自动重建 | GitHub Actions 日志显示作业自动启动 |
| ACC-002 | 构建产物与预期一致 | CI 结束时 artifact 包含 14+ 个 `.so` |
| ACC-003 | 构建总耗时 ≤ 15 分钟（含 NDK 下载缓存命中时 ≤ 5 分钟） | CI Job 运行时长统计 |

---

## 4. 验收总则

### 4.1 验收流程

每个工作包完成后，必须独立执行以下流程：

```
1. 开发者自验 → 通过验收标准
     │
     ▼
2. git commit（含清晰的提交消息）
     │
     ▼
3. 触发 CI 构建（关联 Work Package ID）
     │
     ▼
4. CI 构建通过
     │
     ▼
5. 人工 Code Review
     │
     ▼
6. 在物理设备（Android 14+）上运行验证测试
     │
     ▼
7. 在验收标准表上逐项打勾 ✓
     │
     ▼
8. 更新 WORK_ORDER.md 中对应 WP 状态为 ✅
```

### 4.2 拒绝验收的情形

下列情况之一的交付物将被拒绝：

- 在 physical 设备上出现崩溃、ANR 或功能回归
- 未满足对应工作包的全部验收标准（ACC）
- 引入新的 lint 警告（非原代码已存在的警告）
- 提交消息不符合约定格式（`WP-XXX: <description>`）

### 4.3 回归测试基准

在每次阶段交付前，以下 3 个测试工程必须在设备和模拟器上运行通过：

| 测试工程 | 验证能力 | 预期结果 |
|---------|---------|---------|
| `kleinbottle/` | OpenGL ES 渲染 + 循环动画 | 旋转 Klein 瓶 3D 渲染，无异常退出 |
| `demo/` | UI 布局 + HTTP 请求 + JSON 解析 | 界面正常显示，网络数据加载成功 |
| `testjson/` | JSON 树形浏览器 + 触摸交互 | 展开/折叠 JSON 节点，滚动无异常 |

---

## 5. 回滚与应急方案

### 5.1 提交前回滚

任一工作包在验收流程中发现致命问题，执行：

```bash
git checkout -- <changed_files>
git clean -fd
```

### 5.2 已提交回滚

若已提交的工作包在 CI 或设备验证中失败：

```bash
git revert HEAD --no-edit
```

### 5.3 原生库回滚

若 WP-001 导致 `.so` 文件全部或部分损坏，使用已知良好版本恢复：

```bash
git checkout HEAD~1 -- prebuilt/ app/src/main/jniLibs/
```

### 5.4 阶段冻结

若某一工作包在 3 个工作日内无法通过验收，该工作包将被标记为 **BLOCKED** 并记入风险日志，团队移步至下一非阻塞依赖的工作包。

---

## 6. 风险管理

| 风险 ID | 描述 | 概率 | 影响 | 缓解措施 |
|---------|------|------|------|---------|
| R-001 | NDK 版本升级导致 C 模块 API 不兼容 | 中 | 高 | 锁定 NDK 版本（r26.1），使用 NDK 版本检测脚本 |
| R-002 | Android 15+ 限制 `System.loadLibrary` | 低 | 高 | 跟踪 Android 15 变更，评估 `JNI_OnLoad` 方案 |
| R-003 | `libluv.so` 因包含完整 libuv 源码体积过大 | 中 | 低 | 评估是否实际需要 luv 模块；不需要则从构建列表移除 |
| R-004 | `libsensor.so`（C++）在部分设备上加载失败 | 低 | 中 | 添加 try-catch 加载降级，不影响其他模块 |
| R-005 | 包名重命名遗漏 `AndroidManifest.xml` 中的引用 | 中 | 高 | 使用 `grep -rn "com\.delua"` 穷举扫描 |
| R-006 | ProGuard 混淆导致 JNI 方法名不匹配 | 低 | 高 | 使用 `-keep class com.luajava.** { native <methods>; }` 精确保留 |

---

## 附录 A：文件映射表

| 工作包 | 关联文件 |
|--------|---------|
| WP-001 | `scripts/rebuild_engine.ps1`、`scripts/rebuild_engine.sh`、`prebuilt/*/`、`app/src/main/jniLibs/*/`、`build-scripts/jni/**/*.mk` |
| WP-002 | `app/build.gradle.kts`、`androlua/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`androlua/src/main/AndroidManifest.xml`、`app/src/main/kotlin/com/delua/**/*.kt`、`app/src/main/java/com/delua/**/*.java` |
| WP-003 | `app/src/main/assets/samples/`、`app/src/main/kotlin/.../DeluaApp.kt` |
| WP-004 | `scripts/rebuild_engine.ps1`、`scripts/rebuild_engine.sh` |
| WP-005 | `app/proguard-rules.pro` |
| WP-006 | `app/src/main/AndroidManifest.xml` |
| WP-007 | `androlua/src/main/kotlin/.../engine/EngineBridge.kt`、`engine/EngineResult.kt` |
| WP-008 | `androlua/src/main/java/com/androlua/LuaActivity.java`、`LuaApplication.java`、`LuaUtil.java` |
| WP-009 | `app/src/main/res/values/strings.xml`、`values-zh/strings.xml` |
| WP-010 | `.github/workflows/build.yml` |

## 附录 B：构建验证命令速查

```bash
# 完全编译
.\gradlew assembleArm64Debug

# 仅构建 arm64 ABI
.\scripts\rebuild_engine.ps1 -ABI arm64-v8a

# 检查 APK 包名
aapt dump badging app\build\outputs\apk\arm64\debug\*.apk | findstr "package:"

# 检查 APK 中的原生库
unzip -l app\build\outputs\apk\arm64\debug\*.apk | findstr "\.so$"

# 全局搜索旧包名残留
grep -rn "com\.delua" app/src androlua/src --include="*.kt" --include="*.java" --include="*.xml"

# 运行 lint
.\gradlew lintArm64Debug
```

---

*文档结束*
