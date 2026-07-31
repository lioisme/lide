package com.lioisme.delua.engine

/**
 * 引擎结果解析（纯 Kotlin，JVM 可单测）。
 *
 * 输入：EngineActivity 经 ActivityResult 回传的输出文本。
 * 职责：错误判定、`路径:行号:` 定位、`nil global 'X'` import 建议提取。
 */
object EngineResultParser {

    /** 命中的错误位置（文件相对工程目录；行号为 1 基，编辑器内部转 0 基）。 */
    data class ErrorLocation(val file: String, val line: Int)

    /**
     * 从输出文本提取错误位置。Lua 5.3 的错误消息首行定位格式为 `路径:行号:`
     * （报错点），取第一次出现即真正出错位置；traceback 栈帧中的行号为调用点，
     * 不用于跳行。找不到返回 null。
     */
    fun findErrorLocation(output: String?): ErrorLocation? {
        if (output.isNullOrEmpty()) return null
        // 匹配 `...:NN:`（NN ≥ 1）。
        val regex = Regex("([^\\s:]+):(\\d+):")
        val match = regex.find(output) ?: return null
        val line = match.groupValues[2].toIntOrNull() ?: return null
        if (line < 1) return null
        val file = match.groupValues[1].substringAfterLast('/')
        return ErrorLocation(file, line)
    }

    /** 提取 `nil global 'X'` / `attempt to index a nil value (global 'X')` 的全局名。 */
    fun findNilGlobal(output: String?): String? {
        if (output.isNullOrEmpty()) return null
        val regex = Regex("\\(global '([^']+)'\\)")
        val match = regex.find(output) ?: return null
        return match.groupValues[1]
    }

    /** 错误标记来自引擎侧（[com.lioisme.delua.engine.EngineActivity]），此处仅按文本兜底。 */
    fun isErrorOutput(output: String?): Boolean {
        if (output.isNullOrEmpty()) return false
        val prefix = Regex("^(Runtime error|Syntax error|error error|Out of memory|GC error|Yield error|Unknown error \\d+): ", RegexOption.MULTILINE)
        return prefix.containsMatchIn(output)
    }
}
