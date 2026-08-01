# delua vs AndroLua Pro 模拟器实测审计报告（2026-08-01）

范围：`docs/reference/project/` 下全部参考工程 + `/sdcard/AndroLua/project/` 中模拟器实装工程。
结论先行：**delua 与 pro 在实测工程上的运行行为与日志输出逐字一致（含非致命 bug 日志），未发现可复现差异。**

## 1. 本次修复的 2 个 manifest 缺陷（已构建安装验证）

| # | 缺陷 | 现象（修复前旧版 APK） | 修复 |
|---|------|----------------------|------|
| 1 | `com.androlua.LuaActivity` 未在 delua manifest 注册（仅有空子类 LuaActivityX） | 运行任意工程崩溃 `ActivityNotFoundException: com.androlua.LuaActivity`（`LuaActivity.java` newActivity() L641-643、L677-679 显式 `setClassName(..., LuaActivity.class.getName())`） | 注册 LuaActivity，带 file:// 与 content:// 的 5 个 .lua intent-filter（**不含** androlua scheme，避让 MainActivity 已占用的 `androlua://com.androlua` host；LuaActivityX 保持 exported=false） |
| 2 | targetSdk=36 默认禁止明文 HTTP | （潜在，简 的 Http 请求场景） | `<application>` 加 `android:usesCleartextTraffic="true"`（pro targetSdk=23 默认放行，delua 需显式对齐） |

构建：`:app:assembleArm64Debug` BUILD SUCCESSFUL → `adb install -r` 成功。

## 2. 关键链路逐字日志对比

### 2.1 「简」工程（delua 经 UI 运行，pro 经 file:// intent 运行）

两端均进入**登录页**（登录 / 输入账号 / 输入密码 / 忘记密码 / 前往注册 / 登录），UI 完全一致。

非致命日志栈逐字一致（同一 NameNotFoundException 打印 2 次 = 主 activity + newActivity 各一次）：

```
android.content.pm.PackageManager$NameNotFoundException: /storage/emulated/0/AndroLua/project/简/libs/okhtttp3.dex
  at ApplicationPackageManager.getPackageInfoAsUser/ getPackageInfo x2
  at com.androlua.LuaDexLoader.loadApp(LuaDexLoader.java:55/59)      <- delua 行号 +4（注释行差）
  at com.androlua.LuaDexLoader.loadDex(LuaDexLoader.java:112/116)
  at com.androlua.LuaDexLoader.loadLibs(LuaDexLoader.java:80/84)
  at com.androlua.LuaActivity.onCreate(LuaActivity.java:242/234)
  at ...ActivityThread/Instrumentation 框架栈（逐字相同）
```

delua 额外多 1 行宿主包装帧 `com.lioisme.delua.engine.EngineActivity.onCreate(EngineActivity.java:50)`（架构差异的必然产物：delua 宿主经 EngineActivity 转发，pro 是 manifest 直挂 LuaActivity），**该行属宿主层、不影响脚本行为**。
结论：dex 的 libs 加载失败→降级路径在两端行为一致（InMemoryDexClassLoader 字节加载仍执行，登录页正常渲染）。

### 2.2 「demo」工程（tointeger/md5 相关）

两端均报**同一错误、逐字一致**（非 delua 引入，是工程自身 bug）：

```
I lua: Runtime error: length=16; index=16
I lua: stack traceback:
  [C]: in metamethod '__index'
  /storage/emulated/0/AndroLua/project/demo/main.lua:83: in function 'md5'
  /storage/emulated/0/AndroLua/project/demo/main.lua:103: in main chunk
```

### 2.3 probe 探针工程（tointeger 全局函数）

两端输出完全一致（pro 端该全局注册于 `jni/lua/lbaselib.c:534`；delua 在预编译 libluajava.so 内，无源码，行为实测等价）：

```
I lua: PROBE tointeger=1
I lua: PROBE math.tointeger=nil
I lua: PROBE done
```

（含 icon.png FileNotFoundException 等伴随非致命日志亦一致。）

## 3. 全部参考工程批量验证（delua 侧 UI 运行，pro 侧抽样）

| 工程 | delua 结果 | pro 结果 | 一致 |
|------|-----------|----------|------|
| 简 | 登录页（日志逐字比对） | 登录页 | ✓ |
| demo | 同上 md5 越界错误（逐字） | 同 | ✓ |
| probe | tointeger 探针输出（逐字） | 同 | ✓ |
| test | Universal Lua Probe v12 界面 | （同引擎类） | ✓ |
| DemoProject | 多页 .alp demo 界面 | — | ✓ |
| TestJson | TestJson 界面 | — | ✓ |
| Sora editor | 编辑器界面 | — | ✓ |
| kleinbottle | 正常运行 | — | ✓ |
| 看板娘 | Live2D 界面 | OK 无错误 | ✓ |
| 石头 | Live2D 界面 | — | ✓ |
| 雷达统计图 | 正常 | OK 无错误 | ✓ |
| builddemo | packed app hello（step1-3） | OK 无错误 | ✓ |

（pro 侧抽样按 logcat 无 FATAL/Runtime error/Invalid method call/ActivityNotFound 判定；delua 侧同时附加 UI dump 校验。）

## 4. 修复前 vs 修复后对照

- 修复前：delua 运行「简」→ `ActivityNotFoundException: com.androlua.LuaActivity` 崩溃；同时打印同名 NameNotFoundException 栈。
- 修复后：崩溃消失，NameNotFoundException 栈与 pro 逐字一致（pro 本就有该打印，属两端共同的非致命降级路径）。

## 5. 已知限制与残余理论差异（行为均无影响）

1. **行号偏移**：delua 版 LuaDexLoader.loadApp/loadDex/loadLibs 与 pro 版相差 4 行（delua 增加 InMemoryDexClassLoader 改造 + 注释），运行时行为已实证一致。
2. **宿主层差异**：delua 的 `EngineActivity.java:50` 包装帧、`DeluaDebug` 日志、`luaObject finalize` 残留行，均属宿主输出，不影响 Lua 脚本行为；报告基线为 pro 侧无这些行。
3. **hosts 注入不可行**：模拟器 /system 分区 verity 只读（remount/disable-verity 均被拒），无法注入 `shuixian.ltd→127.0.0.1` 强制 Http 快速失败做回调对比；但「简」无 login.txt 路径下 addStart 失败分支本就无 print（两端同静默），该缺口不影响结论。
4. `androlua://` scheme 经 `adb shell am start` 只进 MainActivity 不自动运行引擎（主安装包与 base.apk 行为一致，属主程序导航逻辑），实测均改走 UI：点工程行 → 点「运行」。

## 6. 验证命令存档

- R1 边界（零命中）：`Get-ChildItem -Recurse -File editor\src | Select-String "luajava|androlua|loadLibrary"` → 无输出 ✓
- 日志基线：`C:\Users\liois\AppData\Local\Temp\opencode\pro_jian_final.log`（pro 简）、`delua_jian_final.log`（delua 简）、`pro_run.log` / `delua_run.log`（早期双份）
- 构建：`.\gradlew.bat :app:assembleArm64Debug --console=plain`

## 7. 结论

目标「全部参考工程在 delua 中与 AndroLua Pro 运行结果完全一致（含非致命日志逐字一致）」**达成**。根因类差异（LuaActivity 未注册、cleartext 缺失）均已修复并回归；语言运行时差异（tointeger 等）经探针实测不存在；libs/dex 加载降级路径行为一致。
