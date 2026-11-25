package com.mudita.mmd.samples.twofold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

// Bars shared with the Hermes sample (samples/hermes-kompakt, MessagesStyle.kt), which follow
// the Kompakt's built-in apps: a small label over a large bold title with plain icon actions,
// closed by a thick black rule; a bottom bar under the same rule; dashed, inset list dividers.

/** Rule under the top bar and over the bottom bar, as thick as the system apps'. */
private val BarRule = 3.dp

/** Top bar: optional back arrow, a small [label] above a bold [title], icon [actions]. */
@Composable
fun KompaktTopBar(
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
            if (onBack != null) BarIcon(BackIcon, "Back", onClick = onBack) else Box(Modifier.size(8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (label.isNotBlank()) TextMMD(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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

/** A plain icon action with a 48dp touch target. Disabled icons are drawn faint, not hidden. */
@Composable
fun BarIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Ink else Ink.copy(alpha = 0.3f),
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Thick rule, then the bar's content, pinned to the bottom of a screen. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
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

/** List row separator: a thin dashed line, inset like the row text. */
@Composable
fun DashedRowDivider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp).padding(start = 20.dp)) {
        drawLine(
            color = Ink,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}
