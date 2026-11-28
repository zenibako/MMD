package com.mudita.mmd.samples.hermes

/**
 * A small Markdown reader for agent replies, covering what they use on a phone: emphasis,
 * code, headings, lists, quotes, links, rules and (as plain aligned text) tables. Pure Kotlin;
 * MarkdownText.kt draws it in black and white.
 */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    /** One list item; [marker] is "•" or the item's number ("2."); [depth] 0 for top level. */
    data class ListItem(val marker: String, val spans: List<MdSpan>, val depth: Int = 0) : MdBlock

    data class Quote(val spans: List<MdSpan>) : MdBlock

    data class Code(val text: String) : MdBlock

    /** A table, drawn as monospace text with aligned columns (see [tableText]). */
    data class Table(val rows: List<List<String>>) : MdBlock

    data object Rule : MdBlock
}

/** A run of text with one style. [url] makes it a link. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

private val FENCE = Regex("""^\s*(```|~~~)""")
private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

/** Reads [source] into blocks. An unclosed code fence runs to the end. */
fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val quote = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
            paragraph.clear()
        }
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) {
            blocks += MdBlock.Quote(parseInline(quote.joinToString("\n").trim()))
            quote.clear()
        }
    }
    fun flush() {
        flushParagraph()
        flushQuote()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.find(line)
        when {
            fence != null -> {
                flush()
                val marker = fence.groupValues[1]
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) code += lines[i++]
                blocks += MdBlock.Code(code.joinToString("\n").trimEnd())
            }
            line.isBlank() -> flush()
            RULE.matches(line) && paragraph.isEmpty() -> {
                flush()
                blocks += MdBlock.Rule
            }
            HEADING.matches(line) -> {
                flush()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2]))
            }
            line.trimStart().startsWith("|") && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) -> {
                flush()
                val rows = mutableListOf(tableCells(line))
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) rows += tableCells(lines[i++])
                blocks += MdBlock.Table(rows)
                continue
            }
            BULLET.matches(line) -> {
                flush()
                val m = BULLET.find(line)!!
                blocks += MdBlock.ListItem("•", parseInline(m.groupValues[2]), m.groupValues[1].length / 2)
            }
            NUMBERED.matches(line) -> {
                flush()
                val m = NUMBERED.find(line)!!
                blocks += MdBlock.ListItem("${m.groupValues[2]}.", parseInline(m.groupValues[3]), m.groupValues[1].length / 2)
            }
            QUOTE.matches(line) -> {
                flushParagraph()
                quote += QUOTE.find(line)!!.groupValues[1]
            }
            // A line under a list item, indented, continues that item.
            blocks.lastOrNull() is MdBlock.ListItem && paragraph.isEmpty() && quote.isEmpty() && line.startsWith("  ") -> {
                val item = blocks.removeAt(blocks.lastIndex) as MdBlock.ListItem
                blocks += item.copy(spans = mergeSpans(item.spans + MdSpan("\n") + parseInline(line.trim())))
            }
            else -> {
                flushQuote()
                paragraph += line
            }
        }
        i++
    }
    flush()
    return blocks
}

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { stripInline(it.trim()) }

/** Plain text of a cell: emphasis and code marks dropped. */
private fun stripInline(text: String): String = parseInline(text).joinToString("") { it.text }

/** A table as aligned columns (cells padded to the widest in each column), rows one per line. */
fun tableText(rows: List<List<String>>): String {
    val columns = rows.maxOfOrNull { it.size } ?: 0
    val widths = (0 until columns).map { c -> rows.maxOf { it.getOrNull(c)?.length ?: 0 } }
    return rows.joinToString("\n") { row ->
        (0 until columns).joinToString("  ") { c -> (row.getOrNull(c) ?: "").padEnd(widths[c]) }.trimEnd()
    }
}

private val INLINE = Regex(
    listOf(
        """`([^`\n]+)`""", // 1 code
        """\[([^\]\n]+)]\((\S+?)\)""", // 2 text, 3 url
        """\*\*(.+?)\*\*""", // 4 bold
        """__(.+?)__""", // 5 bold
        """(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])""", // 6 italic
        """(?<![\w_])_(?![\s_])(.+?)(?<![\s_])_(?![\w_])""", // 7 italic
        """(https?://[^\s<>()]+[^\s<>().,;:!?'"])""", // 8 bare link
    ).joinToString("|"),
    RegexOption.DOT_MATCHES_ALL,
)

/** Reads inline marks in [text] into styled spans, adjacent spans of one style merged. */
fun parseInline(text: String, bold: Boolean = false, italic: Boolean = false, url: String? = null): List<MdSpan> {
    val out = mutableListOf<MdSpan>()
    var at = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first > at) out += MdSpan(text.substring(at, m.range.first), bold, italic, url = url)
        val g = m.groupValues
        out += when {
            g[1].isNotEmpty() -> listOf(MdSpan(g[1], code = true, url = url))
            g[2].isNotEmpty() -> parseInline(g[2], bold, italic, g[3])
            g[4].isNotEmpty() -> parseInline(g[4], true, italic, url)
            g[5].isNotEmpty() -> parseInline(g[5], true, italic, url)
            g[6].isNotEmpty() -> parseInline(g[6], bold, true, url)
            g[7].isNotEmpty() -> parseInline(g[7], bold, true, url)
            else -> listOf(MdSpan(g[8], bold, italic, url = g[8]))
        }
        at = m.range.last + 1
    }
    if (at < text.length) out += MdSpan(text.substring(at), bold, italic, url = url)
    return mergeSpans(out)
}

/** Joins neighbouring spans that share a style. */
private fun mergeSpans(spans: List<MdSpan>): List<MdSpan> = spans.fold(mutableListOf()) { acc, s ->
    val last = acc.lastOrNull()
    if (last != null && last.copy(text = "") == s.copy(text = "")) acc[acc.lastIndex] = last.copy(text = last.text + s.text) else acc += s
    acc
}
