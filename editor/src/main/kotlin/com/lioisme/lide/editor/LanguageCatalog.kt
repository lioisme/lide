package com.lioisme.lide.editor

/**
 * 编辑器支持的 TextMate 语言目录：与 `editor/src/main/assets/textmate/languages.json`
 * 一一对应（39 种，含 lua）。
 *
 * 该文件是全部语言能力的唯一入口（R3/R4：纯 `:editor` 能力，宿主零依赖）：
 * - 资产路径经 [AssetsFileResolver] 以 `textmate/languages/...` 相对 APK assets 解析；
 * - [scopeName] 用于 `GrammarRegistry` 注册与 `TextMateLanguage` 装配；
 * - [grammar] / [languageConfiguration] 为 assets 相对路径，运行时经文件提供器读取。
 *
 * 注：grammar JSON 为 VS Code 系 TextMate 文法（详见 THIRD_PARTY 注记），
 * 通过 Squircle CE（Apache-2.0）引入，仅作纯静态文本资源，不涉及任何运行时语义。
 */
object LanguageCatalog {

    /** 与 languages.json 完全一致的目录项。 */
    val all: List<LanguageSpec> = listOf(
        spec("bat", "source.batchfile", "languages/bat/syntaxes/batchfile.tmLanguage.json", "languages/bat/language-configuration.json"),
        spec("c", "source.c", "languages/c/syntaxes/c.tmLanguage.json", "languages/c/language-configuration.json"),
        spec("clojure", "source.clojure", "languages/clojure/syntaxes/clojure.tmLanguage.json", "languages/clojure/language-configuration.json"),
        spec("cpp", "source.cpp", "languages/cpp/syntaxes/cpp.tmLanguage.json", "languages/cpp/language-configuration.json"),
        spec("csharp", "source.cs", "languages/csharp/syntaxes/csharp.tmLanguage.json", "languages/csharp/language-configuration.json"),
        spec("css", "source.css", "languages/css/syntaxes/css.tmLanguage.json", "languages/css/language-configuration.json"),
        spec("dart", "source.dart", "languages/dart/syntaxes/dart.tmLanguage.json", "languages/dart/language-configuration.json"),
        spec("dockerfile", "source.dockerfile", "languages/docker/syntaxes/docker.tmLanguage.json", "languages/docker/language-configuration.json"),
        spec("fortran", "source.fortran", "languages/fortran/syntaxes/fortran.tmLanguage.json", "languages/fortran/language-configuration.json"),
        spec("fsharp", "source.fsharp", "languages/fsharp/syntaxes/fsharp.tmLanguage.json", "languages/fsharp/language-configuration.json"),
        spec("go", "source.go", "languages/go/syntaxes/go.tmLanguage.json", "languages/go/language-configuration.json"),
        spec("groovy", "source.groovy", "languages/groovy/syntaxes/groovy.tmLanguage.json", "languages/groovy/language-configuration.json"),
        spec("html", "text.html.basic", "languages/html/syntaxes/html.tmLanguage.json", "languages/html/language-configuration.json"),
        spec("ini", "source.ini", "languages/ini/syntaxes/ini.tmLanguage.json", "languages/ini/language-configuration.json"),
        spec("java", "source.java", "languages/java/syntaxes/java.tmLanguage.json", "languages/java/language-configuration.json"),
        spec("javascript", "source.js", "languages/javascript/syntaxes/javascript.tmLanguage.json", "languages/javascript/language-configuration.json"),
        spec("json", "source.json", "languages/json/syntaxes/json.tmLanguage.json", "languages/json/language-configuration.json"),
        spec("julia", "source.julia", "languages/julia/syntaxes/julia.tmLanguage.json", "languages/julia/language-configuration.json"),
        spec("kotlin", "source.kotlin", "languages/kotlin/syntaxes/kotlin.tmLanguage.json", "languages/kotlin/language-configuration.json"),
        spec("latex", "text.tex", "languages/latex/syntaxes/tex.tmLanguage.json", "languages/latex/language-configuration.json"),
        spec("lisp", "source.lisp", "languages/lisp/syntaxes/lisp.tmLanguage.json", "languages/lisp/language-configuration.json"),
        spec("lua", "source.lua", "languages/lua/syntaxes/lua.tmLanguage.json", "languages/lua/language-configuration.json"),
        spec("makefile", "source.makefile", "languages/make/syntaxes/make.tmLanguage.json", "languages/make/language-configuration.json"),
        spec("markdown", "text.html.markdown", "languages/markdown/syntaxes/markdown.tmLanguage.json", "languages/markdown/language-configuration.json"),
        spec("perl", "source.perl", "languages/perl/syntaxes/perl.tmLanguage.json", "languages/perl/language-configuration.json"),
        spec("php", "source.php", "languages/php/syntaxes/php.tmLanguage.json", "languages/php/language-configuration.json"),
        spec("python", "source.python", "languages/python/syntaxes/python.tmLanguage.json", "languages/python/language-configuration.json"),
        spec("ruby", "source.ruby", "languages/ruby/syntaxes/ruby.tmLanguage.json", "languages/ruby/language-configuration.json"),
        spec("rust", "source.rust", "languages/rust/syntaxes/rust.tmLanguage.json", "languages/rust/language-configuration.json"),
        spec("shellscript", "source.shell", "languages/shell/syntaxes/shell.tmLanguage.json", "languages/shell/language-configuration.json"),
        spec("smali", "source.smali", "languages/smali/syntaxes/smali.tmLanguage.json", "languages/smali/language-configuration.json"),
        spec("sql", "source.sql", "languages/sql/syntaxes/sql.tmLanguage.json", "languages/sql/language-configuration.json"),
        spec("toml", "source.toml", "languages/toml/syntaxes/toml.tmLanguage.json", "languages/toml/language-configuration.json"),
        spec("typescript", "source.ts", "languages/typescript/syntaxes/typescript.tmLanguage.json", "languages/typescript/language-configuration.json"),
        spec("vb", "source.vb", "languages/vb/syntaxes/visualbasic.tmLanguage.json", "languages/vb/language-configuration.json"),
        spec("vue", "source.vue", "languages/vue/syntaxes/vue.tmLanguage.json", "languages/vue/language-configuration.json"),
        spec("xml", "text.xml", "languages/xml/syntaxes/xml.tmLanguage.json", "languages/xml/language-configuration.json"),
        spec("yaml", "source.yaml", "languages/yaml/syntaxes/yaml.tmLanguage.json", "languages/yaml/language-configuration.json"),
        spec("zig", "source.zig", "languages/zig/syntaxes/zig.tmLanguage.json", "languages/zig/language-configuration.json"),
    )

    private fun spec(
        name: String,
        scopeName: String,
        grammar: String,
        languageConfiguration: String,
    ) = LanguageSpec(name, scopeName, grammar, languageConfiguration)

    fun find(scopeName: String): LanguageSpec? = all.firstOrNull { it.scopeName == scopeName }

    fun findByName(name: String): LanguageSpec? = all.firstOrNull { it.name == name }
}