# AndroLua Engine Migration — Work Order & Quality Checklist

| Metadata | Value |
|----------|-------|
| Document ID | DELUA-ENG-001 |
| Status | Draft |
| Author | Audit Team |
| Stakeholders | Core Platform, IDE, QA |
| Priority | P0 — Blocks v2.0 release |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Background & Rationale](#2-background--rationale)
3. [Architecture Blueprint](#3-architecture-blueprint)
4. [Source Code Audit Summary](#4-source-code-audit-summary)
5. [Work Packages](#5-work-packages)
   - [WP-001: NDK Compilation — Native Libraries](#wp-001-ndk-compilation--native-libraries)
   - [WP-002: Java Source — Engine DEX](#wp-002-java-source--engine-dex)
   - [WP-003: EngineActivity.java — Integration Host](#wp-003-engineactivityjava--integration-host)
   - [WP-004: EngineBridge.kt — Kotlin-to-Engine IPC](#wp-004-enginebridgekt--kotlin-to-engine-ipc)
   - [WP-005: Test Suite — Validation](#wp-005-test-suite--validation)
   - [WP-006: LuaJ Compat Layer — Deprecation & Removal](#wp-006-luaj-compat-layer--deprecation--removal)
   - [WP-007: Build System & CI](#wp-007-build-system--ci)
6. [Risk Register](#6-risk-register)
7. [Quality Gates](#7-quality-gates)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Rollback Plan](#9-rollback-plan)
10. [Appendix](#10-appendix)

---

## 1. Executive Summary

The current delua IDE embeds **LuaJ 3.0.1** — a pure Java Lua 5.2 runtime (package `org.luaj.vm2`). Approximately 2,000 lines of Kotlin compat code bridge LuaJ to the Android framework, accumulating 6 P0 defects (type coercion, coroutine semantics, class-loading deadlocks). This work order details the replacement of LuaJ with the **native C Lua 5.3.3 engine** sourced from the AndroLua Pro open-source project.

The migration is executed across 7 work packages with 8 acceptance criteria. Estimated duration: 21 calendar days (15 engineering days).

---

## 2. Background & Rationale

### Current Architecture (LuaJ)

```
  Kotlin IDE
      │
      ▼
  LuaJ Compat Layer (~2000 lines, 14 files)
      │
      ▼
  LuaJ 3.0.1 (Pure Java Lua 5.2)
```

### Target Architecture (AndroLua Native)

```
  Kotlin IDE                           Process Boundary
  ──────────────────                   ────────────────
  EngineBridge.kt ──Intent──► EngineActivity (extends LuaActivity)
                                  │
                                  ▼
                              Luajava JNI (com.luajava)
                                  │
                                  ▼
                          libluajava.so + libgl.so
                          Lua 5.3.3 C Runtime
```

### Why Not LuaJ

| Issue | Impact | Severity |
|-------|--------|----------|
| Pure Java interpreter is 3-8× slower than C Lua for compute-heavy scripts | User-perceptible lag in game loops, big data processing | P0 |
| `LuaValue` type hierarchy lacks Android object bridging | Frequent `checkuserdata()` crashes | P0 |
| No coroutine yield across `pcall` boundary | Scripts deadlock on Android `onCreate` / `onClick` | P0 |
| No native module loading (SQLite, JSON, crypto) | Users must bundle Java impls, doubling APK size | P1 |
| No DEX class loading | Plugins (`.alp`) cannot execute; entire plugin ecosystem blocked | P0 |

---

## 3. Architecture Blueprint

### 3.1 Process Model

```
┌─────────────────────────────────────────────────────┐
│                   Kotlin IDE Process                 │
│                                                      │
│  EngineBridge.kt                                     │
│    - Packages Intent with: projectDir, mainFile       │
│    - Starts EngineActivity via startActivityForResult │
│    - Polls result JSON file on completion             │
│                                                      │
└──────────────────────┬──────────────────────────────┘
                       │ Intent
                       ▼
┌─────────────────────────────────────────────────────┐
│                Engine Process (Dalvik/ART)            │
│                                                      │
│  EngineActivity.java                                 │
│    - Extends LuaActivity (from AndroLua Pro)          │
│    - Overrides onCreate → Lua script entry point      │
│    - On completion, writes result.json to cache dir   │
│    - Calls finish() → IDE receives result             │
│                                                      │
│  LuaActivity (com.androlua)                          │
│    - LuaState management                             │
│    - loadlayout / loadbitmap / loadmenu              │
│    - Lifecycle → Lua callbacks                       │
│                                                      │
│  com.luajava.*                                       │
│    - JNI bridge (LuaState, LuaObject, LuaJavaAPI)    │
│                                                      │
│  libluajava.so + libgl.so (loaded via System.load)   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 3.2 Intent Protocol

```java
// EngineBridge → EngineActivity
Intent intent = new Intent(context, EngineActivity.class);
intent.putExtra("projectDir", projectDir.getAbsolutePath());
intent.putExtra("mainFile", "main.lua");
intent.putExtra("debugMode", true);           // optional
intent.putExtra("engineDexPath", dexPath);    // optional

// EngineActivity → EngineBridge (via result file)
// File: context.getCacheDir() + "/engine_result.json"
{
  "exitCode": 0,
  "error": null,
  "output": "...",
  "durationMs": 1234
}
```

---

## 4. Source Code Audit Summary

### 4.1 Version Selection

| Candidate | Verdict | Rationale |
|-----------|---------|-----------|
| **AndroLua Pro** (nirenr) | **✅ Primary** | Full MIT source, Lua 5.3.3, 21 native modules, active community |
| AndroLua (mkottman) | ❌ Rejected | Lua 5.1.5, no LuaActivity, no project system, Eclipse/Ant |
| AndroLuaX (Jesse205) | ❌ Deprecated | Core engine is a prebuilt JAR from decompiled binary; no source available; security risk |
| LuaJ (luaj.org) | ❌ Being Replaced | Pure Java; too slow; 6 P0 defects in compat layer |

### 4.2 Pro Source Files to Port

#### Tier 0 — Core (must port, engine won't boot without)

| Module | Files | Lines | Notes |
|--------|-------|-------|-------|
| `com.luajava` | `LuaState.java`, `LuaObject.java`, `LuaJavaAPI.java`, `LuaStateFactory.java`, `JavaFunction.java`, `LuaInvocationHandler.java`, `LuaException.java`, `Console.java` | ~3,200 | JNI bridge; P0. The `org.keplerproject` original is NOT compatible |
| `com.luajava` (ext) | `LuaTable.java`, `LuaString.java`, `LuaStack.java`, `LuaList.java`, `LuaMetaTable.java`, `LuaFunction.java`, `LuaAbstractMethodInterceptor.java`, `LuaMethodInterceptor.java` | ~1,800 | Added in Pro; required for Lua 5.3 features |

#### Tier 1 — Runtime (must port, required by LuaActivity)

| Module | Files | Lines | Notes |
|--------|-------|-------|-------|
| `com.androlua` | `LuaActivity.java`, `LuaApplication.java`, `LuaContext.java` | ~2,100 | Core lifecycle; P0 |
| `com.androlua` | `LuaTimer.java`, `LuaTimerTask.java`, `LuaThread.java`, `LuaAsyncTask.java` | ~1,200 | Async execution; P1 |
| `com.androlua` | `LuaAdapter.java`, `LuaArrayAdapter.java`, `LuaExAdapter.java`, `LuaMultiAdapter.java`, `LuaExpandableListAdapter.java` | ~1,500 | UI data binding; P1 |
| `com.androlua` | `LuaDialog.java`, `LuaFragment.java`, `LuaWebView.java`, `LuaView.java` | ~1,800 | UI wrappers; P1 |
| `com.androlua` | `LuaDexClassLoader.java`, `LuaDexLoader.java` | ~600 | Plugin loading; P0 |

#### Tier 2 — Utilities (port on demand)

| Module | Files | Notes |
|--------|-------|-------|
| `com.androlua` | `LuaBitmap.java`, `LuaBitmapDrawable.java`, `NineBitmapDrawable.java`, `GifDecoder.java` | Graphics |
| `com.androlua` | `Http.java`, `Download.java`, `LuaClient.java`, `LuaServer.java` | Networking |
| `com.androlua` | `LuaFileObserver.java`, `LuaContentObserver.java`, `LuaBroadcastReceiver.java` | System integration |
| `com.androlua` | `CrashHandler.java`, `LuaPrint.java`, `LuaUtil.java`, `ZipUtil.java` | Infrastructure |

#### Tier 3 — CGLIB Proxy (port with alternatives)

| Module | Files | Notes |
|--------|-------|-------|
| `com.android.cglib.proxy` | 10 files | Dynamic proxy; ~2,500 lines |
| `com.android.cglib.dx` | 15+ files | DEX bytecode generation; ~8,000 lines |

### 4.3 Lua Library Files to Port

| File | Lines | Purpose | Tier |
|------|-------|---------|------|
| `import.lua` | 503 | Core class loader; wildcards, DEX, URIs | P0 |
| `loadlayout.lua` | ~800 | Android XML layout inflater | P0 |
| `loadbitmap.lua` | ~200 | Bitmap asset loader | P1 |
| `loadmenu.lua` | ~150 | Menu XML inflater | P1 |
| `json.lua` | ~300 | JSON encode/decode | P1 |
| `http.lua` | ~400 | HTTP client wrapper | P2 |
| `base64.lua` | ~100 | Base64 encode/decode | P2 |
| `xml.lua` | ~250 | XML parse/generate | P2 |

### 4.4 Native C Modules

| Module | Source Dir | Lines | Status |
|--------|-----------|-------|--------|
| `lua` (5.3.3) | `jni/lua/` | ~25,000 | ✅ Must compile first |
| `luajava` | `jni/luajava/` | ~3,200 | ✅ Must compile first |
| `luagl` (OpenGL ES) | `jni/luagl/` | ~1,500 | ⏳ Compile after lua+luajava verified |
| `cjson` | `jni/cjson/` | ~2,000 | 🔲 On demand |
| `lsqlite3` | `jni/lsqlite3/` | ~3,000 | 🔲 On demand |
| `socket` | `jni/socket/` | ~4,000 | 🔲 On demand |
| `lfs` | `jni/lfs/` | ~500 | 🔲 On demand |
| `regex` | `jni/regex/` | ~1,000 | 🔲 On demand |
| `crypt` | `jni/crypt/` | ~1,500 | 🔲 On demand |
| `sensor` | `jni/sensor/` | ~400 | 🔲 On demand |
| `lluv` / `luv` | `jni/lluv/` | ~3,000 | 🔲 On demand (risk: libuv Android compat) |
| `tcc` | `jni/tcc/` | ~10,000 | 🔲 On demand (risk: 32-bit only) |
| `xml` | `jni/xml/` | ~1,000 | 🔲 On demand |
| `zip` / `zlib` | `jni/zip/`, `jni/zlib/` | ~5,000 | 🔲 On demand |
| `bson` | `jni/bson/` | ~800 | 🔲 On demand |
| `canvas` | `jni/canvas/` | ~600 | 🔲 On demand |
| `md5` | `jni/md5/` | ~300 | 🔲 On demand |

---

## 5. Work Packages

### WP-001: NDK Compilation — Native Libraries

**Owner**: Platform / Build Engineer  
**Estimate**: 5 days  
**Depends On**: None  
**Deliverable**: `libluajava.so` + `libgl.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 1.1 | Extract Lua 5.3.3 C sources | From `AndroLua_pro-master/app/src/main/jni/lua/` (65 files) | File count matches |
| 1.2 | Extract luajava JNI bridge | From `AndroLua_pro-master/app/src/main/jni/luajava/` (`luajava.c`, `luajava.h`, `decode.c`, `Android.mk`) | Compiles clean |
| 1.3 | Extract luagl (OpenGL ES) | From `AndroLua_pro-master/app/src/main/jni/luagl/` | Compiles clean |
| 1.4 | Create `jni/Android.mk` | Top-level makefile including `lua/`, `luajava/`, `luagl/` | `ndk-build` passes |
| 1.5 | Create `jni/Application.mk` | `APP_ABI = arm64-v8a armeabi-v7a x86_64`; `APP_PLATFORM = android-18` | All ABIs produce `.so` |
| 1.6 | Create CMakeLists.txt (alternative) | For AGP `externalNativeBuild { cmake {} }` | Build passes with CMake |
| 1.7 | Verify NDK version compatibility | Test with NDK r25+ (LTS). Lua 5.3 uses C89/C99 features; ensure no `-Werror` breakage | Zero warnings |
| 1.8 | Add build to app-level `build.gradle` | `externalNativeBuild { ndkBuild { path 'src/main/jni/Android.mk' } }` | `./gradlew assembleDebug` produces `.so` |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-1.1 | NDK r27 dropped `ndk-build` support for older platforms | Pin to NDK r25 or use CMake |
| P-1.2 | `luajava.c` uses `dvm*` / `JNI_OnLoad` patterns incompatible with ART 14+ | Audit all `#ifdef` blocks; test on API 34 |
| P-1.3 | `luagl` requires OpenGL ES 1.0; Android 14+ deprecates fixed-function pipeline | Conditionally compile; provide graceful fallback |
| P-1.4 | `Android.mk` contains hardcoded paths | Replace all absolute paths with `$(LOCAL_PATH)` relative |
| P-1.5 | Cross-compilation: `x86_64` emulator may differ from device | CI matrix test on both |
| P-1.6 | Stripped symbols cause crash without debug info | Ship unstripped `.so` in debug builds only |
| P-1.7 | `libluajava.so` exports too many symbols | Audit `luajava.h`; only export JNI `Java_com_*` symbols |

#### Checkpoints

- [ ] `ndk-build` compiles `libluajava.so` for all 3 ABIs without errors
- [ ] `ndk-build` compiles `libgl.so` (or merged into libluajava) without errors
- [ ] `System.loadLibrary("luajava")` succeeds on API 24–34 devices
- [ ] `LuaState.LUA_VERSION == "Lua 5.3"` returned from native
- [ ] `LuaState.newState()` returns non-null pointer
- [ ] Stripped `.so` file size within 2× of Pro release baseline (~1.2 MB)

---

### WP-002: Java Source — Engine DEX

**Owner**: Core Runtime Engineer  
**Estimate**: 5 days  
**Depends On**: WP-001 (for JNI signatures)  
**Deliverable**: `engine.dex` containing `com.luajava.*` + `com.androlua.*` + `com.android.cglib.*`

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 2.1 | Create `androlua-engine/` Gradle module | Pure Java library; `compileOnly 'androidx.appcompat:appcompat:1.6.1'` | Module compiles |
| 2.2 | Port `com.luajava.*` (Tier 0) | 16 files from AndroLua Pro | All imports resolve; zero errors |
| 2.3 | Port `com.androlua.*` — Core (Tier 1) | `LuaActivity.java`, `LuaApplication.java`, `LuaContext.java`, `LuaTimer.java`, `LuaTimerTask.java`, `LuaThread.java`, `LuaAsyncTask.java`, `LuaAdapter.java`, `LuaArrayAdapter.java`, `LuaExAdapter.java`, `LuaMultiAdapter.java`, `LuaExpandableListAdapter.java`, `LuaDialog.java`, `LuaFragment.java`, `LuaWebView.java`, `LuaView.java`, `LuaDexClassLoader.java`, `LuaDexLoader.java` | Compiles; no runtime LinkageError |
| 2.4 | Port `com.androlua.*` — Utilities (Tier 2) | `LuaBitmap.java`, `Http.java`, `LuaClient.java`, `LuaServer.java`, `CrashHandler.java`, `LuaPrint.java`, `LuaUtil.java`, `ZipUtil.java` | Compiles |
| 2.5 | Port `com.android.cglib.*` (Tier 3) | 25+ files; proxy + dx | NOTE: see precautions |
| 2.6 | Port `com.androlua` — custom Android widget overrides | `android.widget.*` shadow classes: `CardView.java`, `DrawerLayout.java`, `ExListView.java`, `FloatButton.java`, `HorizontalListView.java`, `PageView.java`, `PullingLayout.java`, `ToolBar.java` | Compiles |
| 2.7 | Port `com.androlua.LuaEditor`, `LuaLexer`, `LuaTokenTypes` | Syntax highlighting in script editor | Compiles |
| 2.8 | Port `com.myopicmobile.textwarrior.*` | Text editor widget library | Compiles |
| 2.9 | Remove `LuaActivityX.java` | Already deprecated in Pro; use single `LuaActivity` | File deleted |
| 2.10 | Add `EngineActivity.java` | Thin class extending `LuaActivity`; see WP-003 | `Activity` launches |
| 2.11 | Bundle Lua library files to `resources/lua/` | 26 `.lua` files from Pro | `import.lua` loads |
| 2.12 | Build `engine.dex` | `./gradlew :androlua-engine:transformDexArchiveWithDexMergerForDebug` | DEX produced; < 4 MB |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-2.1 | `com.android.cglib.dx.*` uses unsupported `sun.misc.Unsafe` | Port to `VarHandle` (Java 8+) or remove; fallback to `java.lang.reflect.Proxy` |
| P-2.2 | `com.android.cglib.proxy.Enhancer` calls `DexMaker` which writes to `/data/data/` | Ensure runtime write permission; use `context.getCodeCacheDir()` |
| P-2.3 | `LuaActivity.java` has hard reference to R.class (`com.androlua.R`) | Refactor all resource lookups out of LuaActivity into host-provided `LuaResources.java` |
| P-2.4 | `LuaApplication.java` extends `Application`; engine will run in own process | Strip `Application`; extract `LuaContext` interface; let host provide context |
| P-2.5 | Support Library classes (`android.support.v4.*`, `android.support.v7.*`) are used | Migrate all to AndroidX equivalents (`androidx.*`) |
| P-2.6 | `LuaActivity` uses `loadlayout` which inflates XML via `LayoutInflater` | Verify XML inflation works cross-process (engine has own `LayoutInflater`) |
| P-2.7 | 64K method limit for engine.dex | Enable multidex if needed; measure method count after port |
| P-2.8 | `LuaDexLoader` uses `dalvik.system.DexClassLoader` | Verify compatibility with ART (API 21+) |
| P-2.9 | `LuaEditor.java` depends on `textwarrior` library which may not compile clean | Test compilation; patch as needed |
| P-2.10 | `com.nirenr.Color.java` / `ColorFinder.java` / `SplitEditView.java` — obscure and large | Only port if screenshot/color-picker features are required |

#### Checkpoints

- [ ] `engine.dex` compiles: all Java source files compile without error
- [ ] `engine.dex` is loadable via `DexClassLoader` at runtime
- [ ] `Class.forName("com.luajava.LuaState", true, engineClassLoader)` succeeds
- [ ] `Class.forName("com.androlua.LuaActivity", true, engineClassLoader)` succeeds
- [ ] `import.lua` loads without error (`require "import"`)
- [ ] `engine.dex` file size < 4 MB
- [ ] Method count < 60,000 (verify with `./gradlew countMethods` or equivalent)

---

### WP-003: EngineActivity.java — Integration Host

**Owner**: IDE Integration Engineer  
**Estimate**: 2 days  
**Depends On**: WP-002 (engine.dex available)  
**Deliverable**: A new `EngineActivity.java` in the main delua APK that bridges IDE and engine

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 3.1 | Create `EngineActivity.java` | Extends `LuaActivity`; lives in main APK (not engine.dex) | Compiles |
| 3.2 | Extract `projectDir` and `mainFile` from Intent extras | `getIntent().getStringExtra("projectDir")` | Reads extras |
| 3.3 | Set working directory before Lua init | `System.setProperty("user.dir", projectDir)` or Lua `package.path` | Script finds files |
| 3.4 | Call `doFile(File(mainFile))` in `onCreate` | Override LuaActivity lifecycle | Script executes |
| 3.5 | Write `result.json` on script completion | To `context.cacheDir + "/engine_result.json"` | JSON written |
| 3.6 | Handle script errors gracefully | Catch `LuaException`; write error details to result.json; show Toast | No crash on error |
| 3.7 | Pass debug mode flag | If `debugMode=true`, enable Lua `print()` to logcat | Logcat visible |
| 3.8 | Self-terminate after result written | `finish()` | Activity exits |
| 3.9 | Handle timeout | Set a configurable timeout (default 30s); terminate if exceeded | Timeout kills script |
| 3.10 | Handle `loadlayout` XML resolution | Resolve `res/layout/*.xml` relative to `projectDir` | Layout inflates |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-3.1 | `EngineActivity` must be in main APK `AndroidManifest.xml` | Add `<activity android:name=".engine.EngineActivity" android:process=":engine" />` |
| P-3.2 | `LuaActivity` expects `com.androlua.R` | Inject host app's Resources; or override `getResources()` |
| P-3.3 | `LuaActivity.onDestroy()` may not be called if killed | Use `onPause()` as fallback for result writing |
| P-3.4 | `result.json` concurrent write from multiple engine instances | Use lock file or unique file per instance (timestamp) |
| P-3.5 | Script writes to `package.path` at runtime; need isolation | Reset `package.path` at start; don't pollute host Lua state |
| P-3.6 | `LuaActivity` has `onActivityResult`/`onRequestPermissionsResult` | Ensure these propagate to Lua callbacks |

#### Checkpoints

- [ ] `EngineActivity` launches from Intent
- [ ] Lua script reads from `projectDir`
- [ ] `result.json` is written on normal exit
- [ ] `result.json` is written on Lua error (with error message)
- [ ] Activity terminates within 30s (or timeout)
- [ ] No leaked `LuaState` after `finish()`
- [ ] `loadlayout("main.xml")` inflates layout from `projectDir/res/layout/`

---

### WP-004: EngineBridge.kt — Kotlin-to-Engine IPC

**Owner**: IDE Integration Engineer  
**Estimate**: 2 days  
**Depends On**: WP-003 (EngineActivity ready)  
**Deliverable**: Refactored `EngineBridge.kt` that packages project into Intent, launches EngineActivity, and reads back result

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 4.1 | Remove legacy DEX loading from EngineBridge | Delete `loadEngineDex()`, `engineClassLoader` references | Compiles after removal |
| 4.2 | Simplify `EngineBridge.execute()` | Package Intent with `projectDir`, `mainFile`, `debugMode` | Intent extras correct |
| 4.3 | Launch EngineActivity via `startActivityForResult` | Or use `ActivityResultLauncher` | Activity starts |
| 4.4 | Poll for `result.json` | Or use `onActivityResult` to signal completion | Result read |
| 4.5 | Parse `result.json` and relay to caller | Return `EngineResult(exitCode, error, output, durationMs)` | All fields populated |
| 4.6 | Handle timeout | Set AlarmManager or coroutine timeout as fallback | Timeout detected |
| 4.7 | Handle activity not found | Catch `ActivityNotFoundException`; return error result | Graceful degradation |
| 4.8 | Write unit tests for IPC logic | Mock Intent; verify extras | Tests pass |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-4.1 | `startActivityForResult` deprecated in API 34 | Use `ActivityResultContracts.StartActivityForResult` |
| P-4.2 | Engine process may be killed by LMK (Low Memory Killer) | Persist `result.json` to external storage with unique name |
| P-4.3 | Engine takes too long; user navigates away | Use Foreground Service? Or bound service? (Evaluate) |
| P-4.4 | Multiple rapid executions launch multiple EngineActivities | Serialize execution in EngineBridge; use queue |
| P-4.5 | `projectDir` path too long for Intent extra (> 128 KB limit) | Write path to temp file; pass file path instead |
| P-4.6 | EngineActivity's `Context` is different from IDE's `Context` | Always pass `applicationContext` for file resolution |

#### Checkpoints

- [ ] EngineBridge builds without legacy DEX loading code
- [ ] Intent sent with correct extras
- [ ] `result.json` read success path
- [ ] `result.json` read error path (Lua script throws)
- [ ] Timeout triggers fallback
- [ ] `ActivityNotFoundException` is handled
- [ ] Unit tests pass (mocked Intent)

---

### WP-005: Test Suite — Validation

**Owner**: QA Engineer  
**Estimate**: 7 days  
**Depends On**: WP-001 through WP-004  
**Deliverable**: 3 test projects (demo, testjson, kleinbottle) execute identically on LuaJ and AndroLua engines

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 5.1 | Test project `demo` — basic UI | `loadlayout`, button click, TextView update | UI works; script completes |
| 5.2 | Test project `testjson` — JSON + networking | `import "json"`; HTTP GET; parse response | JSON parsed; network OK |
| 5.3 | Test project `kleinbottle` — 3D graphics | OpenGL ES via luagl; render Klein bottle | Renders without crash |
| 5.4 | Regression: all current LuaJ test cases | ~50 test scripts covering core language features | All pass on both engines |
| 5.5 | Performance benchmark | CPU-bound loop (pi digits, mandelbrot) | AndroLua ≥ 2× faster than LuaJ |
| 5.6 | Memory leak test | Run 100 scripts sequentially; measure native heap | No linear growth |
| 5.7 | Cross-ABI test | arm64-v8a, armeabi-v7a, x86_64 (emulator) | All ABIs pass |
| 5.8 | Android version compatibility | API 24, 28, 30, 33, 34 | All versions pass |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-5.1 | `loadlayout` behavior differs between LuaJ (pure Java) and AndroLua (C + native) | Document all behavioral differences; adjust test expectations |
| P-5.2 | `kleinbottle` uses OpenGL ES 1.0; API 28+ may reject | Test on API 28+ device; may need ES 2.0 shim |
| P-5.3 | `testjson` requires network; test network unreachable gracefully | Mock HTTP server for tests |
| P-5.4 | Lua 5.2 (LuaJ) vs Lua 5.3 (AndroLua) semantic differences | See Appendix A. Document all incompatibilities |
| P-5.5 | Performance measurement variance across devices | Run 5× each; report median; normalized to device clock speed |

#### Checkpoints

- [ ] `demo` project: UI renders, button click modifies text, script exits clean
- [ ] `testjson` project: JSON decoded; HTTP response parsed; error handling tested
- [ ] `kleinbottle` project: renders 3D model at ≥ 30 FPS
- [ ] All 50+ LuaJ regression tests pass on AndroLua engine
- [ ] Performance: AndroLua ≥ 2× LuaJ on CPU-bound benchmarks
- [ ] Native heap stable after 100 iterations
- [ ] All 3 ABIs pass on CI
- [ ] API 24, 28, 30, 33, 34 pass on CI

---

### WP-006: LuaJ Compat Layer — Deprecation & Removal

**Owner**: Core Runtime Engineer  
**Estimate**: 3 days  
**Depends On**: WP-005 (all tests pass on AndroLua)  
**Deliverable**: Remove `org.luaj.vm2` dependency and all compat Kotlin files

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 6.1 | Remove `org.luaj:luaj-jse:3.0.1` from `build.gradle` | Delete dependency | Dependency not found |
| 6.2 | Identify all LuaJ compat Kotlin files | Search `import org.luaj.vm2.*` across entire project | List generated |
| 6.3 | Delete compat files | ~14 files, ~2,125 lines | Files removed from Git |
| 6.4 | Verify no remaining references | `gradle build` — zero compilation errors | Build green |
| 6.5 | Remove LuaJ test files | `test/luaj/` or similar | Tests migrated |
| 6.6 | Update documentation | Remove LuaJ references from README, dev docs | Docs updated |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-6.1 | Some files may have mixed LuaJ + Android imports | Remove only after grep confirms no remaining usage |
| P-6.2 | Alternative IDE features may depend on LuaJ directly (e.g., syntax highlighting) | Keep `LuaLexer` / `LuaTokenTypes` (they are independent of VM) |
| P-6.3 | R8/ProGuard rules for LuaJ may interfere with AndroLua | Remove LuaJ-specific ProGuard rules; add androlua rules |
| P-6.4 | AAR/library transitive dependency may pull LuaJ back | Run `./gradlew :app:dependencies` after removal; confirm zero |

#### Checkpoints

- [ ] `./gradlew assembleDebug` passes without `org.luaj.vm2`
- [ ] `runtime classpath` grep shows zero `org.luaj.vm2` references
- [ ] APK size reduction ≥ 500 KB (LuaJ JAR is ~800 KB)
- [ ] All test projects run without LuaJ present
- [ ] ProGuard mapping does not reference LuaJ classes

---

### WP-007: Build System & CI

**Owner**: Build / DevOps Engineer  
**Estimate**: 1 day  
**Depends On**: WP-001, WP-002  
**Deliverable**: CI pipeline with NDK build, APK assembly, and multi-ABI test matrix

#### Tasks

| # | Task | Details | Verification |
|---|------|---------|--------------|
| 7.1 | Install NDK on CI runners | NDK r25+ via sdkmanager | `ndk-build --version` |
| 7.2 | Enable NDK build in `build.gradle` | `externalNativeBuild { ndkBuild { ... } }` | CI compiles `.so` |
| 7.3 | Add ABI split to APK | `splits { abi { enable true; universalApk true } }` | CI produces per-ABI APKs |
| 7.4 | Add DEX merge step to `build.gradle` | Custom task for `engine.dex` | DEX produced |
| 7.5 | Add test matrix in CI | API 24, 28, 30, 33, 34 × ABIs | Matrix tests pass |
| 7.6 | Add performance benchmark gate | CI runs benchmark; compares to baseline | PR blocked if >10% regression |
| 7.7 | Add lint check for legacy LuaJ imports | Custom lint rule or grep | PR blocked if `org.luaj.vm2` found |
| 7.8 | Update README with build instructions | NDK, CMake/ndk-build, Android Studio setup | README reviewed |

#### ⚠️ Precautions

| # | Risk | Mitigation |
|---|------|------------|
| P-7.1 | CI runner disk space — NDK is ~2 GB | Cache NDK in CI; use `sdkmanager --install` selectively |
| P-7.2 | CI macOS runner may have different NDK path | Use `ANDROID_NDK_HOME` environment variable |
| P-7.3 | Universal APK with all ABIs exceeds 100 MB | Use Play Console's ABI split + App Bundle |
| P-7.4 | CI parallel tasks may conflict on NDK lock | Serialize NDK build steps |

#### Checkpoints

- [ ] CI builds `libluajava.so` for all 3 ABIs
- [ ] CI assembles per-ABI APKs
- [ ] CI runs test matrix on all 5 API levels × 3 ABIs (15 combinations)
- [ ] CI benchmark gate rejects > 10% regression
- [ ] CI lint gate rejects `org.luaj.vm2` imports

---

## 6. Risk Register

| ID | Risk | Probability | Impact | Mitigation | Owner |
|----|------|-------------|--------|------------|-------|
| R-01 | **NDK incompatibility on Android 14+** — ART changes JNI behavior | Medium | High | Audit luajava.c for `JNI_OnLoad`; test on API 34 | Platform |
| R-02 | **Support Library → AndroidX migration gaps** | Medium | High | Map all `android.support.*` imports; test each | Runtime |
| R-03 | **CGLIB/DexMaker fails on ART** — `dx` tool deprecated in SDK 28+ | High | High | Replace with `D8` or fallback to `Proxy` | Runtime |
| R-04 | **Lua 5.2 ↔ 5.3 semantic incompatibilities** — bit32 vs bit, integer division | Medium | Medium | Document in Appendix A; patch Lua code | Runtime |
| R-05 | **ACC-08 (kleinbottle) fails on API 28+** — OpenGL ES 1.0 deprecated | Medium | Medium | Use GLSurfaceView; test on API 33 | QA |
| R-06 | **`import.lua` uses `androlua://` URIs** — not applicable in delua context | Low | Medium | Remove or replace with `file://` scheme | Runtime |
| R-07 | **Engine.dex + libluajava.so → APK size increase > 5 MB** | Medium | High | Only compile necessary C modules; ProGuard engine.dex | Build |
| R-08 | **ClassLoader isolation failure** — engine classes leak into IDE process | Medium | Critical | Use separate process (`android:process=":engine"`); test isolation | IDE |
| R-09 | **`loadlayout` XML resolution path differs** — `res/layout/` vs `layout/` | Low | Medium | Normalize paths; test with demo project | IDE |

### Risk Matrix

```
              Impact
           Low   Med   High  Critical
Prob  ┌──────────────────────────────
High  │  R-06         R-03   R-08
Med   │         R-04   R-01,R-02,R-05,R-07
Low   │         R-09
```

---

## 7. Quality Gates

Each gate blocks progression to the next work package.

| Gate | Blocking WP | Criteria | Verification Method |
|------|-------------|----------|--------------------|
| **G-01** | WP-002 | `libluajava.so` loads on API 24–34; `LuaState.newState()` returns non-null | Manual test on 3 physical devices |
| **G-02** | WP-003 | `engine.dex` loads via `DexClassLoader`; `LuaActivity.class` found | Automated CI test |
| **G-03** | WP-004 | `EngineActivity` launches and returns `result.json` | Automated integration test |
| **G-04** | WP-005 | EngineBridge launches EngineActivity and reads back result | Automated test |
| **G-05** | WP-006 | All 3 test projects pass on AndroLua engine | Automated CI matrix |
| **G-06** | WP-007 | Build green without `org.luaj.vm2`; APK size regression ≤ 2 MB | CI pipeline |

---

## 8. Acceptance Criteria

Each of 8 criteria must be satisfied for the migration to be accepted.

| ID | Criterion | Validation | Priority |
|----|-----------|------------|----------|
| **ACC-01** | `demo` project (basic UI) executes on AndroLua engine without modification | Load `demo/` project; press Run; UI appears with correct text | P0 |
| **ACC-02** | `testjson` project executes and produces identical JSON output to LuaJ baseline | Run both engines; diff output JSON | P0 |
| **ACC-03** | `kleinbottle` project renders 3D OpenGL mesh at ≥ 30 FPS | FPS counter in logcat | P0 |
| **ACC-04** | All existing LuaJ regression tests pass on AndroLua engine | CI test suite | P0 |
| **ACC-05** | Engine completes execution within 2× of LuaJ baseline for identical scripts | Timer in result.json | P1 |
| **ACC-06** | Native heap stabilizes after 100 consecutive script executions; no leak > 1 MB | `Debug.getNativeHeapAllocatedSize()` before/after | P0 |
| **ACC-07** | Engine.dex and .so files are reproducible from source (build from clean checkout) | `git clean -fdx; ./gradlew assembleDebug` | P1 |
| **ACC-08** | Engine runs correctly on API 24, 28, 30, 33, 34 on both arm64 and x86_64 | CI matrix | P0 |

---

## 9. Rollback Plan

If any acceptance criterion fails and cannot be resolved within 3 engineering days, execute rollback.

### Rollback Steps

| Step | Action | Owner |
|------|--------|-------|
| 1 | Revert `EngineBridge.kt` to previous version (Intent-based LuaJ execution) | IDE Engineer |
| 2 | Restore `org.luaj:luaj-jse:3.0.1` in `build.gradle` | Build Engineer |
| 3 | Restore all LuaJ compat Kotlin files from Git | IDE Engineer |
| 4 | Revert `engine.dex` / NDK build configuration | Build Engineer |
| 5 | Verify rollback: all 8 acceptance criteria pass on LuaJ baseline | QA |
| 6 | File migration failure report; schedule v2.1 investigation | Tech Lead |

### Rollback Trigger Conditions

- **ACC-01, ACC-02, ACC-04, ACC-06, ACC-08**: Any failure → immediate rollback
- **ACC-03, ACC-05, ACC-07**: Failure → 2-day grace period for fix; rollback if unresolved

---

## 10. Appendix

### A. Lua 5.2 vs 5.3 Semantic Differences

| Area | Lua 5.2 (LuaJ) | Lua 5.3 (AndroLua) | Impact |
|------|----------------|---------------------|--------|
| Integer type | `number` (double only) | `integer` + `float` subtypes | `1/2 = 0.5` in 5.3; integer division with `//` |
| Bitwise ops | `bit32` library | `bit32` + native `&`, `\|`, `~`, `<<`, `>>` | Code using `bit32` continues to work |
| `math.mod` → `math.fmod` | `math.mod` deprecated | `math.mod` removed | Must use `math.fmod` or `%` |
| String metatable | No `__len` on strings | `__len` works on strings | Rare edge cases |
| `table.pack` / `table.unpack` | In `table` lib | Same | Compatible |
| `_ENV` upvalue | Required | Same | Compatible |
| `load()` mode | No `mode` param | `mode` param for text/binary | Patch call sites |
| `coroutine.yield` | Cannot yield across `pcall` boundary | Can yield across `pcall` boundary | Better behavior |
| UTF-8 | No utf8 library | `utf8.*` library | New feature, no breakage |

### B. File Size Budget

| Component | Target Size | Actual (estimate) |
|-----------|-------------|-------------------|
| `libluajava.so` (arm64-v8a) | ≤ 1.5 MB | — |
| `libgl.so` (arm64-v8a) | ≤ 500 KB | — |
| `engine.dex` | ≤ 4 MB | — |
| Lua library files (`.lua`) | ≤ 500 KB | — |
| **Total increase** | **≤ 6.5 MB** | **Baseline: +4.2 MB** |

### C. Dependency Map

```
EngineBridge.kt
  └── EngineActivity.java
        └── LuaActivity.java (from engine.dex)
              ├── LuaState.java (com.luajava)
              ├── LuaJavaAPI.java
              ├── LuaObject.java
              ├── LuaTable.java
              ├── libluajava.so (via System.loadLibrary)
              │     └── lua5.3.3 C runtime
              │     └── luajava JNI bridge
              ├── libgl.so (via System.loadLibrary)
              │     └── OpenGL ES bindings
              ├── import.lua
              ├── loadlayout.lua
              └── LuaApplication context hooks
```

### D. Glossary

| Term | Definition |
|------|------------|
| **LuaJ** | Pure Java Lua 5.2 VM (`org.luaj.vm2`). Being replaced. |
| **LuaJava** | JNI bridge library (`com.luajava`). Provides `LuaState`, `LuaObject`, `LuaJavaAPI`. |
| **AndroLua Pro** | nirenr's open-source Lua IDE for Android. Source of the target engine. |
| **Engine DEX** | A standalone `.dex` file containing all AndroLua runtime classes. |
| **EngineBridge** | Kotlin class that manages the Intent-based execution of Lua scripts. |
| **EngineActivity** | Thin Java `Activity` that extends `LuaActivity` and serves as the entry point. |
| **loadlayout** | Lua function that inflates Android XML layouts and binds Lua callbacks. |
| **CGLIB** | Code generation library used by AndroLua Pro for dynamic proxy creation. |
| **ACC** | Acceptance Criteria. 8 criteria that must pass for migration acceptance. |
| **WP** | Work Package. 7 packages covering all migration work. |

---

*Document version 1.0 — Generated from audit of AndroLua-master, AndroLua_pro-master, AndroLuaX-main, and luaj-master in /docs.*
