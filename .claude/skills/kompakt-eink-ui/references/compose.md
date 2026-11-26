# Kompakt E Ink screens in Compose + MMD

Read `SKILL.md` first: it holds the rules and the stock numbers. This reference is
how to meet them with MMD. Mudita's own Compose apps appear to use MMD but override some
of its defaults, so the reference file below does the same: it keeps MMD's theme, lists,
toggles and buttons, and adds the stock type scale, top bar, dashed divider, bottom sheet
and message bubbles.

## Setup

- Wrap the app in
  `ThemeMMD(typography = KompaktTypography) { Surface(color = MaterialTheme.colorScheme.background) { … } }`.
  `ThemeMMD` gives the black-and-white scheme and turns ripples off; `KompaktTypography`
  (below) swaps MMD's light type scale for the stock one. Never add another
  `MaterialTheme` or colour scheme.
- Depend on `project(":mmd-core")` (or `com.mudita:MMD`), `androidx.activity:activity-compose`
  and `androidx.compose.material3`. `compileSdk = 35` (mmd-core pulls androidx.activity
  1.10), `minSdk = 28`; the Kompakt runs API 31.
- Text: `TextMMD` with `MaterialTheme.typography.*`. Only the roles in `KompaktTypography`.
- Colour: `Ink` and `Paper` only. Disabled glyphs: `Ink.copy(alpha = 0.3f)`.

## Which component to use

| Need | Use | Notes |
|---|---|---|
| Text | `TextMMD` | |
| Primary / secondary button | `KompaktButton(filled = true/false)` below | wraps `ButtonMMD` / `OutlinedButtonMMD` at 48 dp, 12 dp corners |
| Text input in forms | `TextFieldMMD` | compose bars use the borderless `MessageField` |
| List | `LazyColumnMMD` | its default ▲/▼ + pill scrollbar is the stock pattern; keep it. For one long piece of reading or a chat: `scrollStep = 0, isScrollbarVisible = false` |
| Rule under the top bar | `HorizontalDividerMMD(thickness = 3.dp, color = Ink)` | inside `KompaktTopBar` |
| Row divider | `DashedRowDivider()` | MMD only has solid dividers |
| ⋮ menu, long-press actions, confirmations, toggles, short choices | `KompaktSheet` with `SheetButton`, `SheetToggle`, `SheetChoice` below | stock apps never use dropdown menus |
| Switching between a few peers from the title (profiles, accounts) | `KompaktTopBar(onTitleClick = …, titleMenu = { DropdownMenuMMD(…) })` | the one place a dropdown is accepted; see SKILL.md |
| Per-row actions | `Modifier.combinedClickable(onLongClick = …)` opening an item sheet | needs `@OptIn(ExperimentalFoundationApi::class)` on older Compose |
| Switch | `SwitchMMD` | 52 x 32 dp vs stock 48 x 30; acceptable, or wrap in `Modifier.scale(0.93f)` |
| Radio / checkbox | `RadioButtonMMD`, `CheckboxMMD` | stock radio is larger (26 dp); fine to keep MMD's |
| Top bar | `KompaktTopBar` | not `TopAppBarMMD`: stock title is 25 sp Black at 16 dp |
| Create action | `CreateBarButton` in the top bar | not `FloatingActionButtonMMD` |
| Tabs | `TabsMMD` / `TabMMD` | only where a stock app uses tabs (Phone) |
| Snackbar, tooltip, `ModalBottomSheet`, dropdowns other than the title switcher | avoid | they animate or aren't stock patterns |

Avoid `AnimatedVisibility`, `animate*AsState`, `Crossfade`, `AnimatedContent`,
`animateScrollToItem`, infinite progress indicators, `Modifier.shadow`, non-zero
`tonalElevation`/`shadowElevation`, `indication = ripple(...)`, and streaming text.

## Navigation

