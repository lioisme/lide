# LIDE 原生库缺失审计报告

| 元数据 | 内容 |
|--------|------|
| 文档版本 | 1.1 |
| 审计日期 | 2026-07-31 |
| 审计对象 | lide 原生引擎库（NDK .so 模块） |
| 对比基线 | AndroLua Pro 4.2.0（`docs/AndroLua_pro-master/`） |
| 审计范围 | JNI 源码树、构建配置、Lua 运行时脚本、Java 运行时加载链 |
| 执行状态 | **阶段 A 已完成（42/42 模块成功）**；阶段 B（设备验证）已由 LIBS-003/006 收尾（2026-08-05 回填，见 `LIBS_SUPPORT_WORK_ORDER.md` 真机矩阵 V-01~V-10） |

---

## 1. 执行摘要

lide 当前仅打包 **2 个**自定义原生库（`libluajava.so`、`libgl.so`），而 AndroLua Pro 参考源码树定义并可构建 **15 个** `.so`（含 `libluajava.so`），即 **缺失 13 个原生模块**。

缺失模块中，**4 个被 lide 内置 Lua 运行时脚本直接依赖**（`cjson`、`socket`、`mime`、`xml`），缺库将导致 `require "cjson"`、`http.lua`、`smtp.lua` 等核心功能直接报错；其余 9 个为 AndroLua Pro 生态能力（`md5`、`crypt`、`zip`、`zlib`、`regex`、`canvas`、`bson`、`luv`、`sensor`），供用户脚本可选加载。

**根因**：原生模块全部从 C 源码编译，源码存在于 `docs/AndroLua_pro-master/app/src/main/jni/`（398 个 C/C++ 文件），lide 的 `build-scripts/jni/` 仅含 Android.mk 构建配置（17 个 .mk，0 个 C 文件）。上一轮 WP-001 修复已修正 Android.mk 的链接声明，但构建脚本的源码拷贝源指向了不含源码的 `build-scripts/jni/`，**全量编译目前无法产出任何模块**（详见 §5.1）。

---

## 2. 现状盘点

### 2.1 已打包产物（lide）

```
app/src/main/jniLibs/
├── arm64-v8a/   libgl.so, libluajava.so
├── armeabi-v7a/ libgl.so, libluajava.so
└── x86_64/      libgl.so, libluajava.so
```

### 2.2 参考源码树可构建模块（AndroLua Pro）

```
docs/AndroLua_pro-master/app/src/main/jni/
├── bson/      1 个 C 文件   (25 KB)      → libbson.so
├── canvas/    1 个 C 文件   (88 KB)      → libcanvas.so
├── cjson/     5 个 C 文件   (57 KB)      → libcjson.so
├── crypt/     3 个 C 文件   (35 KB)      → libcrypt.so
├── lua/      61 个 C 文件  (812 KB)      → liblua.a（静态，链接入 libluajava.so）
├── luagl/     8 个 C 文件   (89 KB)      → libgl.so
├── luajava/   3 个 C 文件  (101 KB)      → libluajava.so
├── luv/     144 个 C 文件 (1637 KB)      → libluv.so（内嵌 libuv 1.21.1-dev 全源码）
├── md5/       5 个 C 文件   (13 KB)      → libmd5.so
├── regex/     4 个 C 文件   (41 KB)      → libregex.so
├── sensor/    2 个 C++ 文件 (11 KB)      → libsensor.so（唯一 C++ 模块）
├── socket/   33 个 C 文件  (200 KB)      → libsocket.so + libmime.so（双模块）
├── xml/       1 个 C 文件   (12 KB)      → libxml.so（LuaXML 单文件实现，无 expat 依赖）
├── zip/     108 个 C 文件  (419 KB)      → liblibzip.so（libzip 全源码）
└── zlib/      2 个 C 文件   (12 KB)      → libzlib.so（lua_zlib 封装，链接 NDK 系统 -lz）
```

### 2.3 明确排除的模块

