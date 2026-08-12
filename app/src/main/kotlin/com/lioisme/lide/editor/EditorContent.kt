package com.lioisme.lide.editor

import io.github.rosemoe.sora.text.Content

/**
 * 不透明文本句柄（sora 门面契约，2026-08-11 解耦审计 B 级收口）。
 *
 * 宿主（session / ui）传递文本实体只经本句柄，禁止直接 import
 * `io.github.rosemoe.sora.text.Content`（代码审查红线，见 docs/kb/ARCHITECTURE.md）。
 *
 * 契约（对齐 AGENTS.md 编辑器关键约定）：
 * - **构造**：宿主经 [of] 创建（文件读取在 IO 线程）；内部持有 [Content]——
 *   undo 栈随之保留，`setText(Content)` 复用路径零 O(n) 重建语义不变；
 * - **快照**：经 [snapshot]（后台线程安全，内部 ReadWriteLock）；
 * - **raw 访问**：仅同模块内部（session 门面实现），不进入公共 API 面。
 */
class EditorContent internal constructor(
    internal val raw: Content,
) {
    /** 文本快照（后台线程安全）。 */
    fun snapshot(): String = raw.toString()

    companion object {
        /** 由文本构造句柄（宿主 IO 线程调用）。 */
        fun of(text: String): EditorContent = EditorContent(Content(text))
    }
}
