package com.mudita.mmd.samples.twofold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.R as MmdR
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

// Kompakt UI pieces, following the kompakt-eink-ui skill (.claude/skills/kompakt-eink-ui,
// references/compose.md): stock type scale, 64 dp top bar with a 3 dp rule, 2/2 dashed
// dividers, 48 dp buttons with 12 dp corners, and bottom sheets instead of dropdown menus.

object Kompakt {
    val BarHeight = 64.dp
    val BarRule = 3.dp
    val TextInset = 16.dp
    val Touch = 48.dp
    val BarIcon = 28.dp
    val ButtonHeight = 48.dp
    val ButtonCorner = 12.dp
}

// Lato Medium (500) for content and Black (900) for titles and labels, as the stock apps use.
// MMD ships no Lato Medium, and Regular reads lighter than stock on the panel, so 500 is SemiBold.
private val Lato = FontFamily(
    Font(MmdR.font.lato_regular, FontWeight.Normal),
    Font(MmdR.font.lato_semi_bold, FontWeight.Medium),
    Font(MmdR.font.lato_bold, FontWeight.Bold),
    Font(MmdR.font.lato_black, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Lato, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = Ink)

val KompaktTypography = Typography(
    headlineLarge = style(36, 40, FontWeight.Black),
    titleLarge = style(25, 28, FontWeight.Black), // top bar, sheet title
    titleMedium = style(23, 25, FontWeight.Black),
    titleSmall = style(21, 25, FontWeight.Black),
    bodyLarge = style(21, 25, FontWeight.Medium), // row label, body
    bodyMedium = style(18, 22, FontWeight.Medium), // captions
    bodySmall = style(15, 18, FontWeight.Medium),
    labelLarge = style(21, 23, FontWeight.Black), // button label
    labelMedium = style(18, 22, FontWeight.Black),
    labelSmall = style(15, 15, FontWeight.Medium),
)

/** Top bar: optional back arrow, a 25 sp Black title at 16 dp (60 dp after back), icon [actions], 3 dp rule. */
@Composable
fun KompaktTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Paper)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Kompakt.BarHeight), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.padding(start = 6.dp).width(54.dp)) { BarIcon(BackIcon, "Back", onClick = onBack) }
            } else {
                Spacer(Modifier.width(Kompakt.TextInset))
            }
            TextMMD(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDividerMMD(thickness = Kompakt.BarRule, color = Ink)
    }
}

/** Plain black icon, 28 dp in a 48 dp target. Disabled icons are faint, not hidden. */
@Composable
fun BarIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(Kompakt.Touch).clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) Ink else Ink.copy(alpha = 0.3f), modifier = Modifier.size(Kompakt.BarIcon))
    }
}

/** Bottom bar: 3 dp rule, then a padded row (here, the arrow pad). */
@Composable
fun KompaktBottomBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paper)) {
        HorizontalDividerMMD(thickness = Kompakt.BarRule, color = Ink)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 1 dp dashed line (2 on, 2 off) from [start] to the right edge. */
@Composable
fun DashedRowDivider(start: Dp = Kompakt.TextInset) {
    Canvas(Modifier.fillMaxWidth().height(1.dp).padding(start = start)) {
        drawLine(
            color = Ink,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
        )
    }
}

/** Stock button: 48 dp tall, 12 dp corners; filled black or a 2 dp outline, 21 sp Black label. */
@Composable
fun KompaktButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Kompakt.ButtonCorner)
    val m = modifier.heightIn(min = Kompakt.ButtonHeight)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = m, shape = shape) {
            TextMMD(text, style = MaterialTheme.typography.labelLarge, color = Paper)
        }
    } else {
        OutlinedButtonMMD(onClick = onClick, modifier = m, shape = shape, border = BorderStroke(2.dp, Ink)) {
            TextMMD(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Stock bottom sheet for the ⋮ menu: full width, white, 3 dp black top border, no radius, shadow,
 * scrim or animation; a centred 25 sp Black title, the actions, then a filled Cancel. Place it
 * last in a full-screen Box. Back and a tap above it close it.
 */
@Composable
fun KompaktSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().background(Paper)
                .drawBehind { drawRect(Ink, size = size.copy(height = 3.dp.toPx())) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 12.dp, end = 12.dp, top = 3.dp + 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextMMD(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            content()
            SheetButton("Cancel", filled = true, onClick = onDismiss)
        }
    }
}

/** A sheet action: full width, 48 dp, outlined (or filled for the closing button). */
@Composable
fun SheetButton(label: String, filled: Boolean = false, onClick: () -> Unit) =
    KompaktButton(label, filled = filled, modifier = Modifier.fillMaxWidth(), onClick = onClick)