| 模块 | 目录 | 原因 | 建议 |
|------|------|------|------|
| `lluv` | 162 文件 / 1.7 MB | 仅有 `Android.mk2`（AndroLua Pro 官方即禁用），且与 `luv` 功能重叠 | 不构建 |
| `lsqlite3` | 4 文件 / 5.4 MB | 仅有 `Android.mk2`，官方禁用；内嵌 sqlite3.c 体积过大 | 不构建（如需可后续单独评估） |
| `tcc` | 21 文件 / 665 KB | 仅有 `Android.mk2`（Tiny C Compiler，安全风险高） | 不构建 |
| `lfs` | 2 文件 / 27 KB | 无任何构建文件，官方 APK 未包含 | 可选补充（见 §7 方案 B） |

---

## 3. 缺失模块清单（13 项）

| # | .so 文件 | luaopen 导出 | Lua 侧 require 名 | 依赖 lide 现有代码 | 编译风险 |
|---|----------|--------------|--------------------|--------------------|----------|
| 1 | `libcjson.so` | `luaopen_cjson` / `luaopen_cjson_safe` | `"cjson"` | ✅ **直接依赖** | 低 |
| 2 | `libsocket.so` | `luaopen_socket_core` | `"socket.core"`（经 `socket.lua`） | ✅ **直接依赖** | 低 |
| 3 | `libmime.so` | `luaopen_mime_core` | `"mime.core"`（经 `mime.lua`） | ✅ **直接依赖** | 低 |
| 4 | `libxml.so` | `luaopen_xml_core` | `"xml.core"`（经 `xml.lua`） | ✅ **直接依赖** | 低 |
| 5 | `libmd5.so` | `luaopen_md5` | `"md5"` | 可选（用户脚本） | 低 |
| 6 | `libcrypt.so` | `luaopen_crypt` | `"crypt"` | 可选 | 低 |
| 7 | `liblibzip.so` | `luaopen_brimworks_zip` | `"brimworks.zip"` | 可选 | 中（108 文件，需 `-lz`） |
| 8 | `libzlib.so` | `luaopen_zlib` | `"zlib"` | 可选 | 低（NDK 自带 zlib） |
| 9 | `libregex.so` | `luaopen_regex` | `"regex"` | 可选 | 低 |
| 10 | `libcanvas.so` | `luaopen_canvas` | `"canvas"` | 可选 | 低（需确认 `-landroid`/`-ljnigraphics`） |
| 11 | `libbson.so` | `luaopen_bson` | `"bson"` | 可选 | 低 |
| 12 | `libluv.so` | `luaopen_luv` | `"luv"` | 可选 | **高**（libuv 1.21.1 旧源码，NDK r26+ 兼容性存疑） |
| 13 | `libsensor.so` | `luaopen_sensor` | `"sensor"` | 可选 | 中（C++ 模块，需 `APP_STL=c++_static`） |

---

## 4. 加载机制分析（为何缺库会导致功能不可用）

### 4.1 两条加载路径

**路径 A — JNI 静态加载（仅 luajava）**
`LuaState.java:106` 在类加载时 `System.loadLibrary("luajava")`，从 APK `lib/{abi}/` 直接加载。这是唯一一条不依赖 `luaCpath` 的路径。

**路径 B — Lua C 模块加载（其余全部模块）**
`LuaApplication.java:189` 构造：

```java
luaCpath = getApplicationInfo().nativeLibraryDir + "/lib?.so" + ";" + libDir + "/lib?.so";
```

即 **`require "cjson"` → 在 `nativeLibraryDir/libcjson.so` 中查 `luaopen_cjson`**。因此：
- 模块 `.so` 打包进 APK `lib/{abi}/`（即 `app/src/main/jniLibs/{abi}/`）即可被 Lua `require` 直接命中；
- 辅助路径 `libDir`（应用私有目录）由 `LuaDexLoader.loadLibs()` 从用户工程 `luaDir/libs/` 复制，供用户自装 `.so`（import.lua 的 `libsloader` 备用机制）。

