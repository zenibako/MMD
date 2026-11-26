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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

// Layout pieces modelled on the Kompakt's built-in Messages app: a top bar with a back arrow,
// a small label over a large bold title and plain icon actions, closed by a thick black rule;
// a compose bar under the same rule with the send arrow in an outlined rounded square.

/** Rule under the top bar and over the compose bar, as thick as the Messages app's. */
private val BarRule = 3.dp

/** Top bar: optional back arrow, a small [label] above a bold [title], icon [actions]. */
@Composable
fun MessagesTopBar(
    label: String,
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            } else {
                Box(Modifier.size(8.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (label.isNotBlank()) {
                    TextMMD(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                TextMMD(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDividerMMD(thickness = BarRule)
    }
}

/** The bar's main action (Messages' "+"): white icon in a filled black rounded square. */
@Composable
fun PrimaryBarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(48.dp)
            .background(Color.Black, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/** Row separator in Messages' conversation list: a thin dashed line, inset like the row text. */
@Composable
fun DashedRowDivider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp).padding(start = 20.dp)) {
        drawLine(
            color = Color.Black,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** A plain icon action, 48dp touch target, as in the Messages top bar. */
@Composable
fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.Black, modifier = Modifier.size(30.dp))
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
            Modifier.size(40.dp).border(1.5.dp, ink, RoundedCornerShape(9.dp)),
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
 * One chat message, as the Messages app draws them: outlined, never filled. Received messages
 * sit on the left with a thin outline and a square bottom-left corner; yours sit on the right
 * with a thicker outline. The time goes underneath, on the same side.
 */
@Composable
fun MessageBubble(text: String, mine: Boolean, time: String?) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.86f).dp
    val shape = if (mine) {
        RoundedCornerShape(14.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .border(if (mine) 2.5.dp else 1.dp, Color.Black, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TextMMD(text, style = MaterialTheme.typography.bodyLarge)
        }
        if (!time.isNullOrBlank()) {
            TextMMD(time, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
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