- A `sealed interface Screen` plus `var screen by remember { mutableStateOf(...) }`; swap
  whole screens, no navigation animation.
- Sub-screens: `KompaktTopBar(onBack = …)` plus `BackHandler { … }`.
- A setting with several choices opens a sub-screen of radio rows, not a dialog.
- A sheet is part of the screen's state: `var sheet by remember { mutableStateOf(false) }`
  (or the item it is for, `mutableStateOf<Item?>(null)`). `KompaktSheet` handles Back.
- Sheets chain, never stack: an action that leads to another sheet (Menu > Filter, Item >
  Delete confirm, Chat > Model) closes the first and opens the second.

## Reference: `KompaktUi.kt`

Copy into the app's UI package. Keep the numbers; they are the stock values.

```kotlin
package your.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.R
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD

val Ink = Color.Black
val Paper = Color.White

object Kompakt {
    val BarHeight = 64.dp
    val BarRule = 3.dp
    val TextInset = 16.dp
    val IconTextInset = 60.dp
    val Touch = 48.dp
    val BarIcon = 28.dp
    val ButtonHeight = 48.dp
    val ButtonCorner = 12.dp
}

// Stock type scale: Lato Medium (500) for content, Lato Black (900) for titles and labels.
// MMD ships no Lato Medium file, so 500 falls back to Regular; add lato_medium.ttf to your
// app's res/font and list it here for an exact match.
private val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_regular, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Lato, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = Ink)

val KompaktTypography = Typography(
    headlineLarge = style(36, 40, FontWeight.Black),   // full-screen alert title
    titleLarge = style(25, 28, FontWeight.Black),      // top bar, sheet title, empty-state headline
    titleMedium = style(23, 25, FontWeight.Black),     // two-line row title
    titleSmall = style(21, 25, FontWeight.Black),      // section header, list name
    bodyLarge = style(21, 25, FontWeight.Medium),      // row label, summary, body
    bodyMedium = style(18, 22, FontWeight.Medium),     // message text, times, previews
    bodySmall = style(15, 18, FontWeight.Medium),      // densest captions only
    labelLarge = style(21, 23, FontWeight.Black),      // button label
    labelMedium = style(18, 22, FontWeight.Black),     // small labels, URGENT
    labelSmall = style(15, 15, FontWeight.Medium),
)

/**
 * Top bar: optional back arrow, a 25 sp Black title, plain icon [actions], then the 3 dp rule.
 * With [onTitleClick] the title is a switcher: the whole title is a 48 dp target with a ▾ after
 * it, and [titleMenu] (a `DropdownMenuMMD`) is drawn in the title's box so it opens under it.
 * [label] is an optional 18 sp line above the title; stock bars have none, so use it only for
 * context the user needs on every visit (Hermes: the profile and a per-chat model).
 */
@Composable
fun KompaktTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    label: String? = null,
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
    titleMenu: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Paper)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Kompakt.BarHeight), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.padding(start = 6.dp).width(54.dp)) { BarIcon(BackIcon, "Back", onClick = onBack) }
            } else {
                Spacer(Modifier.width(Kompakt.TextInset))
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = Kompakt.Touch)
                        .then(if (onTitleClick != null) Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick) else Modifier),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!label.isNullOrBlank()) TextMMD(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextMMD(
                            title,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onTitleClick != null) Icon(DropDownIcon, null, tint = Ink, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
                }
                titleMenu()
            }
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

/** The screen's one create action, as in Messages: a black 42 x 36 dp rectangle, 8 dp corners, white "+". */
@Composable
fun CreateBarButton(description: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 4.dp, end = 10.dp).size(width = 42.dp, height = Kompakt.Touch)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(42.dp, 36.dp).background(Ink, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(PlusIcon, description, tint = Paper, modifier = Modifier.size(22.dp))
        }
    }
}

/** 1 dp dashed line (2 on, 2 off) from [start] to the right edge. Use [Kompakt.IconTextInset] after an icon. */
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

/**
 * A list row. Single line: [title] in 21 sp Medium, 64 dp. With [subtitle]: 23 sp Black title over a
 * 21 sp Medium summary. [trailing] holds a value, a ">" chevron or a switch.
 */
@Composable
fun KompaktRow(
    title: String,
    subtitle: String? = null,
    minHeight: Dp = 64.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = Kompakt.TextInset, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.width(Kompakt.IconTextInset - Kompakt.TextInset), contentAlignment = Alignment.CenterStart) { leading() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val typography = MaterialTheme.typography
                TextMMD(
                    title,
                    style = if (subtitle == null) typography.bodyLarge else typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) TextMMD(subtitle, style = typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        DashedRowDivider(start = if (leading != null) Kompakt.IconTextInset else Kompakt.TextInset)
    }
}

/** Message-list row (72 dp): bold name with the time at the right, one line of preview. */
@Composable
fun ConversationRow(name: String, time: String, preview: String, onClick: () -> Unit) {
    Column {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick)
                .padding(horizontal = Kompakt.TextInset, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(name, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextMMD(time, Modifier.padding(start = 12.dp).alignByBaseline(), style = MaterialTheme.typography.bodyMedium)
            }
            TextMMD(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DashedRowDivider()
    }
}

/** Settings section header: 21 sp Black at 16 dp, dashed line below. */
@Composable
fun SectionHeader(text: String) {
    TextMMD(text, Modifier.padding(start = Kompakt.TextInset, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
    DashedRowDivider()
}

/** Stock button: 48 dp tall, 12 dp corners; filled black or 2 dp outline. */
@Composable
fun KompaktButton(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Kompakt.ButtonCorner)
    val m = modifier.heightIn(min = Kompakt.ButtonHeight)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = m, enabled = enabled, shape = shape) {
            TextMMD(text, style = MaterialTheme.typography.labelLarge, color = Paper)
        }
    } else {
        OutlinedButtonMMD(onClick = onClick, modifier = m, enabled = enabled, shape = shape, border = BorderStroke(2.dp, Ink)) {
            TextMMD(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Stock bottom sheet for ⋮ menus, item actions, confirmations, toggles and short choices:
 * full width, white, 3 dp black top border, no radius, shadow, scrim or animation. A centred
 * 25 sp Black title, the [content] (usually [SheetButton]s), then the closing button. Place it
 * last inside a full-screen Box so it draws over the screen. Back and a tap above it close it.
 *
 * - Menu or item sheet: outlined [SheetButton]s, filled "Cancel".
 * - Confirm sheet: the question as [title], the item named in a body line, the action as the
 *   one filled button and `closeFilled = false`, so only one button is black.
 * - Toggle sheet: [SheetToggle]s, `closeLabel = "Done"`; changes apply at once.
 */
@Composable
fun KompaktSheet(
    title: String,
    onDismiss: () -> Unit,
    closeLabel: String = "Cancel",
    closeFilled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            TextMMD(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content()
            SheetButton(closeLabel, filled = closeFilled, onClick = onDismiss)
        }
    }
}

/** A sheet action: full width, 48 dp, 12 dp corners, 2 dp outline or filled black, centred 21 sp Black label. */
@Composable
fun SheetButton(label: String, filled: Boolean = false, onClick: () -> Unit) =
    KompaktButton(label, filled = filled, modifier = Modifier.fillMaxWidth(), onClick = onClick)

/** A sheet row with a 21 sp label and a switch at the right; the whole 48 dp row toggles it. */
@Composable
fun SheetToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Kompakt.Touch)
            .clickable(onClickLabel = label) { onChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SwitchMMD(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * One option in a choice sheet: a full-width outlined 48 dp button with the label at the start.
 * The current option is 3 dp, Black and checked. Picking applies and closes the sheet.
 */
@Composable
fun SheetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Kompakt.ButtonCorner)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Kompakt.Touch)
            .border(if (selected) 3.dp else 2.dp, Ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            label,
            Modifier.weight(1f),
            style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) Icon(CheckIcon, "Current", tint = Ink, modifier = Modifier.padding(start = 4.dp).size(24.dp))
    }
}

/** Choices in a sheet, capped so the sheet keeps the screen's top visible. More than about six: use a radio sub-screen. */
@Composable
fun SheetChoices(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A one-line status under the content (loading, an error) with an optional Retry icon at the right. */
@Composable
fun StatusLine(text: String, onRetry: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Kompakt.TextInset, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextMMD(text, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onRetry != null) BarIcon(RetryIcon, "Retry", onClick = onRetry)
    }
}

/** Agent activity between messages, e.g. "Used web_search, read_file": one small line, no bubble. */
@Composable
fun ActivityLine(text: String) {
    TextMMD(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Empty state: illustration, 25 sp Black headline, 21 sp Medium body, one outlined button. */
@Composable
fun EmptyState(headline: String, body: String, action: Pair<String, () -> Unit>?, illustration: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        illustration?.invoke()
        TextMMD(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        TextMMD(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.let { (label, onClick) -> KompaktButton(label, filled = false, onClick = onClick) }
    }
}

/**
 * One message as in Messages: white, 12 dp corners, one square tail corner.
 * Received: left, 1 dp, square bottom-start. Sent: right, 2 dp, square bottom-end. Selected: 3.5 dp.
 */
@Composable
fun MessageBubble(text: String, mine: Boolean, time: String?, selected: Boolean = false, sameAuthorAsNext: Boolean = false) {
    val r = 12.dp
    val shape = if (mine) RoundedCornerShape(r, r, 0.dp, r) else RoundedCornerShape(r, r, r, 0.dp)
    val stroke = when { selected -> 3.5.dp; mine -> 2.dp; else -> 1.dp }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (sameAuthorAsNext) 2.dp else 18.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.8f).dp)
                .border(stroke, Ink, shape).padding(horizontal = 10.dp, vertical = 8.dp),
        ) { TextMMD(text, style = MaterialTheme.typography.bodyMedium) }
        if (!time.isNullOrBlank()) TextMMD(time, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** Compose-bar field: no outline or underline, just text, placeholder and a black cursor. */
@Composable
fun MessageField(value: String, onValueChange: (String) -> Unit, placeholder: String, onSend: () -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        maxLines = 4,
        cursorBrush = SolidColor(Ink),
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

/** Bottom bar: 3 dp rule, then a padded row (compose field + send, or a control pad). */
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

/** Outlined 40 dp square, 2 dp stroke, 10 dp corners, 24 dp icon, 48 dp target (send button, arrow pads). */
@Composable
fun OutlinedIconButton(icon: ImageVector, description: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(Kompakt.Touch).clickable(enabled = enabled, onClickLabel = description, onClick = onClick), contentAlignment = Alignment.Center) {
        val ink = if (enabled) Ink else Ink.copy(alpha = 0.3f)
        Box(Modifier.size(40.dp).border(2.dp, ink, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = ink, modifier = Modifier.size(24.dp))
        }
    }
}

// Line icons drawn like the stock ones: 2 dp strokes, round caps.
private fun lineIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Ink), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }.build()

val PlusIcon = lineIcon("Plus") { moveTo(3f, 12f); lineTo(21f, 12f); moveTo(12f, 3f); lineTo(12f, 21f) }
val BackIcon = lineIcon("Back") { moveTo(20f, 12f); lineTo(4f, 12f); moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f) }
val ChevronRightIcon = lineIcon("Chevron") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
val DropDownIcon = lineIcon("DropDown") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
val CheckIcon = lineIcon("Check") { moveTo(4f, 12f); lineTo(10f, 18f); lineTo(20f, 6f) }
val RetryIcon = lineIcon("Retry") {
    moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.95f, 7.05f)
    moveTo(19f, 3f); lineTo(19f, 8f); lineTo(14f, 8f)
}
val PinIcon = lineIcon("Pin") {
    moveTo(9f, 3f); lineTo(15f, 3f); moveTo(10f, 3f); lineTo(10f, 9f); lineTo(7f, 13f)
    lineTo(17f, 13f); lineTo(14f, 9f); lineTo(14f, 3f); moveTo(12f, 13f); lineTo(12f, 21f)
}
val EditIcon = lineIcon("Edit") {
    moveTo(4f, 20f); lineTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(8f, 20f); close()
    moveTo(13f, 7f); lineTo(17f, 11f)
}
val LockIcon = lineIcon("Lock") {
    moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
    moveTo(8f, 11f); lineTo(8f, 8f); arcTo(4f, 4f, 0f, false, true, 16f, 8f); lineTo(16f, 11f)
}
val MoreIcon: ImageVector = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes("M12,8a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,14a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,20a2,2 0 1,0 0,-4a2,2 0 1,0 0,4z"),
    fill = SolidColor(Ink),
).build()
val SendOutlineIcon: ImageVector = ImageVector.Builder("SendOutline", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Ink), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round) {
        moveTo(3f, 4f); lineTo(21f, 12f); lineTo(3f, 20f); lineTo(6f, 12f); close()
        moveTo(6f, 12f); lineTo(13f, 12f)
    }
}.build()
```