### 4.2 缺失产生的实际故障

| 场景 | 触发点 | 现状行为 |
|------|--------|---------|
| `require "cjson"` | `bmob.lua`、用户脚本 | `no file ./libs/libcjson.so` 报错 |
| `http.lua` / `smtp.lua` / `ftp.lua` | 网络请求 | `require("socket")` → `socket.core` 失败，HTTP 功能不可用 |
| `mime.lua` | 邮件/编码 | `require("mime.core")` 失败 |
| `xml.lua` | XML 解析 | `require("xml.core")` 失败 |
| `import "gl"`（GL 渲染） | kleinbottle 工程 | 可用（已打包） |

**结论**：lide 当前只能运行不依赖网络/JSON-C/XML 的基础脚本；三个回归测试工程中 `demo/` 的 HTTP + JSON 路径实际依赖 `socket`/`mime`/`cjson`，在缺库状态下无法通过回归验收。

---

## 5. 补齐前置条件与既有问题

### 5.1 ⚠️ 构建脚本源码拷贝源错误（WP-001 遗留缺陷）

`scripts/rebuild_engine.ps1` 与 `.sh` 当前从 `build-scripts/jni/` 拷贝 JNI 树，但该目录**只有 17 个 .mk 文件、0 个 C 源码**。直接运行将导致 `ndk-build` 无编译源，产物为 0。

**正确拓扑**：
- **源码（唯一真源）**：`docs/AndroLua_pro-master/app/src/main/jni/`（398 个 C/C++ 文件，勿改动）
- **构建配置（lide 维护）**：`build-scripts/jni/**/Android.mk`（已修复 `LOCAL_SHARED_LIBRARIES := luajava`，共 12 处）

**修复方式**：构建脚本按以下顺序操作——
1. 从 `docs/AndroLua_pro-master/app/src/main/jni/` 拷贝完整源码树至临时构建目录；
2. 用 `build-scripts/jni/` 下修复后的 Android.mk **覆盖** 同名文件；
3. 删除禁用目录（`lluv`、`lsqlite3`、`tcc`、`lfs`）与 `lua/lua.c`、`lua/luac.c`；
4. 写入 `Application.mk`（`APP_ABI`、`APP_PLATFORM=android-24`、`APP_STL=c++_static`、`APP_OPTIM=release`）后执行 `ndk-build`。

### 5.2 NDK 版本选择

本机可用：NDK 25.1.8937393 / 27.0.12077973 / 28.2.13676358 / 29.0.14206865。

| 版本 | 适用性 | 说明 |
|------|--------|------|
| r25 (25.1) | ✅ 最稳 | 与 2018 年 libuv 1.21.1 源码兼容性最好 |
| r26+ (27/28/29) | ⚠️ 需逐个验证 | clang 17+ 对旧汇编/内联系统调用更严格，`luv` 风险最高 |

**建议**：按 WORK_ORDER 风险表 R-001 锁定 **r26.1** 为 CI 标准版本，但初次全量编译用 r25 打通链路，再逐模块验证 r26+ 兼容性。构建脚本应优先使用 `ANDROID_NDK_HOME` 且加入版本探测。

### 5.3 各模块编译要点

