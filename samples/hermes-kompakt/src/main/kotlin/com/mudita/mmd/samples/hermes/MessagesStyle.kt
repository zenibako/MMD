package com.mudita.mmd.samples.hermes

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
fun SendButton(onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(52.dp)
            .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = "Send", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(28.dp))
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
 * One chat message. Your messages sit on the right, filled black with white text; the agent's
 * sit on the left, outlined. Static: no animation, one refresh per message.
 */
@Composable
fun MessageBubble(text: String, mine: Boolean) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.82f).dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .then(
                    if (mine) {
                        Modifier.background(Color.Black, shape)
                    } else {
                        Modifier.border(1.5.dp, Color.Black, shape)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            TextMMD(text, color = if (mine) Color.White else Color.Black, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
