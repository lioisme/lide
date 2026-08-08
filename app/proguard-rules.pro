# 注：LuaJ（org.luaj.*）已于引擎迁移时移除（Lua 5.3 AndroLua C fork 取代），
# 仓库无此包，无对应 keep 规则（2026-08-06 P3-5 清理）。

# ── 引擎 dex 单份化（2026-08-08，D-1 冻结结论落地）────────────────────
# 宿主不再打包任何引擎类（com.luajava.** / com.androlua.** /
# com.lioisme.lide.androlua.** / android.widget 扩展控件 / com.nirenr.** /
# com.android.cglib.** 等）：引擎类经 :app:injectEngineDex 注入为宿主 APK
# 根目录 classes2.dex（单一 classloader 加载），R8 保留面由
# src/runtime/runtime-rules.pro 维护。此处刻意不再保留引擎 keep 规则——
# 若误 keep，引擎类会随宿主 release dex 再度打包（双份引擎代码回归，
# verifyReleaseLuaSurface 反向断言会捕获）。
#
# 宿主 keep 面：Lua 脚本 / 插件 dex 可能按类名反射引用宿主类。
# 逐包枚举（com.lioisme.lide.** 会误匹配 androlua 引擎子树，禁止使用）。

# 宿主根类（manifest 入口）
-keep class com.lioisme.lide.LideApp { *; }
-keep class com.lioisme.lide.MainActivity { *; }

# 宿主功能包（IDE / 编辑器 / 打包 / 工程 / API 浏览器 / UI）
-keep class com.lioisme.lide.apibrowser.** { *; }
-keep class com.lioisme.lide.build.** { *; }
-keep class com.lioisme.lide.editor.** { *; }
-keep class com.lioisme.lide.engine.** { *; }
-keep class com.lioisme.lide.i18n.** { *; }
-keep class com.lioisme.lide.import.** { *; }
-keep class com.lioisme.lide.model.** { *; }
-keep class com.lioisme.lide.project.** { *; }
-keep class com.lioisme.lide.session.** { *; }
-keep class com.lioisme.lide.ui.** { *; }
-keep class com.lioisme.lide.util.** { *; }

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