| 模块 | 编译要点 | 备注 |
|------|---------|------|
| cjson / md5 / crypt / bson / zlib / xml / regex | 纯 C，依赖 `LOCAL_SHARED_LIBRARIES := luajava`，无需额外 `-l` 链 | md5 需 `compat-5.2.c`（源码已含） |
| socket / mime | 纯 C（`usocket.c` 为 Android 适配），双模块同目录 | 已验证模块名 `socket`、`mime` |
| zip (libzip) | 108 个 C 文件，需 `LOCAL_LDLIBS := -lz`（已配置）；建议 `-fPIC` 由 NDK 默认启用 | 编译慢，建议单独验证 |
| canvas | 依赖 Android `libjnigraphics`（`Bitmap` 操作），需确认 `LOCAL_LDLIBS` 追加 `-ljnigraphics -llog` | 若链接失败需补链 |
| sensor | C++（`luasensor.cpp`），依赖 `APP_STL=c++_static`（Application.mk 已写入）；已用 `LOCAL_SHARED_LIBRARIES` | 加载失败需 try-catch 降级（R-004） |
| luv | 内嵌 libuv **1.21.1-dev**（2018 年旧版，`include/uv/version.h`），93 个 uv 源码文件 + `luv/luv.c`；`android-ifaddrs.c`、`linux-syscalls.c` 等旧实现与 NDK r26+ 冲突风险高 | **最高风险**；建议先独立编译验证，失败则按 R-003 决策：升级 libuv 源码或从构建列表移除 |

---

## 6. 依赖关系矩阵（lide 代码 → 原生模块）

### 6.1 直接依赖（必须补齐）

| lide 资产 | 引用的模块 | 引用方式 |
|-----------|-----------|---------|
| `assets/lua/bmob.lua` | cjson | `require "cjson"` |
| `assets/lua/http.lua` | socket, mime, socket.url | `require("socket")` / `require("mime")` |
| `assets/lua/smtp.lua` | socket, mime, socket.tp | 同上 |
| `assets/lua/ftp.lua` | socket, socket.url, socket.tp | 同上 |
| `assets/lua/mime.lua` | mime | `require("mime.core")` |
| `assets/lua/socket.lua` | socket | `require("socket.core")` |
| `assets/lua/xml.lua` | xml | `require("xml.core")` |
| `assets/samples/demo/main.lua` | dkjson（纯 Lua）→ 无原生依赖 | — |
| `assets/samples/kleinbottle/main.lua` | gl | `import "gl"`（已打包） |

### 6.2 间接/可选依赖

| 场景 | 模块 | 说明 |
|------|------|------|
| 用户脚本常见功能 | md5, crypt, zlib, zip, regex | AndroLua 生态常用工具模块 |
| 图形绘制 | canvas | `luaCanvas` 绘图 API |
| BSON 序列化 | bson | 与 MongoDB 兼容 |
| 事件循环 | luv | libuv 异步 IO；lluv 为重复实现已排除 |
| 传感器 | sensor | 加速度/陀螺仪等 |

### 6.3 Kotlin/Java 宿主代码

`ModuleRegistry`/`LibraryRegistry` 为动态扫描（`metadata.json`），**无硬编码模块清单**；`EngineBridge`/`EngineActivity` 不直接加载任何模块。因此补齐模块**无需修改宿主代码**，打包即用。

---

## 7. 补齐实施方案

### 阶段 A：打通全量构建链路（必做，0.5 人日）

1. 修正 `scripts/rebuild_engine.ps1` / `.sh` 的源码拷贝源（§5.1 拓扑）；
2. 用 NDK r25 全量编译三 ABI，产出 15 个 `.so`；
3. 将产物同步至 `prebuilt/{abi}/` 与 `app/src/main/jniLibs/{abi}/`；
4. 在 `build-scripts/jni/` 下补充缺失模块的 Android.mk（canvas 追加 `-ljnigraphics`，如需要）。

### 阶段 B：核心 4 模块优先验收（0.5 人日）

1. 确认 `libcjson.so`、`libsocket.so`、`libmime.so`、`libxml.so` 编译通过；
2. 设备验证：`require "cjson"`、`http.get()`、`xml` 解析正常；
3. `demo/` 回归工程跑通（HTTP + JSON 全链路）。

### 阶段 C：全模块编译与风险处置（1–2 人日）

| 模块 | 处置 |
|------|------|
| md5 / crypt / zlib / regex / bson / zip | 直接编译，验证 luaopen 符号 |
| canvas | 补充 `-ljnigraphics -llog` 后编译 |
| sensor | 验证 C++ 链接（`c++_static`），设备加载测试 |
| luv | **优先独立编译**；NDK r26+ 失败则按 R-003 决策：① 升级内嵌 libuv 至 ≥1.44；② 或从 EXPECTED_MODULES 移除并在文档标注 |

