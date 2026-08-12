package com.lioisme.lide.editor

/**
 * 编辑器规模上限常量单一事实源（D2，2026-08-12）：原 200 KB 阈值在
 * [LuaEditor]（大文件降级矩阵）与 [LuaFormatter]（格式化长度上限）各写一份，
 * 改动易漏。阈值语义与降级行为见 LuaEditor「大文件降级」KDoc。
 */
object EditorLimits {
    /** 大文件阈值（字节）：超过后关闭括号高亮 / 当前块 / 自动换行，并跳过行级诊断。 */
    const val LARGE_FILE_THRESHOLD = 200 * 1024

    /** 格式化长度上限（字节）：超过直接原样返回，不做格式化。 */
    const val MAX_FORMAT_LENGTH = LARGE_FILE_THRESHOLD
}
