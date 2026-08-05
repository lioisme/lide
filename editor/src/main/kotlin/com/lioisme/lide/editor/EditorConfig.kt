package com.lioisme.lide.editor

/**
 * 编辑器视图配置（由宿主注入）。
 *
 * 与宿主的持久化设置解耦：本模块只定义渲染所需的纯值，
 * 持久化、默认值来源等属于 `:app` 的 [SettingsManager] 职责。
 */
data class EditorConfig(
    val wordWrap: Boolean = false,
    val showWhitespace: Boolean = false,
    val fontSize: Int = 14,
    val tabSize: Int = 4,
)
