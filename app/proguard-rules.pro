# 注：LuaJ（org.luaj.*）已于引擎迁移时移除（Lua 5.3 AndroLua C fork 取代），
# 仓库无此包，无对应 keep 规则（2026-08-06 P3-5 清理）。

# ── 引擎并入单 dex 集（2026-08-09 源码组织三需求）────────────────
# :androlua 以 implementation 并入宿主单一 dex 集（根目录 classes*.dex，无任何
# 拆分/注入/标记），R8 keep 面合并至此（原 src/runtime/runtime-rules.pro 已随
# runtime buildType / engineDexExtract / injectEngineDex 一并删除）。Lua 脚本与
# 插件 dex 按类名反射引用引擎类，必须整体 keep；不存在跨 dex 双 R8 命名冲突，
# 原 -repackageclasses 'lide_runtime_' 不再需要。
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

# ── 引擎 keep 面（原 src/runtime/runtime-rules.pro 并入；维护约定已收敛，
#    2026-08-09：唯一事实源 = app/engine-surface.txt，本段 keep 覆盖由
#    EngineSurfaceConsistencyTest 断言（通配/单列命中），不再手写第三份清单）────────

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

# ── Lua 可见 API 面（清单见 app/engine-surface.txt，本段为其 R8 keep 覆盖）────────
-keep class android.widget.** { *; }
-keep class android.app.AlertDialogBuilder { *; }
-keep class android.app.FloatWindow { *; }
-keep class com.nirenr.** { *; }
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
