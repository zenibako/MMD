package com.mudita.mmd.samples.hermes

import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

// Layout pieces modelled on the Kompakt's built-in Messages app: a top bar with a back arrow,
// a small label over a large bold title and plain icon actions, closed by a thick black rule;
// a compose bar under the same rule with the send arrow in an outlined rounded square.

/** Rule under the top bar and over the compose bar, as thick as the Messages app's. */
private val BarRule = 3.dp

/**
 * Top bar: optional back arrow, a small [label] above a bold [title], icon [actions]. With
 * [onTitleClick] the title is itself a button, marked by a small arrow after it.
 */
@Composable
fun MessagesTopBar(
    label: String,
    title: String,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
    // Makes the label line (and the title under it, for a 48 dp target) open a setting, such
    // as a chat's model; the label then ends with a small ▾ and the title has none.
    onLabelClick: (() -> Unit)? = null,
    labelClickLabel: String? = null,
    // Drawn in the title's box, so a dropdown opened from the title is anchored under it.
    titleMenu: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Title at 16 dp, or at 60 dp after the back arrow, as in the stock bars.
            if (onBack != null) {
                Box(Modifier.padding(start = 6.dp).width(54.dp)) {
                    BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                onTitleClick != null -> Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick)
                                onLabelClick != null -> Modifier.clickable(onClickLabel = labelClickLabel, onClick = onLabelClick)
                                else -> Modifier
                            },
                        )
                        .heightIn(min = 48.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (label.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextMMD(
                                label,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (onLabelClick != null && onTitleClick == null) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextMMD(
                            title,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onTitleClick != null) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(start = 4.dp).size(24.dp))
                        }
                    }
                }
                titleMenu()
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDividerMMD(thickness = BarRule)
    }
}

/**
 * The screen's one create action, as in Messages: a black 42 x 36 dp rectangle with 8 dp
 * corners and a white line "+", in a 48 dp tall target.
 */
@Composable
fun PrimaryBarIcon(description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp, end = 4.dp)
            .size(width = 42.dp, height = 48.dp)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(42.dp, 36.dp).background(Color.Black, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(PlusLine, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** The stock "+": two 2 dp strokes with round caps. */
private val PlusLine: ImageVector by lazy {
    ImageVector.Builder("PlusLine", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(12f, 3f)
            lineTo(12f, 21f)
        }
    }.build()
}

/** Row separator: 1 dp dashed (2 on, 2 off) from the 16 dp text inset to the right edge. */
@Composable
fun DashedRowDivider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp).padding(start = 16.dp)) {
        drawLine(
            color = Color.Black,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
        )
    }
}

/** A plain icon action: a 28 dp glyph in a 48 dp target, as in the stock top bars. */
@Composable
fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.Black, modifier = Modifier.size(28.dp))
    }
}

/** The compose bar's send control: an arrow inside an outlined rounded square. */
@Composable
fun SendButton(onClick: () -> Unit, enabled: Boolean = true) =
    OutlinedSquareButton(SendOutline, "Send", enabled, onClick)

/**
 * An icon inside an outlined rounded square, like the Messages send button. Sized from the
 * Messages compose bar: ~40dp square, thin border, ~24dp icon, inside a 48dp touch target.
 * Disabled shows the faint (30%) glyph the stock apps use, never grey text.
 */
@Composable
fun OutlinedSquareButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (enabled) Color.Black else Color.Black.copy(alpha = 0.3f)
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).border(2.dp, ink, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = ink, modifier = Modifier.size(24.dp))
        }
    }
}

/** What started a session, as an icon: a schedule (cron), a board (kanban), or a chat app. */
fun sourceIcon(source: String): ImageVector = when (source.lowercase()) {
    "cron", "schedule", "scheduler" -> Icons.Outlined.DateRange
    "kanban" -> Icons.AutoMirrored.Outlined.List
    "" -> Icons.Outlined.Lock
    else -> Icons.Outlined.Email // messaging platforms: telegram, discord, slack, sms…
}

