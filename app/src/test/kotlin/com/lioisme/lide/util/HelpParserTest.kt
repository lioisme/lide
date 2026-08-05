package com.lioisme.lide.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpParserTest {
    @Test
    fun `empty text yields no sections`() {
        assertTrue(HelpParser.parse("").isEmpty())
        assertTrue(HelpParser.parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `parses sections and preserves body lines`() {
        val raw =
            "@Getting Started@\n" +
                "Open a project from the list.\n" +
                "Run it with the play button.\n" +
                "\n" +
                "@Editor@\n" +
                "Files open as tabs.\n"
        val sections = HelpParser.parse(raw)
        assertEquals(2, sections.size)
        assertEquals("Getting Started", sections[0].title)
        assertEquals(listOf("Open a project from the list.", "Run it with the play button."), sections[0].lines)
        assertEquals("Editor", sections[1].title)
        assertEquals(listOf("Files open as tabs."), sections[1].lines)
    }

    @Test
    fun `header regex only matches standalone lines`() {
        val raw =
            "@Title@\n" +
                "inline @not a header@ stays in body\n" +
                "trailing @also fine@\n"
        val sections = HelpParser.parse(raw)
        assertEquals(1, sections.size)
        assertEquals("Title", sections[0].title)
        assertEquals(2, sections[0].lines.size)
    }

    @Test
    fun `crlf and cr line endings normalize`() {
        val raw = "@A@\r\nline1\r\n\r\n@B@\rline2\r"
        val sections = HelpParser.parse(raw)
        assertEquals(2, sections.size)
        assertEquals(listOf("line1"), sections[0].lines)
        assertEquals(listOf("line2"), sections[1].lines)
    }

    @Test
    fun `empty sections are dropped but preceding content kept`() {
        val raw = "@A@\n\nonly\n\n\n@B@\n"
        val sections = HelpParser.parse(raw)
        assertEquals(1, sections.size)
        assertEquals("A", sections[0].title)
        assertEquals(listOf("only"), sections[0].lines)
    }

    @Test
    fun `content before first header is ignored`() {
        val raw = "preamble without header\n@Real@\nbody\n"
        val sections = HelpParser.parse(raw)
        assertEquals(1, sections.size)
        assertEquals("Real", sections[0].title)
    }
}
