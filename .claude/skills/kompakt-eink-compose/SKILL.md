---
name: kompakt-eink-compose
description: Build Kompakt-style E Ink screens in Jetpack Compose with MMD (ThemeMMD, *MMD components). Use when writing or reviewing Compose UI for the Mudita Kompakt or any app in this repo.
---

# Kompakt E Ink screens in Compose + MMD

Read `kompakt-eink-design` first: it holds the rules and the numbers. This skill is how to
meet them with MMD. The reference code below is the merged, cleaned-up version of what the
Hermes sample (`samples/hermes-kompakt`, `MessagesStyle.kt`) and Twofold
(`samples/twofold`, `KompaktBars.kt`) each built by hand.

## Setup

- Wrap the whole app in `ThemeMMD { Surface(color = MaterialTheme.colorScheme.background) { … } }`.
  It sets the black-and-white scheme, Lato and turns ripples off. Never pass another
  `colorScheme` and never wrap content in a second `MaterialTheme`.
- Depend on `project(":mmd-core")` (or `com.mudita:MMD`), `androidx.activity:activity-compose`
  and `androidx.compose.material3`. `compileSdk = 35` (mmd-core pulls androidx.activity
  1.10), `minSdk = 28`, the Kompakt runs API 31.
- Text: `TextMMD`, with `MaterialTheme.typography.*` styles. Use `fontWeight = FontWeight.Bold`
  for titles; do not introduce new sizes.
- Colour: only `Color.Black`, `Color.White` or the scheme's roles (which are black or white).
  Name them once (`val Ink = Color.Black; val Paper = Color.White`). Disabled =
  `Ink.copy(alpha = 0.3f)`.

## Which MMD component to use

| Need | Use | Notes |
|---|---|---|
| Text | `TextMMD` | |
| Primary button | `ButtonMMD` | one per screen |
| Secondary button | `OutlinedButtonMMD` | |
| Text input in forms | `TextFieldMMD` | compose bars use the borderless field below |
| List | `LazyColumnMMD` | default paged scrolling + scrollbar for rows; `scrollStep = 0, isScrollbarVisible = false` for chats and long reading |
| Rule | `HorizontalDividerMMD(thickness = 3.dp)` | the bar rule |
| Row divider | `DashedRowDivider()` below | MMD has no dashed divider |
| Overflow menu | `DropdownMenuMMD` + `DropdownMenuItemMMD` | already flat and outlined |
| Toggles | `SwitchMMD`, `CheckboxMMD`, `RadioButtonMMD` | |
| Tabs | `TabsMMD` / `TabMMD` | only when the stock app uses tabs for the same thing |
| Top bar | `KompaktTopBar` below | not `TopAppBarMMD`: the stock bar has a label over a bold title and a 3 dp rule |
| Main create action | `PrimaryBarIcon` in the top bar | not `FloatingActionButtonMMD` |
| Sheets, snackbars, tooltips | avoid | use a sub-screen, a one-line status, or the label |

Avoid: `AnimatedVisibility`, `animate*AsState`, `Crossfade`, `AnimatedContent`, infinite
`CircularProgressIndicator`, `Modifier.shadow`, any non-zero `tonalElevation` or
`shadowElevation`, `indication = ripple(...)`, colour other than black and white, and
streaming text into the UI token by token.

## Navigation

- Keep a `sealed interface Screen` and a `var screen by remember { mutableStateOf(...) }`,
  and swap whole screens. No navigation animations.
- Each sub-screen has a back arrow in `KompaktTopBar(onBack = …)` and a
  `BackHandler { … }` for the system back gesture.