### 阶段 D：验收与归档（0.5 人日）

1. 三 ABI 产物齐全性校验（脚本 `EXPECTED_MODULES` 比对，缺失即 `FAILED: <module>` 退出）；
2. `readelf -d` 抽查动态段有效性；
3. APK 体积增量审计（13 模块 × 3 ABI，预计 ≤ 6 MB）；
4. `git add prebuilt/ app/src/main/jniLibs/` 提交二进制产物。

**验收标准**（对应 WP-001 ACC-001 ~ ACC-005）：每 ABI ≥ 14 个 `.so`、无 error 级输出、体积增量 ≤ 6 MB、失败模块显式报错。

---

## 7.5 执行记录（2026-07-31，阶段 A 完成）

| 项目 | 结果 |
|------|------|
| 构建工具链 | NDK **29.0.14206865**（r25 目录为空；r29 为唯一完整 NDK，全量编译通过） |
| 构建脚本 | `rebuild_engine.ps1` 修复：① 源码拷贝源指向 docs 参考树；② `build-scripts/jni/` 覆盖 Android.mk；③ 禁用目录（lluv/lsqlite3/tcc/lfs）；④ **补丁机制**（`build-scripts/patches/` 覆盖参考树文件）；⑤ `cmd /c` 重定向日志与退出码；⑥ 根目录解析不依赖 `$PSScriptRoot`（opencode 会话中为空） |
| 源码补丁 | `build-scripts/patches/zip/mkstemp.c`：新增 `#include <unistd.h>`（NDK r29 / C99 严格要求 `getpid()` 显式声明，libzip 源码缺失导致唯一编译错误） |
| 编译结果 | **42/42 模块成功**（3 ABI × 14 模块），`libzip.so` 按 LOCAL_MODULE 输出（`liblibzip.so` 预期名已修正为 `libzip.so` 实际名） |
| 静态验收 | `llvm-readelf -d` 抽查 15 个模块：NEEDED 全部为 Android 系统库（libc/libm/libdl/liblog/libandroid/libGLESv1_CM/libz）+ libluajava，无意外依赖 |
| APK 验收 | `assembleArm64Debug` 成功；APK 内含 15 个 arm64 `.so`（原生库合计 1.3 MB，APK 22.79 MB） |
| 加载链路 | `require "xxx"` 经 `luaCpath = nativeLibraryDir + "/lib?.so"`（LuaApplication.java:189）直接命中打包 `.so`，**无需修改宿主代码**；`LuaDexLoader.getLibrarys()` 仅服务用户工程自带 `.so`，与本次无关 |
| 待办 | 阶段 B：设备验证 `cjson`/`socket`/`mime`/`xml` 及 `demo/` 回归 |

---

## 8. 结论

lide 缺失的 13 个原生库全部可从 `docs/AndroLua_pro-master/app/src/main/jni/`（398 个 C/C++ 源文件）编译产出，源码完备、无需移植。阻塞补齐的**唯一技术障碍**是构建脚本的源码拷贝源错误（§5.1），修复后按 §7 阶段 A → B → C → D 执行即可。

**优先级排序**：
1. **P0**：修复构建脚本拷贝源 + 全量编译链路（阶段 A）
2. **P1**：cjson / socket / mime / xml 四模块（阶段 B，解锁 http/smtp/xml/json 功能）
3. **P2**：md5 / crypt / zip / zlib / regex / canvas / bson / sensor（阶段 C，生态完整性）
4. **P3**：luv（阶段 C 风险项，独立验证后决策）

---

*审计完成。数据来源：lide 工作树、AndroLua Pro 4.2.0 参考源码树、Lua 运行时脚本、Java 运行时加载链、NDK 构建配置。*
