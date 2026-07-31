# delua × AndroLua 原生引擎集成方案

> **文档编号**: DELUA-ARCH-002  
> **版本**: 1.0  
> **编制日期**: 2026-07-31  

---

## 目录

1. [审计结论](#1-审计结论)
2. [目标架构](#2-目标架构)
3. [整改步骤](#3-整改步骤)
4. [文件清单](#4-文件清单)
5. [回退方案](#5-回退方案)
6. [验收标准](#6-验收标准)

---

## 1. 审计结论

### 1.1 /docs 下三个 AndroLua 参考分析

| 目录 | 来源 | 版本 | 状态 |
|------|------|------|------|
| `AndroLua-master/` | mkottman/AndroLua（原始版） | v1.0 (2011) | 原始 MVP，单 Activity + REPL，无项目系统，无 LuaActivity。**不可用** |
| **`AndroLua_pro-master/`** | **nirenr/AndroLuaPro（官方版）** | **v4.4.2** | **完整 MIT 源码，全功能项目运行环境。✅ 官方纯净** |
| `AndroLuaX-main/` | Jesse205 反编译魔改版 | 5.0.18 (预编译 JAR) | 反编译 AndroLua+ 后加 AndroidX，核心引擎仅为预编译 JAR 无源码。❌ 魔改 |

**结论**：使用 `AndroLua_pro-master/` 作为官方纯净参考源。

### 1.2 当前 delua 架构问题

当前执行路径：

```
Kotlin EngineActivity (239行, 反射调用 LuaState)
  → System.loadLibrary("luajava.so")
  → InMemoryDexClassLoader(engine.dex)
  → 反射: LuaState.openLibs(), pushJavaObject(), setGlobal(), setPackagePath(), doFile()
```

问题：
1. `engine.dex` **缺少 LuaActivity**（只有 LuaApplication/LuaContext/LuaState 等基础类）
2. `EngineActivity.kt` 用 Kotlin + 反射手写本该由 LuaActivity 完成的初始化逻辑
3. 维护成本高：LuaActivity 有 ~1500 行成熟逻辑，Kotlin 版只实现了子集
4. 不符合"Kotlin 管宿主、Java 管引擎"的架构分离原则

---

## 2. 目标架构

```
┌─────────────────────────────────────────────────┐
│  Kotlin 宿主 (IDE)                               │
│  ┌─────────────────────────────────────────┐    │
│  │  Compose UI (MainActivity, RunScreen…)  │    │
│  └────────────────────┬────────────────────┘    │
│  ┌────────────────────┴────────────────────┐    │
│  │  EngineBridge (Kotlin, ~50 lines)       │    │
│  │  createEngineIntent() → Intent          │    │
│  │  collectResult() → read JSON            │    │
│  └────────────────────┬────────────────────┘    │
└───────────────────────┼─────────────────────────┘
                        │ Intent
┌───────────────────────┴─────────────────────────┐
│  AndroLua 原生引擎 (纯 Java + C + Lua)          │
│                                                  │
│  EngineActivity.java (extends LuaActivity)       │
│    ├── getLuaPath() → projectDir from Intent     │
│    └── onDestroy() → write engine_result.json    │
│                                                  │
│  LuaActivity.java (~1500 lines, from AndroLuaPro)│
│    ├── initLua() → LuaStateFactory.newLuaState() │
│    ├── openLibs(), setGlobal, setPackagePath     │
│    ├── doFile("init.lua"), doFile("main.lua")    │
│    └── 生命周期回调 → Lua 函数                   │
│                                                  │
│  com.luajava.* (Java JNI 桥接)                   │
│  com.androlua.* (LuaDialog, LuaThread, etc.)     │
│  com.android.cglib.* (代理/增强)                 │
│  libluajava.so / libgl.so / ... (C 原生代码)     │
│  import.lua / loadlayout.lua / ... (Lua 模块)    │
└──────────────────────────────────────────────────┘
```

**变化要点**：
- `EngineActivity` 从 Kotlin 改为 Java，直接继承 LuaActivity
- `LuaActivity` 等 AndroLua 运行时类从静态 Java 源码编译（而非从 DEX 动态加载）
- 移除 `engine.dex` 动态加载（保留为备用路径）
- Kotlin 仅保留 EngineBridge 做 Intent 创建和结果收集

---

## 3. 整改步骤

### Phase 1：导入 AndroLuaPro 官方源码到 `androlua` 模块

**操作**：从 `docs/AndroLua_pro-master/app/src/main/java/` 复制以下包到 `androlua/src/main/java/`

| 包 | 必需度 | 说明 |
|---|--------|------|
| `com.luajava.*` | P0 | LuaState、LuaObject、LuaJavaAPI 等 JNI 桥接核心 |
| `com.androlua.*` (运行时) | P0 | LuaActivity、LuaApplication、LuaContext、LuaDexLoader、LuaPrint、LuaDialog、LuaThread、LuaTimer、LuaAsyncTask、LuaAdapter、LuaEnhancer、LuaGcable、LuaUtil、LuaBitmap、LuaBroadcastReceiver、CrashHandler、Ticker、LuaRunnable、LuaTimerTask、LuaResources 等 |
| `com.androlua.*` (视图中介) | P1 | LuaAdapter、LuaArrayAdapter、LuaExAdapter、LuaMultiAdapter、LuaExpandableListAdapter |
| `com.android.cglib.*` | P0 | Proxy、Enhancer（LuaEnhancer 依赖） |
| `com.android.cglib.dx.*` | P1 | DexMaker（动态 DEX 生成，LuaDexLoader 依赖） |

**不导入**：
- `com.myopicmobile.textwarrior.*`（编辑器组件，delua 用 sora-editor）
- `com.androlua.LuaEditor`、`LuaLexer`、`LuaTokenTypes`（同上）
- `com.nirenr.*`（截图、取色等工具，delua 不需要）
- `android.widget.*`（AndroLuaPro 自定义视图，delua 不需要）

### Phase 2：创建 EngineActivity.java

新建 `app/src/main/java/com/delua/engine/EngineActivity.java`：

```java
package com.delua.engine;

import com.androlua.LuaActivity;
import com.androlua.LuaApplication;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

public class EngineActivity extends LuaActivity {

    private String projectDir;
    private String mainFile;
    private long startTime;
    private boolean hasContentView = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        projectDir = intent.getStringExtra("projectDir");
        mainFile = intent.getStringExtra("mainFile");
        if (mainFile == null) mainFile = "main.lua";
        startTime = System.currentTimeMillis();

        // LuaActivity.onCreate() 会调用 getLuaPath() 获取路径
        // 然后执行 init.lua + main.lua
        super.onCreate(savedInstanceState);
    }

    @Override
    public String getLuaPath() {
        return projectDir + "/" + mainFile;
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        hasContentView = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        writeResult();
    }

    private void writeResult() {
        long duration = System.currentTimeMillis() - startTime;
        String error = getScriptError();
        String json = String.format(
            "{\"success\":%b,\"hasContentView\":%b,\"durationMs\":%d,\"timestamp\":\"%s\"%s}",
            error == null, hasContentView, duration,
            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .format(new java.util.Date()),
            error != null ? ",\"errorMessage\":\"" + escapeJson(error) + "\"" : ""
        );
        try {
            FileOutputStream fos = new FileOutputStream(
                new File(getCacheDir(), "engine_result.json"));
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            // 写文件失败不阻塞销毁
        }
    }

    private String getScriptError() { return null; /* LuaActivity 需提供此方法 */ }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

### Phase 3：替换 LuaApplication 为 Java 版

`DeluaApp.kt` 保持不变（仍 extends LuaApplication），但 `LuaApplication` 现在指向从 AndroLuaPro 导入的 Java 类而非 Kotlin 版。

删除旧的 Kotlin 实现：
- ❌ `androlua/src/main/kotlin/com/delua/androlua/LuaApplication.kt`
- ❌ `androlua/src/main/kotlin/com/delua/androlua/LuaContext.kt`

### Phase 4：移除 EngineActivity.kt

删除：
- ❌ `app/src/main/kotlin/com/delua/engine/EngineActivity.kt`
- ❌ `app/src/main/kotlin/com/delua/engine/`（如果仅此文件）

### Phase 5：精简 EngineBridge.kt

不再需要加载 `engine.dex`。简化为：

```kotlin
// androlua/src/main/kotlin/com/delua/androlua/engine/EngineBridge.kt
class EngineBridge(private val context: Context) {
    companion object {
        const val EXTRA_PROJECT_DIR = "projectDir"
        const val EXTRA_MAIN_FILE = "mainFile"
        private const val RESULT_FILE = "engine_result.json"
    }

    fun createEngineIntent(
        projectDir: String,
        mainFile: String = "main.lua"
    ): Intent {
        return Intent(context, EngineActivity::class.java).apply {
            putExtra(EXTRA_PROJECT_DIR, projectDir)
            putExtra(EXTRA_MAIN_FILE, mainFile)
        }
    }

    fun collectResult(): EngineResult? {
        val resultFile = File(context.cacheDir, RESULT_FILE)
        if (!resultFile.exists()) return null
        return try {
            val json = resultFile.readText()
            resultFile.delete()
            EngineResult.fromJson(json)
        } catch (e: Exception) { null }
    }
}
```

### Phase 6：移除（或保留）engine.dex 动态加载

`prebuilt/engine.dex` 不再用于主执行路径。两种选择：
- **保留**：作为"打包 APK"或"插件系统"的基础
- **移除**：完全静态编译，简化架构

建议**保留**，因为 WP-004（打包 APK）和插件系统未来可能用到。

### Phase 7：原生库构建

目前 `build-scripts/jni/` 仅有 lua、luajava、luagl 三个模块。AndroLuaPro 有更多：

| 模块 | 工程依赖 | 建议 |
|------|---------|------|
| lua | 全部 | ✅ 已就绪 |
| luajava | 全部 | ✅ 已就绪 |
| luagl | kleinbottle | ✅ 已就绪 |
| luasocket/http/ftp/smtp | demo（HTTP请求） | ⚠️ demo 用 luajava 的 Java 网络也可行 |
| lua-cjson | demo（JSON解析） | ⚠️ Lua 侧已有 dkjson.lua |
| 其他（bson/crypt/zip等） | 项目未用 | ⏸ 按需添加 |

**建议**：首批仅保持现有三个 native 模块，待测试工程验证通过后再按需补齐。

---

## 4. 文件清单

### 4.1 新增文件

| 文件 | 来源 | 说明 |
|------|------|------|
| `androlua/src/main/java/com/luajava/*.java` | AndroLua_pro-master | 约 20 个文件 |
| `androlua/src/main/java/com/androlua/*.java`（运行时类） | AndroLua_pro-master | 约 30 个文件 |
| `androlua/src/main/java/com/android/cglib/*.java` | AndroLua_pro-master | 约 65 个文件 |
| `app/src/main/java/com/delua/engine/EngineActivity.java` | 新建 | 薄 Java 层 |

### 4.2 删除文件

| 文件 | 原因 |
|------|------|
| `app/src/main/kotlin/com/delua/engine/EngineActivity.kt` | 被 Java EngineActivity 替代 |
| `androlua/src/main/kotlin/com/delua/androlua/LuaApplication.kt` | 被 Java LuaApplication 替代 |
| `androlua/src/main/kotlin/com/delua/androlua/LuaContext.kt` | 被 Java LuaContext 替代 |

### 4.3 修改文件

| 文件 | 变更 |
|------|------|
| `androlua/src/main/kotlin/com/delua/androlua/engine/EngineBridge.kt` | 移除 engine.dex 加载，简化 |
| `androlua/build.gradle.kts` | 确认 Java 源码编译 |
| `androlua/src/main/assets/` | 加入 AndroLua 标准 Lua 模块（import.lua 等） |
| `app/src/main/AndroidManifest.xml` | 无需修改（EngineActivity 类名不变） |

### 4.4 保留文件（无需修改）

| 文件 | 说明 |
|------|------|
| `app/src/main/kotlin/com/delua/DeluaApp.kt` | 继续 extends LuaApplication |
| `app/src/main/kotlin/com/delua/ui/screen/RunScreen.kt` | 使用 EngineBridge 不变 |
| `prebuilt/engine.dex` | 保留备用 |
| `prebuilt/arm64-v8a/libluajava.so` | 原生库 |
| `prebuilt/armeabi-v7a/libluajava.so` | 原生库 |
| `prebuilt/lua/*.lua` | Lua 模块库（与 AndroLua 标准版对齐） |
| `build-scripts/jni/` | 原生代码构建 |

---

## 5. 回退方案

若迁移失败，可回退到当前架构：

1. 还原 `EngineActivity.kt`（git checkout）
2. 还原 `LuaApplication.kt`、`LuaContext.kt`
3. 还原 `EngineBridge.kt`
4. 确保 `engine.dex` 在 assets 中可用

**决策时间点**：Phase 5 完成后运行三个测试工程验证，任一失败则评估是否需要回退。

---

## 6. 验收标准

| 编号 | 检查项 | 验证方法 |
|------|--------|---------|
| AC-01 | demo 工程运行，显示 ListView | 编译运行，选 demo 点击 Run |
| AC-02 | testjson 工程运行，显示 JSON 内容 | 同上 |
| AC-03 | kleinbottle 工程运行，显示 3D 渲染 | 同上 |
| AC-04 | EngineActivity 无 Kotlin 依赖 | `javap -p com.delua.engine.EngineActivity` 确认 |
| AC-05 | LuaActivity 等运行时类为 Java 源码 | 确认 `androlua/src/main/java/` 下无 Kotlin |
| AC-06 | engine.dex 不再在主执行路径中使用 | 确认 EngineBridge 无 loadEngine() 调用 |
| AC-07 | 脚本错误时正确回传 JSON | 运行错误脚本，检查结果文件 |
| AC-08 | `setContentView` 脚本保持界面不自动关闭 | 运行 demo 确认 ListView 保持 |
