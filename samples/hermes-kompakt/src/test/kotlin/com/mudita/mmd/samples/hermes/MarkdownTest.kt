package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {
    @Test fun plainTextIsOneSpan() {
        assertEquals(listOf(MdSpan("Just text, 2 * 3 = 6")), parseInline("Just text, 2 * 3 = 6"))
    }

    @Test fun boldItalicAndCode() {
        assertEquals(
            listOf(MdSpan("Use "), MdSpan("git", code = true), MdSpan(" with "), MdSpan("care", bold = true), MdSpan(" and "), MdSpan("taste", italic = true), MdSpan(".")),
            parseInline("Use `git` with **care** and *taste*."),
        )
    }

    @Test fun italicInsideBold() {
        assertEquals(listOf(MdSpan("very ", bold = true), MdSpan("much", bold = true, italic = true)), parseInline("**very _much_**"))
    }

    @Test fun snakeCaseIsNotItalic() {
        assertEquals(listOf(MdSpan("call my_helper_fn now")), parseInline("call my_helper_fn now"))
    }

    @Test fun linksAndBareUrls() {
        assertEquals(
            listOf(MdSpan("See "), MdSpan("the docs", url = "https://x.io/a"), MdSpan(" or "), MdSpan("https://y.io/b", url = "https://y.io/b"), MdSpan(".")),
            parseInline("See [the docs](https://x.io/a) or https://y.io/b."),
        )
    }
}

class BlockMarkdownTest {
    @Test fun paragraphsHeadingsAndLists() {
        val blocks = parseMarkdown("# Plan\n\nFirst line\nsecond line\n\n- one\n- **two**\n  1. nested\n")
        assertEquals(
            listOf(
                MdBlock.Heading(1, listOf(MdSpan("Plan"))),
                MdBlock.Paragraph(listOf(MdSpan("First line\nsecond line"))),
                MdBlock.ListItem("•", listOf(MdSpan("one"))),
                MdBlock.ListItem("•", listOf(MdSpan("two", bold = true))),
                MdBlock.ListItem("1.", listOf(MdSpan("nested")), depth = 1),
            ),
            blocks,
        )
    }

    @Test fun codeBlockKeepsItsTextVerbatim() {
        assertEquals(
            listOf(MdBlock.Paragraph(listOf(MdSpan("Run:"))), MdBlock.Code("echo **hi**\n\nls -la")),
            parseMarkdown("Run:\n```bash\necho **hi**\n\nls -la\n```"),
        )
    }

    @Test fun quotesMergeConsecutiveLines() {
        assertEquals(listOf(MdBlock.Quote(listOf(MdSpan("a\nb")))), parseMarkdown("> a\n> b"))
    }

    @Test fun tablesBecomeAlignedText() {
        val blocks = parseMarkdown("| Name | Size |\n|---|---:|\n| **a** | 1 |\n| bb | 22 |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf(listOf("Name", "Size"), listOf("a", "1"), listOf("bb", "22")), table.rows)
        assertEquals("Name  Size\na     1\nbb    22", tableText(table.rows))
    }

    @Test fun ruleAndContinuationLines() {
        assertEquals(
            listOf(MdBlock.ListItem("•", listOf(MdSpan("item\nmore"))), MdBlock.Rule),
            parseMarkdown("- item\n  more\n\n---"),
        )
    }
}

class TextSizeTest {
    @Test fun unknownKeysFallBackToDefault() {
        assertEquals(TextSize.LARGE, TextSize.fromKey("LARGE"))
        assertEquals(TextSize.DEFAULT, TextSize.fromKey("huge"))
        assertEquals(TextSize.DEFAULT, TextSize.fromKey(null))
    }
}
