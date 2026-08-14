package com.lioisme.lide.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.oshai.kotlinlogging.Level

/**
 * LogcatParser 契约测试（2026-08-14 LOGCAT L1）。
 *
 * 覆盖 threadtime 标准行 / 定宽填充 tag / 含冒号 tag（首 `": "` 切分语义）/
 * fatal 级别回落 / 空消息 / 多行续行 / 缓冲分节头 / 乱行兜底。
 */
class LogcatParserTest {

    private fun parseEntry(line: String): LogcatParser.Result.Entry =
        LogcatParser.parseLine(line) as LogcatParser.Result.Entry

    @Test
    fun `标准 threadtime 行解析`() {
        val entry =
            parseEntry("08-14 10:22:33.123  1234  5678  I lua: hello world")
        assertEquals("08-14 10:22:33.123", entry.timeText)
        assertEquals(1234, entry.pid)
        assertEquals(5678, entry.tid)
        assertEquals(Level.INFO, entry.level)
        assertEquals("lua", entry.tag)
        assertEquals("hello world", entry.message)
    }

    @Test
    fun `定宽填充 tag 两侧空白被裁剪`() {
        val entry = parseEntry("08-14 10:22:33.123  1234  5678  E lua              : boom")
        assertEquals("lua", entry.tag)
        assertEquals("boom", entry.message)
    }

    @Test
    fun `消息内含冒号仅首个分隔符生效`() {
        val entry = parseEntry("08-14 10:22:33.123  1234  5678  I System.out: foo: bar")
        assertEquals("System.out", entry.tag)
        assertEquals("foo: bar", entry.message)
    }

    @Test
    fun `含冒号 tag 按首分隔符切分语义`() {
        // 引擎 LuaJavaAPI tag "javaGc: " 输出为 "javaGc:  : msg"（tag 自带冒号 + 标准分隔符），
        // 首 ": " 切分 → tag=javaGc，消息以 " : " 开头（logcat 文本格式固有歧义，文档化接受）。
        val entry = parseEntry("08-14 10:22:33.123  1234  5678  I javaGc:  : leak")
        assertEquals("javaGc", entry.tag)
        assertEquals(" : leak", entry.message)
    }

    @Test
    fun `级别字符全量映射`() {
        assertEquals(Level.TRACE, parseEntry("08-14 10:22:33.123  1  2  V t: m").level)
        assertEquals(Level.DEBUG, parseEntry("08-14 10:22:33.123  1  2  D t: m").level)
        assertEquals(Level.INFO, parseEntry("08-14 10:22:33.123  1  2  I t: m").level)
        assertEquals(Level.WARN, parseEntry("08-14 10:22:33.123  1  2  W t: m").level)
        assertEquals(Level.ERROR, parseEntry("08-14 10:22:33.123  1  2  E t: m").level)
    }

    @Test
    fun `标准整行格式化`() {
        // 长按复制契约：Android Studio Logcat / pro `logcat -v long` 同款
        // `MM-DD HH:MM:SS.mmm PID TID I/tag: message`。
        val entry =
            LogEntry(
                id = 1,
                timeText = "08-14 10:22:33.123",
                pid = 1234,
                tid = 5678,
                level = Level.INFO,
                tag = "lua",
                message = "hello world",
            )
        assertEquals("08-14 10:22:33.123 1234 5678 I/lua: hello world", entry.formatted())
    }

    @Test
    fun `fatal 级别回落 ERROR`() {
        val entry = parseEntry("08-14 10:22:33.123  1  2  F t: m")
        assertEquals(Level.ERROR, entry.level)
        assertSame(Level.ERROR, logcatSymbolToLevel('F'))
    }

    @Test
    fun `空消息解析`() {
        val entry = parseEntry("08-14 10:22:33.123  1  2  I lua: ")
        assertEquals("lua", entry.tag)
        assertEquals("", entry.message)
    }

    @Test
    fun `无分隔符行 tag 兜底整个剩余`() {
        val entry = parseEntry("08-14 10:22:33.123  1  2  I baretag")
        assertEquals("baretag", entry.tag)
        assertEquals("", entry.message)
    }

    @Test
    fun `多行消息续行返回 Unparsed`() {
        val result = LogcatParser.parseLine("    at com.example.foo(bar.lua:42)")
        assertTrue(result is LogcatParser.Result.Unparsed)
        assertEquals(
            "    at com.example.foo(bar.lua:42)",
            (result as LogcatParser.Result.Unparsed).text,
        )
    }

    @Test
    fun `缓冲分节头丢弃`() {
        assertSame(LogcatParser.Result.Header, LogcatParser.parseLine("----- beginning of main"))
        assertSame(LogcatParser.Result.Header, LogcatParser.parseLine("--------- beginning of crash"))
        assertSame(LogcatParser.Result.Header, LogcatParser.parseLine(""))
        assertSame(LogcatParser.Result.Header, LogcatParser.parseLine("   "))
    }

    @Test
    fun `乱行返回 Unparsed`() {
        assertTrue(LogcatParser.parseLine("garbage line") is LogcatParser.Result.Unparsed)
    }

    @Test
    fun `中文消息保留`() {
        val entry = parseEntry("08-14 10:22:33.123  1  2  I lua: 你好，世界")
        assertEquals("你好，世界", entry.message)
    }
}
