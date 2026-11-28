package com.mudita.mmd.samples.hermes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD

/**
 * An agent reply drawn from Markdown, black on white: bold in Lato Black, italic, monospace
 * code, code blocks in a 1 dp outline (wrapping, never scrolling sideways), headings a step
 * up, lists with a hanging indent, quotes behind a 2 dp rule, links underlined (tap opens the
 * browser), tables as aligned monospace text. [style] is the body style, already scaled.
 */
@Composable
fun MarkdownText(text: String, style: TextStyle) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Paragraph -> TextMMD(annotate(block.spans), style = style)
                is MdBlock.Heading -> TextMMD(
                    annotate(block.spans),
                    style = style.scaledBy(headingScale(block.level)).copy(fontWeight = FontWeight.Black),
                )
                is MdBlock.ListItem -> Row {
                    Spacer(Modifier.width((block.depth * 16).dp))
                    TextMMD(block.marker, modifier = Modifier.width(if (block.marker == "•") 16.dp else 26.dp), style = style)
                    TextMMD(annotate(block.spans), modifier = Modifier.weight(1f, fill = false), style = style)
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
                    TextMMD(annotate(block.spans), modifier = Modifier.padding(start = 8.dp), style = style.copy(fontStyle = FontStyle.Italic))
                }
                is MdBlock.Code -> CodeBox(block.text, style)
                is MdBlock.Table -> CodeBox(tableText(block.rows), style)
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
            }
        }
    }
}

/** 23/21/18 sp Black for heading levels 1/2/3+ against the 18 sp body. */
private fun headingScale(level: Int): Float = when (level) {
    1 -> 23f / 18f
    2 -> 21f / 18f
    else -> 1f
}

@Composable
private fun CodeBox(text: String, style: TextStyle) {
    Box(
        Modifier
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        TextMMD(
            text,
            style = style.scaledBy(0.85f).copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private val LinkStyles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))

/** Styled spans as one AnnotatedString, @handles in Black as in plain messages. */
private fun annotate(spans: List<MdSpan>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        val span = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Black else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
        )
        val body: AnnotatedString.Builder.() -> Unit = {
            withStyle(span) { if (s.code) append(s.text) else append(withBoldMentions(s.text)) }
        }
        if (s.url != null) withLink(LinkAnnotation.Url(s.url, LinkStyles)) { body() } else body()
    }
}
