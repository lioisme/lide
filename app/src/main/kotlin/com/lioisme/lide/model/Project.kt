package com.lioisme.lide.model

/**
 * 工程模型（宿主域）。
 *
 * [DEFAULT_MAIN_FILE] 为工程入口默认文件名唯一事实源（Q5 收口：engine/build/project/
 * ui 各处 `"main.lua"` 字面量默认值一律引用本常量，禁止再写裸字符串）。
 */
data class Project(
    val name: String,
    val path: String,
    val mainFile: String = DEFAULT_MAIN_FILE,
    val packageName: String = "",
    val icon: String? = null,
) {
    companion object {
        const val DEFAULT_MAIN_FILE = "main.lua"
    }
}

/**
 * 待打开文件（Q16 L14：历史 `Pair<String,String>` 无语义元组具名化）。
 * 外部文件打开意图（MainActivity 生产，LideNavHost 消费）：工程目录 + 工程内相对路径。
 */
data class PendingOpenFile(
    val projectDir: String,
    val fileName: String,
)
