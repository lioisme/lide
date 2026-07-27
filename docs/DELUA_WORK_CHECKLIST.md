# delua 工程 — 全量迁移工作清单与执行手册

> **项目**：AndroLuaX (Java + Lua) → delua (Kotlin + Lua)
> **版本**：v2.0 · 2026-07-28
> **标准**：本文件按 Google Design Doc / Apple Engineering Proposal 质量标准编制
> **前置文档**：`docs/DELUA_PROJECT_PLAN.md`（架构规划）、`docs/ANDROLUA_AUDIT_REPORT.md`（审计基线）

---

## 目录

1. [阶段总览](#1-阶段总览)
2. [Phase 0 — 源码解放](#2-phase-0--源码解放)
3. [Phase 1 — 构建系统迁移](#3-phase-1--构建系统迁移)
4. [Phase 2 — JNI 绑定层翻译（P0，最高风险）](#4-phase-2--jni-绑定层翻译p0最高风险)
5. [Phase 3 — 运行时引擎翻译（P0）](#5-phase-3--运行时引擎翻译p0)
6. [Phase 4 — 宿主 IDE 与 UI 翻译（P1）](#6-phase-4--宿主-ide-与-ui-翻译p1)
7. [Phase 5 — 自定义小部件翻译（P2）](#7-phase-5--自定义小部件翻译p2)
8. [Phase 6 — 工具与网络翻译（P1-P2）](#8-phase-6--工具与网络翻译p1-p2)
9. [Phase 7 — TextWarrior 编辑器框架翻译（P1）](#9-phase-7--textwarrior-编辑器框架翻译p1)
10. [Phase 8 — 收尾与集成](#10-phase-8--收尾与集成)
11. [技术注意事项（核心）](#11-技术注意事项核心)
12. [附录：文件映射表](#12-附录文件映射表)

---

## 1. 阶段总览

```
Phase 0 [源码解放]     复制源码 + 删除 JAR    → Java 全开源版本
     ↓
Phase 1 [构建系统]      引入 Kotlin + Gradle KTS  → 编译框架就绪
     ↓
Phase 2 [JNI 绑定]      com.luajava.* → Kotlin → 核心 JNI 验证
     ↓
Phase 3 [运行时引擎]      LuaActivity/Application/Service → Kotlin
     ↓
Phase 4 [宿主 UI]        Main/Welcome/Editor/Dialog → Kotlin
     ↓
Phase 5 [自定义 Widget]  android.widget.* 等 → Kotlin
     ↓
Phase 6 [工具与网络]      util/Http/Tcp 等 → Kotlin
     ↓
Phase 7 [TextWarrior]    编辑器核心框架 → Kotlin
     ↓
Phase 8 [收尾]           集成测试 → 性能验收 → 发布
```

**里程碑 gate：每个 Phase 结束时必须有可编译、可运行的版本。**

---

## 2. Phase 0 — 源码解放

### 目标

将 AndroLuaX 从「JAR 闭源 + 3 个 Java 文件」转变为「136+ 个 Java 源文件的全开源项目」。

### 2.1 复制 AndroLuaPro 源码

从 `docs/AndroLua_pro-master/app/src/main/java/` 复制以下包到 `androlua/src/main/java/`：

| 源路径 | 目标路径 | 文件数 | 备注 |
|--------|----------|--------|------|
| `com/androlua/` | `androlua/src/main/java/com/androlua/` | ~55 | 核心引擎 |
| `com/androlua/util/` | `androlua/src/main/java/com/androlua/util/` | 10 | 工具类 |
| `com/luajava/` | `androlua/src/main/java/com/luajava/` | 16 | JNI 绑定 |
| `com/nirenr/` | `androlua/src/main/java/com/nirenr/` | 5 + 4 | 颜色/截屏 |
| `com/myopicmobile/textwarrior/common/` | `androlua/src/main/java/com/myopicmobile/textwarrior/common/` | 24 | 编辑器通用 |
| `com/myopicmobile/textwarrior/android/` | `androlua/src/main/java/com/myopicmobile/textwarrior/android/` | 9 | 编辑器 Android |
| `android/widget/` | `androlua/src/main/java/android/widget/` | 24 | 自定义 Widget |
| `android/app/` | `androlua/src/main/java/android/app/` | 2 | AlertDialogBuilder, FloatWindow |
| `android/content/` | `androlua/src/main/java/android/content/` | 1 | FileProvider |
| `android/support/` | `androlua/src/main/java/android/support/` | 6 | MultiDex, Fragment 等 |

### 2.2 复制 AndroLuaX 独有源码

从 `docs/AndroLuaX-main/` 复制 AndroLuaPro 中没有的文件：

- `app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.java` → `app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.java`
- `androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.java` → `androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.java`

### 2.3 删除 JAR 依赖

- 删除 `androlua/libs/AndroLua+5.0.18_AndroidX.jar`
- 更新 `androlua/build.gradle`：移除 `api fileTree(dir: 'libs', include: ['*.jar'])` （如无其他 JAR 则整行删除）
- 将 `app/build.gradle` 中的 `api fileTree(dir: "libs", include: ["*.jar"])` 改为明确列出需要的 JAR：
  ```groovy
  implementation files('libs/glide-full-4.7.0(AndroidX).jar')
  implementation files('libs/LuaCustRecyclerAdapter.jar')
  ```
  > 保留 `bsh-*.jar` 和 `dexmaker-*.jar` 为 Java 依赖，**不翻译**。

### 2.4 验证：纯 Java 编译

```bash
./gradlew :androlua:compileDebugSources
./gradlew :app:compileDebugSources
```

> **Gate**：编译零错误。如有缺失的引用类，从 `docs/decompiled/` 的反编译 `.class` 中提取补充。

### 2.5 处理混淆包遗留问题

审计 `docs/decompiled/` 中未被 AndroLuaPro 覆盖的混淆类：

| 混淆包 | 风险 | 处理 |
|--------|------|------|
| `a/a/a/a/a.class` | 空标记类，安全 | **删除** |
| `c/a/a/*` (~35 类) | 功能未知 | 用 CFR/Jadx 反编译，如为混淆的序列化/日志等工具，翻译后合并 |
| `b/b/a/*` | 已由 TextWarrior 覆盖 | **删除** |
| `b/b/b/*` | 待定 | 反编译分析后决定 |

---

## 3. Phase 1 — 构建系统迁移

### 3.1 引入 Kotlin 插件

**文件**：`build.gradle`（项目级）
```groovy
plugins {
    id 'com.android.application' version '8.1.1' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
```

**文件**：`androlua/build.gradle`
```groovy
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
}
```
添加：
```groovy
android {
    // ... existing config ...
    buildFeatures {
        prefab true
    }
}
dependencies {
    // 保留原有 AndroidX 依赖
    // 移除 api fileTree(dir: 'libs', include: ['*.jar'])
    implementation kotlin('stdlib')
}
```

**文件**：`app/build.gradle`
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
```
添加：
```groovy
dependencies {
    implementation kotlin('stdlib')
    implementation kotlin('reflect')  // 用于 kotlin-reflect
}
```

### 3.2 Gradle KTS 方案（可选）

如需迁移到 `.kts`，需同时转换：
- `build.gradle` (project) → `build.gradle.kts`
- `app/build.gradle` → `app/build.gradle.kts`
- `androlua/build.gradle` → `androlua/build.gradle.kts`
- `settings.gradle` → `settings.gradle.kts`

```
⚠️ Gradle KTS 迁移和 Kotlin 源文件翻译可以解耦。
建议：先保留 Groovy DSL，只加 Kotlin 插件，等所有代码翻译完成后再迁移 KTS。
```

### 3.3 验证

```bash
./gradlew :androlua:compileDebugKotlin
./gradlew :app:compileDebugKotlin
```

> **Gate**：Kotlin 编译零错误。此时项目应仍为纯 Java，Kotlin 编译应跳过（0 个 .kt 文件）。

---

## 4. Phase 2 — JNI 绑定层翻译（P0，最高风险）

### 4.1 翻译文件清单

**优先级**：P0（阻塞所有下游）

| Java 文件 | Kotlin 文件 | 行数 (est.) | 风险等级 |
|-----------|-------------|-------------|----------|
| `LuaState.java` | `LuaState.kt` | ~1277 | 🔴 **关键** |
| `LuaJavaAPI.java` | `LuaJavaAPI.kt` | ~1400+ | 🔴 **关键** |
| `LuaStateFactory.java` | `LuaStateFactory.kt` | ~100 | 🟡 中 |
| `LuaObject.java` | `LuaObject.kt` | ~300 | 🟡 中 |
| `LuaTable.java` | `LuaTable.kt` | ~200 | 🟡 中 |
| `LuaFunction.java` | `LuaFunction.kt` | ~100 | 🟢 低 |
| `LuaList.java` | `LuaList.kt` | ~80 | 🟢 低 |
| `LuaStack.java` | `LuaStack.kt` | ~80 | 🟢 低 |
| `LuaString.java` | `LuaString.kt` | ~80 | 🟢 低 |
| `LuaInvocationHandler.java` | `LuaInvocationHandler.kt` | ~80 | 🟡 中 |
| `LuaMethodInterceptor.java` | `LuaMethodInterceptor.kt` | ~60 | 🟢 低 |
| `LuaAbstractMethodInterceptor.java` | `LuaAbstractMethodInterceptor.kt` | ~40 | 🟢 低 |
| `LuaMetaTable.java` | `LuaMetaTable.kt` | ~20 | 🟢 低 |
| `LuaException.java` | `LuaException.kt` | ~20 | 🟢 低 |
| `JavaFunction.java` | `JavaFunction.kt` | ~120 | 🟢 低 |
| `Console.java` | `Console.kt` | ~60 | 🟢 低 |

### 4.2 JNI 翻译规范（必须遵守）

#### 4.2.1 `native` → `external` 映射规则

```kotlin
// Java 原始代码
private synchronized native long _newstate();
```

```kotlin
// Kotlin 翻译 — 保持成员函数（非 companion object）以确保 JNI 符号一致
class LuaState {
    @Synchronized
    private external fun _newstate(): Long
}
```

**JNI 符号一致性说明：**

| 声明位置 | JNI 符号 | 与 Java 版对比 |
|----------|----------|----------------|
| `private external fun` (class 成员) | `Java_com_luajava_LuaState__newstate` | ✅ **一致** |
| `companion object { external fun }` | `Java_com_luajava_LuaState_Companion__newstate` | ❌ **不一致，会崩溃** |
| `companion object { @JvmStatic external fun }` | 目标符号为 `Java_com_luajava_LuaState_xxx`，但编译器行为随版本变化 | ⚠️ **不可靠** |

**规则：**
1. **非静态 `native` 方法** → `private external fun` 作为类成员，用 `@Synchronized` 替换 `synchronized`
2. **静态 `native` 方法** → 放在 `companion object` 中，用 `@JvmStatic external fun`，**必须用 `javap -s` 验证 JNI 符号**
3. 不要使用 Kotlin 默认参数（会生成桥接重载，改变符号）
4. `final static int` 常量 → `const val` 放在 `companion object` 或顶层

#### 4.2.2 `LuaJavaAPI` 静态方法翻译（特例）

`LuaJavaAPI` 是纯静态工具类，其 `public static native xxx()` 方法被 Lua C 代码通过 JNI 直接调用。

```kotlin
// Java: public static int newArray(long luaState, Class<?> clazz, int size)
// Kotlin:
class LuaJavaAPI {
    companion object {
        @JvmStatic
        external fun newArray(luaState: Long, clazz: Class<*>, size: Int): Int
    }
}
```

**验证命令：**
```bash
# Java 版本的 JNI 符号
javap -s -p com/luajava/LuaJavaAPI.class
# Kotlin 版本编译后的 JNI 符号
javap -s -p com/luajava/LuaJavaAPIKt.class   # 顶层函数
javap -s -p com/luajava/LuaJavaAPI.class      # companion object 函数
```
> 必须确保两条命令输出的符号一致，否则在 `libluajava.so` 加载时会 `UnsatisfiedLinkError`。

#### 4.2.3 `LuaState` 成员翻译模板

```kotlin
package com.luajava

class LuaState {
    companion object {
        const val LUAI_MAXSTACK = 1000000
        const val LUA_REGISTRYINDEX = -LUAI_MAXSTACK - 1000
        // ... 其他常量
    }

    private var luaState: Long = 0
    private var mContext: com.androlua.LuaContext? = null

    // native 方法 — 保持为 private external 成员函数
    @Synchronized
    private external fun _newstate(): Long

    @Synchronized
    private external fun _close(ptr: Long)

    // ... 其余 ~100 个 native 方法同理

    // 公有 API 包装 — 注意 Kotlin 的 null 安全
    fun pushString(str: String?) {
        if (str == null) _pushNil(luaState)
        else _pushString(luaState, str)
    }

    // getter/setter → Kotlin 属性
    var pointer: Long get() = luaState
}
```

### 4.3 JNI 验证测试

```kotlin
// 文件：app/src/test/java/com/luajava/LuaStateTest.kt
class LuaStateTest {
    @Test
    fun `test native library loads`() {
        // 应不抛 UnsatisfiedLinkError
        assertDoesNotThrow { System.loadLibrary("luajava") }
    }
    
    @Test
    fun `test create and close state`() {
        val state = LuaState()
        assertFalse(state.isClosed)
        state.close()
        assertTrue(state.isClosed)
    }
    
    @Test
    fun `test jni symbol match`() {
        // 用 javap -s 对比 Java/Kotlin 的 JNI 符号
        // 在 CI 脚本中自动执行
    }
}
```

> **Gate**：JNI 绑定层翻译后，编写一个 `LuaState` 创建/关闭的集成测试，在真机或模拟器上运行通过。

---

## 5. Phase 3 — 运行时引擎翻译（P0）

### 5.1 翻译文件清单

| Java 文件 | Kotlin 文件 | 风险 | 备注 |
|-----------|-------------|------|------|
| `LuaContext.java` | `LuaContext.kt` | 🟢 低 | 纯接口，直接映射 |
| `LuaApplication.java` | `LuaApplication.kt` | 🟡 中 | Application 子类，单例 |
| `LuaActivity.java` | `LuaActivity.kt` | 🔴 **高** | ~100 方法，Lua 运行时核心 |
| `LuaActivityX.java` | `LuaActivityX.kt` | 🟡 中 | 继承 LuaActivity |
| `LuaService.java` | `LuaService.kt` | 🟡 中 | Service 生命周期 |
| `LuaAccessibilityService.java` | `LuaAccessibilityService.kt` | 🟡 中 | 无障碍服务 |
| `CrashHandler.java` | `CrashHandler.kt` | 🟢 低 | 单例异常处理器 |
| `LuaDexLoader.java` | `LuaDexLoader.kt` | 🟡 中 | DEX 加载管理 |
| `LuaDexClassLoader.java` | `LuaDexClassLoader.kt` | 🟢 低 | 内部类 |
| `LuaAssetLoader.java` | `LuaAssetLoader.kt` | 🟢 低 | Asset 加载 |
| `LuaEnhancer.java` | `LuaEnhancer.kt` | 🟡 中 | DexMaker 桥接 |

### 5.2 `LuaContext` 接口翻译

```kotlin
package com.androlua

interface LuaContext {
    val classLoaders: ArrayList<ClassLoader>
    
    fun call(func: String, vararg args: Any?)
    fun set(name: String, value: Any?)
    
    val luaPath: String
    fun getLuaPath(path: String): String
    fun getLuaPath(dir: String, name: String): String
    
    val luaDir: String
    fun getLuaDir(dir: String): String
    
    val luaExtDir: String
    fun getLuaExtDir(dir: String): String
    var luaExtDir: String  // setter
    
    fun getLuaExtPath(path: String): String
    fun getLuaExtPath(dir: String, name: String): String
    
    val luaLpath: String
    val luaCpath: String
    
    val context: Context
    val luaState: LuaState
    
    fun doFile(path: String, vararg arg: Any?): Any?
    fun sendMsg(msg: String)
    fun sendError(title: String, msg: Exception)
    
    val width: Int
    val height: Int
    
    val globalData: Map<*, *>
    fun getSharedData(key: String): Any?
    fun getSharedData(key: String, def: Any?): Any?
    fun setSharedData(key: String, value: Any?): Boolean
    
    fun regGc(obj: LuaGcable)
}
```

> **注意**：Kotlin 不允许接口属性与 Java 的 `getXxx()/setXxx()` 方法命名冲突。需要在 `@JvmName` 注解或重命名中仔细处理。

### 5.3 `LuaActivity` 翻译重点

```
Java→Kotlin 的关键转换点：
─────────────────────────────────────────────
Handler + Looper         → Handler(Looper.getMainLooper())
   或 CoroutineScope(Dispatchers.Main)
    
onActivityResult         → ActivityResultContracts
    
匿名内部类                → SAM 转换 / 对象表达式
    
AsyncTask                → Kotlin Coroutines 或 Thread
    
getLuaDir/setLuaDir      → var luaDir: String 属性

finalize()               → 不重写 finalize()
                             改用 Cleaner 或 Closeable
```

### 5.4 运行时翻译特殊注意事项

1. **`LuaActivity.MainHandler`**：内部 Handler 类，在 Kotlin 中用匿名对象表达式或内部类
2. **`LuaActivity` 静态 Map `H`**：`HashMap<String, LuaActivity>` → `companion object { val instances = mutableMapOf<String, LuaActivity>() }`
3. **Lua 调用路径**：`runFunc()` → `luaState.pcall()` 等 JNI 调用，翻译后必须保持参数顺序一致
4. **`onActivityResult` 弃用**：AndroidX Activity 1.8+ 引入 `ActivityResultContracts`。考虑到 minSdk=14/21，建议：
   - 保留 `onActivityResult` 覆盖（弃用但可用）
   - 或迁移到 `registerForActivityResult()`（推荐，但需 minSdk=21）

> **Gate**：`LuaApplication` 和 `LuaActivity` 翻译后，一个最基本的 "Hello World" Lua 脚本能在 App 中运行。

---

## 6. Phase 4 — 宿主 IDE 与 UI 翻译（P1）

### 6.1 翻译文件清单

| Java 文件 | Kotlin 文件 | 风险 | 备注 |
|-----------|-------------|------|------|
| `Main.java` | `Main.kt` | 🔴 **高** | 主 Activity，IDE 入口 |
| `Welcome.java` | `Welcome.kt` | 🟡 中 | 启动屏 |
| `LuaEditor.java` | `LuaEditor.kt` | 🟡 中 | 编辑器封装层（基类在 TextWarrior） |
| `LuaLexer.java` | `LuaLexer.kt` | 🔴 **高** | 词法分析器，行为必需一致 |
| `LuaTokenTypes.java` | `LuaTokenTypes.kt` | 🟢 低 | 枚举转 enum class |
| `LuaDialog.java` | `LuaDialog.kt` | 🟢 低 | 已有源码 |
| `LuaMaterialDialog.java` | `LuaMaterialDialog.kt` | 🟢 低 | 已有源码 |
| `AlertControllerBridge.java` | `AlertControllerBridge.kt` | 🟢 低 | 已有源码 |

### 6.2 `LuaTokenTypes` 翻译

```kotlin
// Java enum → Kotlin enum class
enum class LuaTokenTypes {
    AND, BREAK, CASE, CONTINUE, DO, ELSE, ELSEIF, END,
    FALSE, FOR, FUNCTION, GOTO, IF, IN, LOCAL, NIL, NOT, OR,
    REPEAT, RETURN, SWITCH, THEN, TRUE, UNTIL, WHEN, WHILE,
    PLUS, MINUS, MULT, DIV, MOD, EXP, CONCAT,
    LT, GT, LE, GE, EQ, NE, ASSIGN,
    BIT_AND, BIT_OR, BIT_TILDE, BIT_LTLT, BIT_RTRT, DOUBLE_DIV,
    ELLIPSIS, DOT, COLON, DOUBLE_COLON, SEMI, COMMA,
    SHORT_COMMENT, BLOCK_COMMENT, DOC_COMMENT,
    SHEBANG, SHEBANG_CONTENT,
    NUMBER, STRING, NAME,
    WHITESPACE, NEWLINE, EOF, BAD_CHARACTER,
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET;
}
```

### 6.3 `LuaLexer` 翻译重点

`LuaLexer` 是 JFlex 风格的词法分析器，核心挑战在于保持 Token 化行为与 Java 版完全一致。

```kotlin
// Java 版签名
public class LuaLexer {
    public LuaLexer(Reader reader) { ... }
    public LuaLexer(CharSequence chars) { ... }
    public LuaTokenTypes advance() throws IOException { ... }
    public String getTokenText() { ... }
    public int getTokenLength() { ... }
    public int getTokenStart() { ... }
    public int getLineNumber() { ... }
}

// Kotlin 翻译要点
// 1. Reader 参数 → 可直接接受 Reader
// 2. CharSequence → Kotlin CharSequence，不改
// 3. advance() 返回枚举 → LuaTokenTypes
// 4. 状态机字段保持 @Synchronized 或 AtomicReference
// 5. 编写黄金测试：对已知输入，Java 版和 Kotlin 版输出完全相同的 Token 流
```

**关键：** 针对 `LuaLexer` 编写**基于快照的等价性测试**：

```kotlin
// 对同一个输入字符串，Java 版 LuaLexer 和 Kotlin 版 LuaLexer
// 应产生完全相同的 Token 序列
@Test
fun `lexer produces identical tokens for test script`() {
    val script = """
        -- comment
        function hello()
            print("hello " .. name)
        end
        hello()
    """.trimIndent()
    
    val javaTokens = JavaLuaLexer(script).tokenize()    // 临时保留 Java 版
    val kotlinTokens = KotlinLuaLexer(script).tokenize() // 新版
    
    assertEquals(javaTokens, kotlinTokens)
}
```

> **Gate**：LuaLexer Token 序列黄金测试全部通过。

---

## 7. Phase 5 — 自定义小部件翻译（P2）

### 7.1 翻译文件清单

| 包 | 文件 | 数量 |
|----|------|------|
| `android.widget.*` | DrawerLayout, SlidingLayout, PullingLayout, PageView, PageLayout, HorizontalListView, ExListView, ToolBar, FloatButton, CardView, CircleImageView, RippleLayout, RippleHelper, RoundRectDrawable, RoundRectDrawableWithShadow, ViewDragHelper | 16 |
| `android.widget.*` (Adapter) | ArrayListAdapter, ArrayPageAdapter, ArrayExpandableListAdapter, BasePageAdapter, PageAdapter | 5 |
| `android.app.*` | AlertDialogBuilder, FloatWindow | 2 |
| `android.content.*` | FileProvider | 1 |
| `android.support.annotation.*` | IntRange, RequiresApi | 2 |
| `android.support.multidex.*` | MultiDex, MultiDexExtractor, ZipUtil | 3 |
| `android.support.v4.app.*` | Fragment | 1 |

### 7.2 翻译策略

**决策原则**：
- **精确翻译**：保持包名 `android.widget.*` 不变，行为完全一致（推荐的初始策略）
- **现代化重构**：仅在确认与原版行为等价后，择机迁移到 AndroidX/Material 标准组件

### 7.3 `PullingLayout` 翻译注意

`PullingLayout` 是下拉刷新/上拉加载布局，约 20 个内部类（HeadView/FootView/动画 Drawable 等）。内部类在 Kotlin 中的处理：

```kotlin
// Java 匿名内部类
private OnRefreshListener mOnRefreshListener;
public void setOnRefreshListener(OnRefreshListener listener) { ... }

// Kotlin — 使用函数类型属性
class PullingLayout : ViewGroup {
    var onRefreshListener: (() -> Unit)? = null
    
    // 内部枚举
    enum class State {
        NORMAL, PULLING, REFRESHING, COMPLETE
    }
}
```

---

## 8. Phase 6 — 工具与网络翻译（P1-P2）

### 8.1 翻译文件清单

| 包 | 文件 | 数量 | 建议策略 |
|----|------|------|----------|
| `com.androlua.util.*` | AsyncTaskX, TimerX, TimerTaskX, ClickRunnable, ScreenMetrics, VolatileBox, VolatileDispose, GlobalActionAutomator, RootUtil, RSASecurity, ZipUtil | 11 | **精确翻译** |
| `com.androlua.LuaUtil` | 工具类 | 1 | 改为顶层扩展函数 |
| `com.androlua.LuaStringUtil` | 字符串工具 | 1 | 改为 String 扩展 |
| `com.androlua.LuaPrint` | 打印输出 | 1 | 简单翻译 |
| `com.androlua.Http` | HTTP 请求 | 1 | **推荐改 OkHttp** |
| `com.androlua.Download` | 文件下载 | 1 | **推荐改 OkHttp** |
| `com.androlua.LuaServer` | TCP Server | 1 | 精确翻译 |
| `com.androlua.LuaClient` | TCP Client | 1 | 精确翻译 |
| `com.androlua.LuaBroadcastReceiver` | 广播 | 1 | 简单翻译 |
| `com.androlua.LuaContentObserver` | 内容观察 | 1 | 简单翻译 |
| `com.androlua.LuaFileObserver` | 文件观察 | 1 | 简单翻译 |
| `com.androlua.LuaGcable` | GC 接口 | 1 | 接口 |

### 8.2 `Http` 改造方案

**原始实现**：基于 `HttpURLConnection`，返回 `HttpTask`（AsyncTask 子类）

**推荐方案**：引入 OkHttp + Kotlin Coroutines，保持 Lua API 兼容

```kotlin
// Lua 侧调用方式不变：
// Http.get("https://...", callback)
// Http.post("https://...", body, callback)

// Kotlin 内部实现：
object Http {
    private val client = OkHttpClient()
    
    // 暴露给 Lua 的 API
    @JvmStatic
    fun get(url: String, callback: HttpCallback? = null): HttpTask {
        val request = Request.Builder().url(url).get().build()
        return execute(request, callback)
    }
    
    // 内部使用 Coroutine
    private fun execute(request: Request, callback: HttpCallback?): HttpTask {
        val task = HttpTask()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                // 回调到主线程
                withContext(Dispatchers.Main) {
                    callback?.onSuccess(response.body?.string())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError(e)
                }
            }
        }
        return task
    }
}
```

### 8.3 并发 API 翻译对照

| Java | Kotlin |
|------|--------|
| `LuaAsyncTask` | 保持 API 兼容，内部用 `CoroutineScope` + `async` |
| `LuaThread` | `Thread` 包装器，内部创建独立 LuaState |
| `LuaTimer` | `Timer` 包装器，保留 `start/stop` API |
| `AsyncTaskX` | 保持不翻译（保留 Java），或用 Coroutine 重写 |
| `TimerX` | 保持不翻译（保留 Java） |

---

## 9. Phase 7 — TextWarrior 编辑器框架翻译（P1）

### 9.1 翻译文件清单

**`com.myopicmobile.textwarrior.common`（24 个文件）：**

| 文件 | 风险 | 说明 |
|------|------|------|
| `FreeScrollingTextField.java` | 🔴 **高** | 核心编辑控件，~2000+ 行 |
| `Document.java` | 🟡 中 | 文档模型 |
| `DocumentProvider.java` | 🟡 中 | 文档提供器 |
| `Lexer.java` | 🟡 中 | 词法分析器基类 |
| `Language.java` | 🟢 低 | 语言接口 |
| `LanguageLua.java` | 🟢 低 | Lua 语言定义 |
| `LanguageC.java` | 🟢 低 | C 语言定义 |
| `TextBuffer.java` | 🟡 中 | 文本缓冲区 |
| `TextBufferCache.java` | 🟢 低 | 缓冲区缓存 |
| `AutoComplete.java` | 🟡 中 | 自动补全 |
| `AutoIndent.java` | 🟢 低 | 自动缩进 |
| `ColorScheme.java` | 🟢 低 | 颜色方案 |
| `ColorSchemeDark.java` | 🟢 低 | 深色方案 |
| `ColorSchemeLight.java` | 🟢 低 | 浅色方案 |
| `UndoStack.java` | 🟢 低 | 撤销栈 |
| `ReadTask.java` | 🟢 低 | 读取任务 |
| `WriteTask.java` | 🟢 低 | 写入任务 |
| `Pair.java` | 🟢 低 | 元组 |
| `Flag.java` | 🟢 低 | 标志接口 |
| `RowListener.java` | 🟢 低 | 行监听器 |
| `SearchStrategy.java` | 🟢 低 | 搜索策略 |
| `LinearSearchStrategy.java` | 🟢 低 | 线性搜索 |
| `TextWarriorException.java` | 🟢 低 | 异常 |
| `LanguageNonProg.java` | 🟢 低 | 非编程语言 |

**`com.myopicmobile.textwarrior.android`（9 个文件）：**

| 文件 | 风险 | 说明 |
|------|------|------|
| `FreeScrollingTextField.java` | 🔴 **高** | 核心编辑器，绘制/触摸/光标 |
| `AutoCompletePanel.java` | 🟡 中 | 自动补全面板 |
| `ClipboardPanel.java` | 🟢 低 | 剪贴板面板 |
| `KeysInterpreter.java` | 🟡 中 | 按键解释器 |
| `OnSelectionChangedListener.java` | 🟢 低 | 选择监听器 |
| `TextChangeListener.java` | 🟢 低 | 文本变更监听器 |
| `TouchNavigationMethod.java` | 🟡 中 | 触摸导航 |
| `TrackpadNavigationMethod.java` | 🟢 低 | 触控板导航 |
| `YoyoNavigationMethod.java` | 🟢 低 | Yoyo 导航 |

### 9.2 `FreeScrollingTextField` 翻译重点

这是最大的单个文件（~2000+ 行），包含：
- **绘制**：行号、文本、光标、选中高亮
- **触摸事件**：滚动、选择、长按
- **光标管理**：移动、输入、删除
- **行包装**：自动换行计算
- **撤销/重做**
- **自动缩进**

```kotlin
// Java 翻译示例
// 注意 protected 字段的处理

// Java: protected Paint _paint;
// Kotlin: protectedlateinit var _paint: Paint
// 或用: protected var _paint: Paint = Paint()

// Java: protected int _caretX, _caretY;
// Kotlin: protected var caretX: Int = 0
//         protected var caretY: Int = 0

// protected 方法 → protected fun
// private 方法 → private fun
// 匿名内部类 → 对象表达式或 inner class
```

**危险信号：** `FreeScrollingTextField` 在 AndroLuaX 的原始版本中是混淆类 `b.b.a.a.c`，因此需要确保翻译后的行为**完全等价**，**建议编写渲染快照测试**。

---

## 10. Phase 8 — 收尾与集成

### 10.1 第三方依赖处理

| 依赖 | 处置 | 原因 |
|------|------|------|
| `bsh-*.jar` (BeanShell) | **保留 Java 依赖** | ~100 类，纯 Java 实现，无需翻译 |
| `com.android.cglib.*` (DexMaker) | **保留 Java 依赖** | 字节码操作，无需翻译 |
| `glide-full-4.7.0` | **保留 Java 依赖** | 图片加载库 |
| `LuaCustRecyclerAdapter.jar` | **翻译为 Kotlin** | 项目特有代码 |
| `com.baidu.mobstat.*` | **移除** | 百度统计，替换或删除 |
| `a/a/*`, `b/a/*`, `c/a/*` | **审计后移除** | 混淆类，已由 AndroLuaPro 源码替代 |

### 10.2 清理剩余的 Java 文件

在所有 Kotlin 翻译完成后：
1. 删除所有 `.java` 源文件
2. 验证 `androlua/src/main/java/` 下仅含 `.kt` 文件
3. 验证 `app/src/main/java/` 下仅含 `.kt` 文件
4. 删除 `docs/decompiled/` 不再需要的 `.class` 文件（项目清理）

### 10.3 最终验证清单

```
[ ] 所有 136+ Java 文件 → Kotlin 翻译完成
[ ] 零 Java 源文件残留
[ ] 编译零错误、零警告
[ ] JNI 符号验证通过（javap -s 对比）
[ ] Lua 测试套件全通过
[ ] 启动时间 < 2s
[ ] APK 构建功能正常
[ ] APK 大小在预期范围内
[ ] 性能基准满足：Lua 执行、编辑器启动、内存占用
```

---

## 11. 技术注意事项（核心）

### 11.1 JNI 符号验证流程（每个 JNI 类翻译后必须执行）

```bash
# Step 1: 反编译 Java 版的 JNI 符号
# 从原始 JAR 中提取目标类
unzip -p androlua/libs/AndroLua+5.0.18_AndroidX.jar com/luajava/LuaState.class > /tmp/LuaState_java.class
javap -s -p /tmp/LuaState_java.class | grep "native" > /tmp/java_jni_sigs.txt

# Step 2: 编译 Kotlin 版后提取 JNI 符号
./gradlew :androlua:compileDebugKotlin
javap -s -p androlua/build/intermediates/classes/debug/com/luajava/LuaState.class | grep "native" > /tmp/kt_jni_sigs.txt

# Step 3: 对比（差异必须为零）
diff /tmp/java_jni_sigs.txt /tmp/kt_jni_sigs.txt
```

**如果出现差异，常见原因：**
1. `private external fun` 写到了 `companion object` 中 → 改为 class 成员
2. 方法名大小写不一致 → 使用 `@JvmName("originalName")`
3. Kotlin 编译器生成了额外桥接方法 → 检查是否使用了默认参数

### 11.2 Kotlin 空安全处理边界

`LuaJavaAPI` 和 `LuaActivity` 中大量的 `Object` 参数传递对空安全影响最大：

```kotlin
// Java: public void pushString(String str)
// Kotlin: fun pushString(str: String?)  // 允许 null

// Java: public Object toJavaObject(int idx)
// Kotlin: fun toJavaObject(idx: Int): Any?  // 返回可能为 null

// 关键：被 Lua 脚本调用的方法必须对 null 容忍
// 因为在 Lua 中 nil = Kotlin null
```

**规则：**
- 所有通过 JNI 传入/传出的参数声明为 `Any?`（可空）
- 只有明确的内部逻辑可以声明非空
- 使用 `requireNotNull()` 做防御性检查，而非 `!!`

### 11.3 `LuaInvocationHandler` — Proxy 兼容性

```kotlin
// Java 版使用 java.lang.reflect.Proxy，在 Kotlin 中完全兼容
class LuaInvocationHandler : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method?, args: Array<Any?>?): Any? {
        // 实现 Lua 函数调用桥接
    }
}

// Lua 侧使用方式不变：
// local proxy = luajava.createProxy("android.view.View.OnClickListener", {onClick = function(v) ... end})
```

### 11.4 `LuaEnhancer` — DexMaker 桥接

`LuaEnhancer` 封装了 `com.android.cglib.proxy.Enhancer`（DexMaker）：
- **不翻译 DexMaker**：保持为 Java JAR 依赖
- **只翻译 `LuaEnhancer` 的包装逻辑**：Kotlin 调用 Java 库完全兼容

### 11.5 `onActivityResult` → `ActivityResultContracts`

```kotlin
// 方案 A（兼容，推荐初始采用）：
// 保留 @Deprecated("Use ActivityResultContracts") 覆盖
@Deprecated("Use ActivityResultContracts")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // 保持原逻辑
    super.onActivityResult(requestCode, resultCode, data)
}

// 方案 B（现代化）：
private val startForResult = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    // 处理 result
}
```

### 11.6 `finalize()` → `Cleaner` / `Closeable`

```java
// Java 版依赖 finalize() 释放 LuaState
@Override
protected void finalize() {
    close();
}
```

```kotlin
// Kotlin 版：不重写 finalize()
// 选项 1：实现 Closeable，配合 use()
class LuaState : Closeable {
    override fun close() {
        // 释放资源
    }
}

// 选项 2：使用 Cleaner（JDK 9+）
// Android API 26+ 支持，但本项目 minSdk=14/21，
// 因此建议使用 finalize() 的 Kotlin 等效方案：
protected fun finalize() {
    close()
}
// 或者使用 kotlinx.coroutines 的 CancelHandler
```

> ⚠️ **注意**：Kotlin 官方不推荐重写 `finalize()`，但在 Android minSdk 14-21 的环境下，这是最安全的选择。如果 minSdk 提升到 26+，可改用 `Cleaner`。

### 11.7 `synchronized` 关键字的处理

```kotlin
// Java: synchronized void xxx() { ... }
// Kotlin: @Synchronized fun xxx() { ... }

// Java: synchronized (L) { ... }
// Kotlin: synchronized(L) { ... }

// Java: private synchronized native void _xxx();
// Kotlin: @Synchronized private external fun _xxx()
```

### 11.8 与 Lua 脚本兼容性边界

**不做任何修改的 Lua API**（必须保持 100% 兼容）：

```lua
-- 以下 Lua 代码必须在 Java 版和 Kotlin 版中行为一致
require "import"
import "android.widget.*"
import "com.androlua.*"

activity.setTitle("Test")
activity.setContentView(loadlayout(layout))

-- 并发
thread(code)
task(code, callback)
timer(func, delay, period)

-- Java 反射
luajava.bindClass("java.lang.String")
luajava.new("java.lang.StringBuilder")
luajava.override(Interface, {method = function() end})

-- 构建
require "bin"
bin(luapath)
```

---

## 12. 附录：文件映射表

### 12.1 完整文件对应关系

`androlua/src/main/java/com/androlua/` → 55 个 Kotlin 文件

| # | Java 源文件 | Kotlin 目标 | 优先级 | Phase |
|---|-------------|-------------|--------|-------|
| 1 | LuaActivity.java | LuaActivity.kt | P0 | 3 |
| 2 | LuaActivityX.java | LuaActivityX.kt | P0 | 3 |
| 3 | LuaApplication.java | LuaApplication.kt | P0 | 3 |
| 4 | LuaContext.java | LuaContext.kt | P0 | 3 |
| 5 | LuaService.java | LuaService.kt | P0 | 3 |
| 6 | LuaAccessibilityService.java | LuaAccessibilityService.kt | P0 | 3 |
| 7 | CrashHandler.java | CrashHandler.kt | P0 | 3 |
| 8 | Main.java | Main.kt | P1 | 4 |
| 9 | Welcome.java | Welcome.kt | P1 | 4 |
| 10 | LuaDialog.java | LuaDialog.kt | P1 | 4 |
| 11 | LuaEditor.java | LuaEditor.kt | P1 | 4 |
| 12 | LuaLexer.java | LuaLexer.kt | P1 | 4 |
| 13 | LuaTokenTypes.java | LuaTokenTypes.kt | P1 | 4 |
| 14 | LuaAdapter.java | LuaAdapter.kt | P1 | 4 |
| 15 | LuaArrayAdapter.java | LuaArrayAdapter.kt | P1 | 4 |
| 16 | LuaMultiAdapter.java | LuaMultiAdapter.kt | P1 | 4 |
| 17 | LuaExpandableListAdapter.java | LuaExpandableListAdapter.kt | P1 | 4 |
| 18 | LuaAsyncTask.java | LuaAsyncTask.kt | P1 | 6 |
| 19 | LuaThread.java | LuaThread.kt | P1 | 6 |
| 20 | LuaTimer.java | LuaTimer.kt | P1 | 6 |
| 21 | LuaTimerTask.java | LuaTimerTask.kt | P1 | 6 |
| 22 | LuaRunnable.java | LuaRunnable.kt | P1 | 6 |
| 23 | Ticker.java | Ticker.kt | P1 | 6 |
| 24 | LuaDexLoader.java | LuaDexLoader.kt | P1 | 3 |
| 25 | LuaDexClassLoader.java | LuaDexClassLoader.kt | P1 | 3 |
| 26 | LuaEnhancer.java | LuaEnhancer.kt | P1 | 3 |
| 27 | LuaAssetLoader.java | LuaAssetLoader.kt | P1 | 3 |
| 28 | LuaWebView.java | LuaWebView.kt | P2 | 4 |
| 29 | LuaCameraView.java | LuaCameraView.kt | P2 | 5 |
| 30 | LuaView.java | LuaView.kt | P2 | 5 |
| 31 | LuaDrawable.java | LuaDrawable.kt | P2 | 5 |
| 32 | LuaBitmapDrawable.java | LuaBitmapDrawable.kt | P2 | 5 |
| 33 | NineBitmapDrawable.java | NineBitmapDrawable.kt | P2 | 5 |
| 34 | LoadingDrawable.java | LoadingDrawable.kt | P2 | 5 |
| 35 | LuaAnimation.java | LuaAnimation.kt | P2 | 5 |
| 36 | LuaBitmap.java | LuaBitmap.kt | P2 | 5 |
| 37 | LuaGcable.java | LuaGcable.kt | P2 | 6 |
| 38 | LuaFragment.java | LuaFragment.kt | P2 | 5 |
| 39 | LuaPreferenceFragment.java | LuaPreferenceFragment.kt | P2 | 5 |
| 40 | LuaResources.java | LuaResources.kt | P2 | 5 |
| 41 | LuaUtil.java | LuaUtil.kt | P1 | 6 |
| 42 | ZipUtil.java | ZipUtil.kt | P1 | 6 |
| 43 | LuaStringUtil.java | LuaStringUtil.kt | P1 | 6 |
| 44 | LuaPrint.java | LuaPrint.kt | P1 | 6 |
| 45 | Http.java | Http.kt | P2 | 6 |
| 46 | Download.java | Download.kt | P2 | 6 |
| 47 | LuaServer.java | LuaServer.kt | P2 | 6 |
| 48 | LuaClient.java | LuaClient.kt | P2 | 6 |
| 49 | LuaBroadcastReceiver.java | LuaBroadcastReceiver.kt | P2 | 6 |
| 50 | LuaContentObserver.java | LuaContentObserver.kt | P2 | 6 |
| 51 | LuaFileObserver.java | LuaFileObserver.kt | P2 | 6 |
| 52 | LuaExAdapter.java | LuaExAdapter.kt | P2 | 5 |
| 53 | GifDecoder.java | GifDecoder.kt | P2 | 6 |
| 54 | LuaMaterialDialog.java | LuaMaterialDialog.kt | P1 | 4 |
| 55 | AlertControllerBridge.java | AlertControllerBridge.kt | P1 | 4 |

### 12.2 翻译优先级说明

```
P0（关键路径，必须首先翻译）：
  - JNI 绑定层 (com.luajava.*)
  - LuaApplication, LuaActivity, LuaService, LuaContext
  - CrashHandler, LuaDexLoader, LuaEnhancer
  - Main, Welcome

P1（宿主核心功能）：
  - LuaEditor, LuaLexer, LuaTokenTypes
  - LuaDialog, LuaMaterialDialog
  - LuaAdapter 系列
  - LuaAsyncTask, LuaThread, LuaTimer 等并发
  - LuaUtil, LuaPrint, LuaStringUtil
  - TextWarrior 编辑器框架

P2（次要功能）：
  - 自定义 Widget (android.widget.*)
  - Http, Download, LuaServer, LuaClient
  - LuaWebView, LuaCameraView
  - LuaFragment, LuaDrawable 系列
  - com.nirenr.* 工具
```

---

> **文档结束**
>
> 版本：v2.0 · 最后更新：2026-07-28
> 编制基准：`docs/DELUA_PROJECT_PLAN.md` + `docs/ANDROLUA_AUDIT_REPORT.md` + `docs/AndroLua_pro-master/` 源码审计
>
> 本文件作为 delua 项目的工作执行手册，所有迁移工作应按 Phase 编号顺序推进，
> 每个 Phase 完成前必须通过对应的 Quality Gate 方可进入下一 Phase。
