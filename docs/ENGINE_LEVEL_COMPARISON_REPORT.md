# delua 与 AndroLua Pro 引擎水平对比报告（修订版）

- 日期：2026-07-31
- 依据：`docs/log/delua.log`（2026-07-31 16:01:37，com.lioisme.delua）与 `docs/log/androluapro.log`（2026-07-31 06:00:36，com.androlua），同一测试工程 `docs/project/test`（Universal Lua Probe v20，956 项）在同一环境产出；结合双方 Java/原生源码、构建脚本、APK 产物逐层核对。
- 前提：delua 为**首次安装后立即测试**，排除旧安装包/残留数据因素。

## 1. 结论摘要

| 指标 | delua | AndroLua Pro |
|---|---|---|
| 总分 | 878/956 (91%) | 879/956 (91%) |
| 性能扩展 | 35% (18/51) | 37% (19/51) |
| 其余 13 个评分组 | 与 AndroLua Pro 完全一致 | — |

唯一差异（1/956）为 `require("socket")` 失败（扩展库-网络 20% vs 40%）。

**根因（已定案）：delua 引擎的资产解包逻辑缺陷 —— `LuaApplication.extractLuaScripts()` 用 `getAssets().open()` 逐条打开 `assets/lua/` 下的每一项，其中 "socket" 是子目录，打开目录抛 IOException；try-catch 位于循环外，异常中断剩余全部解包，导致 `socket.lua`、`socket/{url,tp,headers}.lua` 及其后所有脚本（su.lua/test.lua/xml.lua）从未落盘。`require("socket")` 找不到 socket.lua → "模块未找到" → 探针记为"无/超时"。**

这不是性能波动、不是 .so 链接问题、不是设备环境问题，而是 delua 侧可复现、可修复的代码缺陷。

## 2. 日志差异定位

两份日志共 1123 行，实质差异仅一处：

| 位置 | delua | AndroLua Pro | 判定 |
|---|---|---|---|
| L10 / L27 | 878/956；性能扩展 35% | 879/956；37% | 1 分差 |
| **L1025** | **[--] socket: 无/超时** | **[OK] socket: LuaSocket 3.0-rc1** | **唯一实质差异** |
| L68/681/691/693/694/941 | 内存/GC 时序微差 | — | 测量噪音 |
| L708-709 | package.path/cpath 应用目录 | com.androlua 目录 | 预期差异 |

其余逐项一致：cjson/json/zlib/md5/base64/bit32/ltn12 全部一致；性能基准 10/10 一致（`递归(1000)` delua 0.0116s vs AndroLua Pro 0.0002s 为后者低于时钟分辨率的测量伪迹）。

## 3. 根因分析与验证链

### 3.1 证据链（排除法）

| 层面 | 验证 | 结论 |
|---|---|---|
| APK 打包 | 两个 arm64 APK（debug/release，15:38 构建）均含 `lib/arm64-v8a/libsocket.so`、`assets/lua/socket.lua`、`assets/lua/socket/*.lua` | 完整 |
| APK↔源码一致性 | APK 内 libsocket.so/socket.lua 与 prebuilt/jniLibs/资产 MD5 全等 | 一致 |
| .so 可加载性 | NEEDED={libluajava,libc,libm,libdl}；50 个未定义 lua_* 符号全部可解析；导出 luaopen_socket_core；loadlib.c 用 RTLD_NOW | 无链接缺口 |
| require 解析路径 | `require("socket.core")` → searcher_Lua(无) → searcher_C(libsocket/core.so 无) → **searcher_Croot**（loadlib.c:499）按根名 "socket" 命中 libsocket.so → dlsym luaopen_socket_core；与 AndroLua Pro 同机制 | 机制正确 |
| 原生源码 | 构建脚本把 AndroLua Pro jni 全树拷贝+delua Android.mk 覆盖+单一 zip 补丁，Lua/loadlib.c/luajava.c/socket.c 与 AndroLua Pro 同源 | 无差异 |
| **资产解包** | **见 3.2 —— 差异所在** | **缺陷定案** |

### 3.2 缺陷机理

delua `androlua/src/main/java/com/androlua/LuaApplication.java`（修复前）：

```java
String[] files = getAssets().list("lua");        // 含 "socket" 目录项
for (String name : files) {
    File outFile = new File(luaMdDir, name);
    if (outFile.exists()) continue;
    try (InputStream in = getAssets().open("lua/" + name);  // "lua/socket" 是目录 → IOException
         ...) { ... }
}                                                  // try-catch 在循环外 → 一旦抛错，其余全部跳过
String[] socketFiles = getAssets().list("lua/socket");  // 永远执行不到
```

