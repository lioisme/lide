# LuaJ - keep as-is (unmodified core, 脚本通过 luajava 按类名引用)
-keep class org.luaj.** { *; }

# luajava - libluajava.so 的 JNI 桥接层（LuaState/LuaJavaAPI/LuaObject 等 16 类）。
# native 侧经 FindClass/GetStaticMethodID 按字面量类名与方法名绑定，R8 的 Java 引用图
# 看不到这些 JNI 字符串引用，若不 keep 会被整类删除或改名（曾导致引擎启动空白，见审计）。
# 必须 { *; }：native 可能调用任意成员；不得混淆类名与成员名。
-keep class com.luajava.** { *; }

# androlua - 运行时反射 / 动态 dex 按类名引用（LuaActivity、LuaApplication 等）
-keep class com.androlua.** { *; }
-keep class com.lioisme.lide.androlua.** { *; }

# 宿主应用类 - Lua 脚本 / 插件 dex 可能按类名反射引用
-keep class com.lioisme.lide.** { *; }

# ── Lua 可见 API 面（按类名引用，R8 引用图不可见）────────────────────
# androlua 框架把扩展类放在 android.* / com.nirenr.* 命名空间，Lua 脚本经
# luajava 以字符串类名 bindClass/import（如 loadlayout.lua 的
# bindClass("android.widget.ArrayListAdapter")、bin.lua 的 AlertDialogBuilder）。
# 未 keep 的类会被 R8 整类删除，工程运行到布局即报错（历史事故，见审计记录）。
# android.widget.** 仅匹配打进 APK 的自定义控件，框架类在设备侧不受影响。
-keep class android.widget.** { *; }
-keep class android.app.AlertDialogBuilder { *; }
-keep class android.app.FloatWindow { *; }
-keep class com.nirenr.** { *; }

# com.android.cglib（DexMaker）供 LuaEnhancer 在运行时生成并加载 dex：
# 生成字节码与 luajava 均按类名/方法名绑定，防混淆防裁剪。
-keep class com.android.cglib.** { *; }

# 反射所需的属性信息
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
