# P0 运行时基座 R8 规则（runtime buildType 专用，与宿主 proguard-rules.pro 独立）。
#
# 目标：白名单 keep「引擎 + Lua 可见 API 面」，R8 从 manifest 可达性出发把
# IDE 类（com.lioisme.lide.ui.** / editor / build / project 等）整体剥离，
# 使打包产物 dex 不再携带 IDE 代码（需求 1：androlua 运行环境与 LIDE 解耦）。
#
# 刻意不包含宿主 proguard-rules.pro 的「-keep class com.lioisme.lide.** { *; }」。
# 若新增引擎侧被 Lua 按类名反射引用的类，须同时登记 luaApiSurface（三处同步约定）。

# luajava - libluajava.so 的 JNI 桥接层（LuaState/LuaJavaAPI/LuaObject 等）。
# native 侧按字面量类名/方法名绑定，R8 引用图不可见，必须 { *; }。
-keep class com.luajava.** { *; }

# androlua - 运行时反射 / 动态 dex 按类名引用（LuaActivity/LuaApplication 等）。
-keep class com.androlua.** { *; }

# 引擎契约（EngineActivity 依赖；:androlua 模块内，与 IDE 无关）。
-keep class com.lioisme.lide.androlua.** { *; }

# 引擎页（产物 launcher）：LuaActivity 子类，经 manifest 引用，显式 keep 防误删。
-keep class com.lioisme.lide.engine.** { *; }

# ── Lua 可见 API 面（与宿主 proguard-rules.pro / luaApiSurface 同步）────────
-keep class android.widget.** { *; }
-keep class android.app.AlertDialogBuilder { *; }
-keep class android.app.FloatWindow { *; }
-keep class com.nirenr.** { *; }
-keep class com.android.cglib.** { *; }

# 反射所需的属性信息
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
