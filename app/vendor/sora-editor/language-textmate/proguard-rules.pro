# TextMate grammar / language-configuration JSON 由 Gson 反射反序列化（无 @SerializedName
# 全量覆盖，字段按名反射绑定）。R8 class-merging 会把 model 类改写为抽象类，导致
# Gson 报 "Abstract classes can't be instantiated!"（详见
# https://github.com/google/gson/blob/main/Troubleshooting.md#r8-abstract-class）。
# 模型类体积小，整体 keep（类 + 字段名）以保住反射字段绑定。
-keep class org.eclipse.tm4e.languageconfiguration.internal.model.** { *; }

# LanguageDefinitionReader$LanguageDefinitionList：languages.json 反序列化容器类。
-keep class io.github.rosemoe.sora.langs.textmate.registry.reader.** { *; }