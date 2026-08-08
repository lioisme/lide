# P0 运行时基座 R8 规则（runtime buildType 专用，与宿主 proguard-rules.pro 独立）。
#
# 目标：白名单 keep「引擎 + Lua 可见 API 面」，R8 从 manifest 可达性出发把
# IDE 类（com.lioisme.lide.ui.** / editor / build / project 等）整体剥离，
# 使打包产物 dex 不再携带 IDE 代码（需求 1：androlua 运行环境与 LIDE 解耦）。
#
# 刻意不包含宿主 proguard-rules.pro 的「-keep class com.lioisme.lide.** { *; }」。
# 若新增引擎侧被 Lua 按类名反射引用的类，须同时登记 luaApiSurface（三处同步约定）。

# 跨 dex 双 R8 命名隔离（2026-08-08 模拟器实测崩溃）：
# 引擎 dex（runtime 变体 R8 产物）与宿主 IDE dex（release 变体 R8 产物）混入同一
# classloader，两套 R8 各自生成同名短混淆类（例：两边都叫 b0.c——宿主=GridSlotCache，
# 引擎=compose outline），引擎 keep 类字节码交叉引用被宿主同名类遮蔽 →
# NoSuchMethodError。本规则把引擎侧被混淆（非 keep）的类统一重定位到
# lide_runtime_ 前缀命名空间（keep 类名不动），与宿主短名混淆区零重叠。
-repackageclasses 'lide_runtime_'

# luajava - libluajava.so 的 JNI 桥接层（LuaState/LuaJavaAPI/LuaObject 等）。
# native 侧按字面量类名/方法名绑定，R8 引用图不可见，必须 { *; }。
-keep class com.luajava.** { *; }

# androlua - 运行时反射 / 动态 dex 按类名引用（LuaActivity/LuaApplication 等）。
-keep class com.androlua.** { *; }

# android.content.FileProvider - 引擎自带同名支持类副本（pro 原版收录，
# 由 manifest 按类名注册、ActivityThread.installProvider 运行时反射加载）。
# attachInfo 以编译期调用点构造 SimplePathStrategy(String)，R8 视内部类无直接
# 引用而混淆/收缩构造 → NoSuchMethodError（2026-08-08 模拟器实测崩溃）。
# 内部类须单列（`{ *; }` 不递归内部类），含构造器/接口保留。
-keep class android.content.FileProvider { *; }
-keep class android.content.FileProvider$* { *; }

# 引擎核心 keep 面（EngineActivity 等宿主 IDE 类不在此列：P0 源码组织五需求后
# 引擎页归位 app 主源（只进 IDE dex，打包产物经 P0-7 直用宿主根 dex），
# runtime 变体按 manifest 可达性剥离之——见 verifyRuntimeSurface 剥离断言）。

# ── Lua 可见 API 面（与宿主 proguard-rules.pro / luaApiSurface 同步）────────
-keep class android.widget.** { *; }
-keep class android.app.AlertDialogBuilder { *; }
-keep class android.app.FloatWindow { *; }
-keep class com.nirenr.** { *; }
-keep class com.android.cglib.** { *; }

# 反射所需的属性信息
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