- `AssetManager.list("lua")` 返回**文件与目录**的混合列表，"socket" 目录必然在列；
- `getAssets().open("lua/socket")` 打开目录必然抛 IOException；
- 异常被循环外 catch 吞掉 → **"socket" 之后的全部条目不再解包**；
- 实际列表按字母序，"socket" < "socket.lua"（短前缀在前）→ `socket.lua` 及其后文件全部缺失；
- 而 json.lua、ltn12.lua、base64.lua、mime.lua 等字母序在前的脚本已成功解包 → 探针中 json/ltn12 等正常，唯独 socket 失败 —— 与日志观察完全吻合；
- cjson 为纯 C 模块走 cpath 加载，与资产解包无关 → 正常。

对照：AndroLua Pro 通过 `Welcome.unApk()`/`LuaActivity.unZipAssets()` 以 **ZIP 条目遍历 + `zipEntry.isDirectory()` 判断** 解包（LuaActivity.java:635-682），目录被正确跳过，因此其设备上 socket.lua 完好。

## 4. 修复方案（已实施）

文件：`androlua/src/main/java/com/androlua/LuaApplication.java`

将硬编码双循环的 `extractLuaScripts()` 替换为递归目录遍历：

- 按字母序以外无法保证的目录项：以 `name.indexOf('.') == -1` 判定为目录并递归进入（assets/lua 下目录均无扩展名，文件均有扩展名）；
- try-catch 移至**单文件内**：单个资产失败不再中断其余资产；
- 递归天然覆盖 `socket/` 等子目录，删除原先硬编码的 socket 分支；
- 保留 `if (outFile.exists()) continue` 的既有语义（已解包文件不覆盖）。

```java
private void extractLuaScripts() {
    extractLuaDir("lua", new File(luaMdDir));
}

private void extractLuaDir(String assetDir, File destDir) {
    try {
        String[] names = getAssets().list(assetDir);
        if (names == null) return;
        if (!destDir.exists() && !destDir.mkdirs()) return;
        for (String name : names) {
            try {
                File outFile = new File(destDir, name);
                if (name.indexOf('.') == -1) {
                    extractLuaDir(assetDir + "/" + name, outFile);
                    continue;
                }
                if (outFile.exists()) continue;
                try (InputStream in = getAssets().open(assetDir + "/" + name);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                // 单个资产失败不中断其余资产
            }
        }
    } catch (Exception e) {
        // 提取失败不阻塞启动
    }
}
```

验证：`gradlew :androlua:compileDebugJavaWithJavac` → **BUILD SUCCESSFUL**。

## 5. 优化建议（后续）

1. **解包版本化**：当前 `if (outFile.exists()) continue` 使 app 升级后旧资产永不刷新。建议引入提取版本标记（如 SharedPreferences 存资产清单 MD5），版本不符时整目录重建；至少对已存在但长度不符的文件强制覆盖。
2. **模块预注册（可选）**：可在引擎初始化时对全部 C 模块执行 `luaL_requiref`（package.preload），与 cpath 双保险，消除对运行时 dlopen 的依赖，向 AndroLua Pro 的"all-in-one loader"风格靠拢。
3. **回归测试**：在探针中新增对 `require("xml")`、`require("mime")` 等包装型模块的检查（同样依赖解包），防止此类问题回归。
4. **启动自检**：首次启动后校验 app_lua 关键资产存在性，缺失时告警并重新解包。

## 6. 验证计划

1. 重新构建安装：`gradlew :app:assembleArm64Debug`（或直接重装现有 APK 并清除应用数据触发重新解包）。
2. 重跑探针：预期 `socket: LuaSocket 3.0-rc1` [OK]、扩展库-网络 40%、**总分 879/956，与 AndroLua Pro 完全持平**。
3. 抽查 `su.lua`/`test.lua`/`xml.lua` 同步落盘（本次修复同时解包了此前被中断的资产）。

## 附：关键文件与行号

- 缺陷代码：`androlua/src/main/java/com/androlua/LuaApplication.java:195-231`（已修复）
- 对照实现：`docs/AndroLua_pro-master/app/src/main/java/com/androlua/LuaActivity.java:635-682`（unZipAssets）
- 解析机制：`docs/AndroLua_pro-master/app/src/main/jni/lua/loadlib.c:499-508`（searcher_Croot）、`:157`（dlopen RTLD_NOW）
- 探针判定：`docs/project/test/main.lua:1851-1872`（extlib_net，1 秒伪超时）
- 构建管线：`scripts/rebuild_engine.ps1`（jni 全树拷贝 + Android.mk 覆盖 + patches 覆盖）