- A secondary option set (Twofold's tile style) is a sub-screen, not a sheet or dialog.

```kotlin
if (pickingStyle) {
    BackHandler { pickingStyle = false }
    StyleScreen(selected, onSelect, onBack = { pickingStyle = false })
    return
}
```

## Reference: `KompaktUi.kt`

Copy this file into the app's UI package and build screens from it. Keep the numbers; they
are the design contract.

```kotlin
package your.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

val Ink = Color.Black
val Paper = Color.White

object Kompakt {
    val BarRule = 3.dp
    val RowInsetStart = 20.dp
    val RowInsetEnd = 8.dp
    val Touch = 48.dp
    val BarIconSize = 30.dp
    val ButtonIconSize = 24.dp
}

/** Top bar: optional back arrow, a small [label] above a bold [title], plain icon [actions]. */
@Composable
fun KompaktTopBar(
    title: String,
    label: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BarIcon(BackIcon, "Back", onClick = onBack) else Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (label.isNotBlank()) {
                    TextMMD(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        HorizontalDividerMMD(thickness = Kompakt.BarRule, color = Ink)
    }
}

/** Plain icon action: 30 dp icon, 48 dp target. Disabled icons are faint, not hidden. */
@Composable
fun BarIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(Kompakt.Touch).clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) Ink else Ink.copy(alpha = 0.3f), modifier = Modifier.size(Kompakt.BarIconSize))
    }
}

/** The screen's one main action (Messages' "+"): white icon in a filled black rounded square. */
@Composable
fun PrimaryBarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(Kompakt.Touch)
            .background(Ink, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Paper, modifier = Modifier.size(Kompakt.BarIconSize))
    }
}

/** Bottom bar: 3 dp rule, then a padded row of controls. */
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

/** Outlined 40 dp rounded square with a 24 dp icon, in a 48 dp target (send, arrow pads). */
@Composable
fun OutlinedIconButton(icon: ImageVector, description: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(Kompakt.Touch).clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (enabled) Ink else Ink.copy(alpha = 0.3f)
        Box(Modifier.size(40.dp).border(1.5.dp, ink, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = ink, modifier = Modifier.size(Kompakt.ButtonIconSize))
        }
    }
}

/** Row separator: 1 dp dashed line (4 on, 3 off), inset like the row text. */
@Composable
fun DashedRowDivider() {
    Canvas(
        Modifier.fillMaxWidth().height(1.dp).padding(start = Kompakt.RowInsetStart, end = Kompakt.RowInsetEnd),
    ) {
        drawLine(
            color = Ink,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** A list row: bold title with [meta] (a time or count) at the right, optional one-line [subtitle]. */
@Composable
fun KompaktListRow(
    title: String,
    meta: String? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(3.dp, Ink) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = Kompakt.RowInsetStart, end = Kompakt.RowInsetEnd, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(
                title,
                modifier = Modifier.weight(1f).alignByBaseline(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                TextMMD(meta, modifier = Modifier.padding(start = 12.dp).alignByBaseline(), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!subtitle.isNullOrBlank()) {
            TextMMD(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Settings section header: bold, black, at the row inset. */
@Composable
fun SectionHeader(text: String) {
    TextMMD(
        text,
        modifier = Modifier.padding(start = Kompakt.RowInsetStart, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** One message, outlined, never filled. Received: left, 1 dp, square bottom-start. Mine: right, 2.5 dp. */
@Composable
fun MessageBubble(text: String, mine: Boolean, time: String?) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.86f).dp
    val shape = if (mine) RoundedCornerShape(14.dp) else RoundedCornerShape(14.dp, 14.dp, 14.dp, 0.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(Modifier.widthIn(max = maxWidth).border(if (mine) 2.5.dp else 1.dp, Ink, shape).padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextMMD(text, style = MaterialTheme.typography.bodyLarge)
        }
        if (!time.isNullOrBlank()) {
            TextMMD(time, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "•  23 Nov  •" before the first message of each day. */
@Composable
fun DaySeparator(day: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        TextMMD("•  $day  •", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compose-bar field: no outline or underline, just text, placeholder and a black cursor. */
@Composable
fun MessageField(value: String, onValueChange: (String) -> Unit, placeholder: String, onSend: () -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
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

/** Centred dialog: white, 3 dp frame, bold title, outlined secondary + filled primary. */
@Composable
fun KompaktDialogBox(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().background(Paper).border(3.dp, Ink).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextMMD(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            TextMMD(body, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                secondary?.let { (text, action) -> OutlinedButtonMMD(onClick = action) { TextMMD(text) } }
                ButtonMMD(onClick = primary.second) { TextMMD(primary.first) }
            }
        }
    }
}

// Icons: Material Symbols path data (Apache 2.0), inlined so no icons artifact is needed.
private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(pathData), fill = SolidColor(Ink)).build()

val BackIcon = icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
val MoreIcon = icon(
    "More",
    "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 " +
        "2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
)
val AddIcon = icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
val RefreshIcon = icon(
    "Refresh",
    "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 " +
        "7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 " +
        "4.22,1.78L13,11h7V4l-2.35,2.35z",
)

/** The stock send icon: a hollow paper plane, drawn as a stroke. */
val SendOutlineIcon: ImageVector = ImageVector.Builder("SendOutline", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Ink), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round) {
        moveTo(3f, 4f); lineTo(21f, 12f); lineTo(3f, 20f); lineTo(6f, 12f); close()
        moveTo(6f, 12f); lineTo(13f, 12f)
    }
}.build()
```

For other icons use Material Symbols *outlined* path data the same way, or
`androidx.compose.material.icons.Icons.Outlined.*` (core set only). For directions use MMD's
`R.drawable.chevron_filled_{left,up,down,right}` via `painterResource`.

## Screen recipes

**List screen** (Messages list, Hermes sessions, ntfy topics):

```kotlin
Column(Modifier.fillMaxSize()) {
    KompaktTopBar(title = "Messages") {
        BarIcon(RefreshIcon, "Refresh") { refresh() }
        Box {
            BarIcon(MoreIcon, "More options") { menuOpen = true }
            DropdownMenuMMD(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItemMMD(text = { TextMMD("Settings") }, onClick = { menuOpen = false; onSettings() })
            }
        }
        PrimaryBarIcon(AddIcon, "New") { onNew() }
    }
    if (status.isNotBlank()) TextMMD(status, Modifier.padding(16.dp))
    LazyColumnMMD(Modifier.fillMaxSize()) {
        items(rows, key = { it.id }) { r ->
            KompaktListRow(r.title, meta = r.time, subtitle = r.preview) { onOpen(r) }
            DashedRowDivider()
        }
    }
}
```

**Conversation screen**: `KompaktTopBar(label = who, title = subject, onBack = …)`, then a
`LazyColumnMMD(Modifier.weight(1f), scrollStep = 0, isScrollbarVisible = false)` of
`DaySeparator` + `MessageBubble`, a one-line status, and
`KompaktBottomBar { MessageField(Modifier.weight(1f)); OutlinedIconButton(SendOutlineIcon, "Send") }`.
Scroll to the last item with `listState.scrollToItem(last)` (no `animateScrollToItem`).

**Settings screen**: `KompaktTopBar(label = appName, title = "Settings", onBack = …)`, a
`verticalScroll` column of `SectionHeader` + rows separated by `DashedRowDivider()` (none after
the last in a section), `TextFieldMMD` for input, and at the end `OutlinedButtonMMD("Test")`
next to `ButtonMMD("Save")`.

**Short fixed choice list that must fit** (Twofold tile style): rows with `Modifier.weight(1f)`,
`RadioButtonMMD(selected, onClick = null)` inside `Modifier.selectable(...)`, bold label, one
line description, `DashedRowDivider` between rows.

## Review

Before you finish, run through the checklist in `kompakt-eink-design`, then grep your diff
for `animate`, `Animated`, `Crossfade`, `shadow`, `Elevation = [1-9]`, `ripple`, `Color(0x`
other than pure black or white, `alpha =` other than 0.3f on disabled ink, and
`FloatingActionButton`. Build on a real Kompakt when you can: the emulator hides ghosting.