/** Read-only marker: a lock and the source's icon, small and inline like a status glyph. */
@Composable
fun ReadOnlyIcons(source: String, size: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = "Read-only", tint = Color.Black, modifier = Modifier.size(size))
        Icon(
            sourceIcon(source),
            contentDescription = "Started from ${source.ifBlank { "another surface" }}",
            tint = Color.Black,
            modifier = Modifier.size(size),
        )
    }
}

/** Thick rule, then the bar's content, pinned to the bottom of a screen. */
@Composable
fun ComposeBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDividerMMD(thickness = BarRule)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * One chat message, as the Messages app draws them: outlined, never filled, 12 dp corners with
 * one square "tail" corner. Received: left, 1 dp, square bottom-left. Yours: right, 2 dp, square
 * bottom-right. 18 sp text, 10/8 dp padding, the time 4 dp under it on the same side. Messages
 * from one author sit 2 dp apart ([sameAuthorAsNext]); a change of author gets 18 dp.
 */
@Composable
fun MessageBubble(
    text: String,
    mine: Boolean,
    time: String?,
    sameAuthorAsNext: Boolean = false,
    // Another agent's name, drawn above its message in 18 sp Black.
    sender: String? = null,
    // Draw [text] as Markdown (agent replies); yours stay plain.
    markdown: Boolean = false,
) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val r = 12.dp
    val shape = if (mine) {
        RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0.dp, bottomStart = r)
    } else {
        RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r, bottomStart = 0.dp)
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (sameAuthorAsNext) 2.dp else 18.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!sender.isNullOrBlank()) {
            TextMMD(
                sender,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .border(if (mine) 2.dp else 1.dp, Color.Black, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            val body = MaterialTheme.typography.bodyMedium.scaledForMessages()
            if (markdown) MarkdownText(text, body) else TextMMD(withBoldMentions(text), style = body)
        }
        if (!time.isNullOrBlank()) {
            TextMMD(time, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** [text] with each @handle in Black (weight, not colour). */
fun withBoldMentions(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in mentionRanges(text)) {
        addStyle(SpanStyle(fontWeight = FontWeight.Black), range.first, range.last + 1)
    }
}

/** Centered day marker between messages from different days, e.g. "• 03 Aug •". */
@Composable
fun DaySeparator(day: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        TextMMD("•  $day  •", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The compose field: no outline or underline, just text and a cursor, as in Messages. */
@Composable
fun MessageField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
        textStyle = style,
        maxLines = 4,
        cursorBrush = SolidColor(Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) TextMMD(placeholder, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

/**
 * [MessageField] on a [TextFieldValue], for callers that need the cursor (the @mention
 * suggestions); [bold] ranges (known @handles) are drawn in Black.
 */
@Composable
fun MessageField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    bold: (String) -> List<IntRange> = { emptyList() },
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = Color.Black)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
        textStyle = style,
        maxLines = 4,
        cursorBrush = SolidColor(Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        visualTransformation = { text ->
            val ranges = bold(text.text)
            val styled = buildAnnotatedString {
                append(text)
                for (r in ranges) addStyle(SpanStyle(fontWeight = FontWeight.Black), r.first, r.last + 1)
            }
            TransformedText(styled, OffsetMapping.Identity)
        },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) TextMMD(placeholder, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

/**
 * The Messages app's send icon: a hollow paper plane, drawn as a thin stroke (an arrowhead with
 * a notch at the back and a short line to its centre), unlike Material's solid Send icon.
 */
private val SendOutline: ImageVector by lazy {
    ImageVector.Builder("SendOutline", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(3f, 4f)
            lineTo(21f, 12f)
            lineTo(3f, 20f)
            lineTo(6f, 12f)
            close()
            moveTo(6f, 12f)
            lineTo(13f, 12f)
        }
    }.build()
}

/** Pinned-session marker: a thin-line push pin, drawn like [SendOutline]. */
val PinOutline: ImageVector by lazy {
    ImageVector.Builder("PinOutline", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            // Head, body flaring to a flat base, then the needle.
            moveTo(9f, 3f)
            lineTo(15f, 3f)
            moveTo(10f, 3f)
            lineTo(10f, 9f)
            lineTo(7f, 13f)
            lineTo(17f, 13f)
            lineTo(14f, 9f)
            lineTo(14f, 3f)
            moveTo(12f, 13f)
            lineTo(12f, 21f)
        }
    }.build()
}
