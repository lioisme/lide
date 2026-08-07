# 注：LuaJ（org.luaj.*）已于引擎迁移时移除（Lua 5.3 AndroLua C fork 取代），
# 仓库无此包，无对应 keep 规则（2026-08-06 P3-5 清理）。

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

# ── apksig（工程打包签名）──────────────────────────────────────
# apksig 的 ASN.1 编码器（Asn1DerEncoder）对模型类做注解驱动反射
# （@Asn1Field/@Asn1Choice/@Asn1Tagging + getDeclaredFields），R8 混淆/
# 裁剪会删改模型类字段，反射空转抛 Asn1EncodingException，被
# V1SchemeSigner 无 cause 包装成 "Failed to encode signature block"——
# 曾导致 release 宿主（R8 开启）打包必败而 debug 正常（2026-08-07 定位）。
-keep class com.android.apksig.** { *; }

# ── sora-editor language-textmate（Gson 反射反序列化 model 类）────────────────
# language-textmate 的 languages.json / language-configuration.json 由 Gson 按字段名反射
# 反序列化：部分 model 类没有 @SerializedName，字段名混淆会静默丢字段；且 R8 class-merging
# 会把 model 类改写为抽象类，运行时 Gson 报 "Abstract classes can't be instantiated!"
# （见 https://github.com/google/gson/blob/main/Troubleshooting.md#r8-abstract-class，
# 真机 release 扫描 39 个 grammar 全部加载失败，见 EDITOR_ALL_LANGUAGES_SUPPORT_WORK_ORDER）。
-keep class org.eclipse.tm4e.languageconfiguration.internal.model.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.reader.** { *; }

# Joni 正则引擎（tm4e 的 RegExPattern 依赖）：R8 优化会破坏其 AST 内部类静态初始化
# （org.joni.ast.QuantifierNode <clinit> NPE / NoClassDefFoundError），整体 keep。
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
