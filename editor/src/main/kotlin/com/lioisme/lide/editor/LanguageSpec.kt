package com.lioisme.lide.editor

/**
 * 单个语言的定义（对应 assets/textmate/languages.json 的单项）。
 */
data class LanguageSpec(
    val name: String,
    val scopeName: String,
    val grammar: String,
    val languageConfiguration: String,
)