For other icons, draw them the same way or use Material Symbols *outlined* path data. For
directions use MMD's `R.drawable.chevron_filled_{left,up,down,right}`.

## Screen recipes

**List screen** (conversations, sessions, topics):

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        KompaktTopBar("Messages") {
            BarIcon(MoreIcon, "More options") { menu = true }
            CreateBarButton("New message") { onNew() }
        }
        if (status.isNotBlank()) TextMMD(status, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        if (rows.isEmpty()) EmptyState("No messages", "Start a conversation with +.", null)
        else LazyColumnMMD(Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { r -> ConversationRow(r.name, r.time, r.preview) { onOpen(r) } }
        }
    }
    if (menu) {
        KompaktSheet("Menu", onDismiss = { menu = false }) {
            SheetButton("Refresh") { menu = false; refresh() }
            SheetButton(if (hidden.isEmpty()) "Filter" else "Filter (some hidden)") { menu = false; filter = true }
            SheetButton("Settings") { menu = false; onSettings() }
        }
    }
    if (filter) {
        KompaktSheet("Show", onDismiss = { filter = false }, closeLabel = "Done") {
            Group.entries.forEach { g -> SheetToggle(g.label, checked = g !in hidden) { show -> hidden = if (show) hidden - g else hidden + g } }
        }
    }
    itemSheet?.let { r ->
        KompaktSheet(r.name, onDismiss = { itemSheet = null }) {
            SheetButton(if (r.pinned) "Unpin" else "Pin to top") { itemSheet = null; setPinned(r, !r.pinned) }
            SheetButton("Delete") { itemSheet = null; confirmDelete = r }
        }
    }
    confirmDelete?.let { r ->
        KompaktSheet("Delete this conversation?", onDismiss = { confirmDelete = null }, closeFilled = false) {
            TextMMD(r.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 2)
            SheetButton("Delete", filled = true) { confirmDelete = null; delete(r) }
        }
    }
}
```

Rows open on tap and offer their actions on long-press:
`Modifier.combinedClickable(onClickLabel = "Open", onLongClickLabel = "Actions", onLongClick = { itemSheet = r }, onClick = { onOpen(r) })`.
Mark a pinned row with a 20 dp `PinIcon` before its name and sort pinned rows first; mark a
read-only row with an 18 dp `LockIcon` before its preview. Filters persist in settings and
the ⋮ entry says when some rows are hidden.

**Title switcher** (a few peer accounts or profiles, Hermes): `KompaktTopBar(profileName,
onTitleClick = if (profiles.size > 1) ({ open = true }) else null, titleClickLabel = "Switch profile",
titleMenu = { DropdownMenuMMD(expanded = open, onDismissRequest = { open = false }) { … } })`.
Items are `DropdownMenuItemMMD` with 21 sp labels that may wrap to two lines, the current one
Black with a `CheckIcon`. Only with two or more peers; otherwise the title is plain.

**Conversation screen**: `KompaktTopBar(contactName, onBack = …)`, a
`LazyColumnMMD(Modifier.weight(1f), scrollStep = 0, isScrollbarVisible = false)` of day
markers and `MessageBubble`s, a `StatusLine` while working or after a failure (with `onRetry` when history failed to load),
then `KompaktBottomBar { MessageField(…, Modifier.weight(1f)); OutlinedIconButton(SendOutlineIcon, "Send") { send() } }`.

- Open on the end of the newest message, and stay there only while the reader is there, so
  a reply doesn't undo scrolling up to read:

  ```kotlin
  LaunchedEffect(listState) {
      snapshotFlow { listState.isScrollInProgress }.collect { if (!it) followBottom = !listState.canScrollForward }
  }
  LaunchedEffect(items.size) {
      // The offset is clamped, so this shows the end of a long last item, not just its top.
      if (items.isNotEmpty() && followBottom) listState.scrollToItem(items.size - 1, scrollOffset = Int.MAX_VALUE)
  }
  ```
  Sending sets `followBottom = true`.
- Agent or system activity: an `ActivityLine("Used web_search, read_file")` between bubbles.
  Full tool calls and system entries stay hidden behind a ⋮ > `SheetToggle("Show tool and
  system messages")`; when shown, each is an 18 sp Black label over up to four 18 sp lines,
  with a dashed divider.
- A conversation the user can't write to: replace the compose bar's content, don't disable
  it. `KompaktBottomBar { Icon(LockIcon, "Read-only"); /* source icon */; Spacer(Modifier.weight(1f)); OutlinedIconButton(EditIcon, "Continue here") { fork() } }`.
- A per-conversation choice (Hermes: the model) lives in the chat's ⋮ sheet as
  `SheetButton("Model: $current")`, which opens a choice sheet of `SheetChoice`s inside
  `SheetChoices`, the first one "Default". While the options load or fail, the sheet shows
  one line of text in their place. When a choice overrides a default, say so in the bar's
  `label`.

**Settings screen**: `KompaktTopBar("Settings", onBack = …)`, then a scrolling column of
`SectionHeader` and `KompaktRow`s: `trailing = { SwitchMMD(checked, onCheckedChange = null) }`
with `onClick` toggling; `trailing = { Icon(ChevronRightIcon, null) }` for sub-pages; a radio
sub-screen of `KompaktRow(minHeight = 62.dp, leading = { RadioButtonMMD(selected, null) })`.
Form fields use `TextFieldMMD`, and actions are `KompaktButton("Test", filled = false)` next to
`KompaktButton("Save", filled = true)`.

**Short fixed choice list that must fit** (Twofold tile style): rows sharing the height
with `Modifier.weight(1f)`, radio leading, dashed dividers between.

## Review

Run the checklist in `SKILL.md`, then grep the diff for `animate`, `Animated`,
`Crossfade`, `shadow`, `Elevation = [1-9]`, `ripple`, `DropdownMenu` (outside a title switcher), `AlertDialog`,
`ModalBottomSheet`, `FloatingActionButton`, `Icons.Filled`/`Icons.Outlined` (prefer the line icons), `Color(0x` other than black or white, and
`fontWeight = FontWeight.Bold` (the stock weight is Black). Look at the result on a real
Kompakt; emulators hide ghosting.
