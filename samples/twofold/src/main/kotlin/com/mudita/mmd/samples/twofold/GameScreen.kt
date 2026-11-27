package com.mudita.mmd.samples.twofold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.R as MmdR
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.samples.twofold.game.Direction
import com.mudita.mmd.samples.twofold.game.GOAL_TILE
import com.mudita.mmd.samples.twofold.game.GameState
import com.mudita.mmd.samples.twofold.game.continuePastGoal
import com.mudita.mmd.samples.twofold.game.move
import com.mudita.mmd.samples.twofold.game.restart
import com.mudita.mmd.samples.twofold.game.undo
import kotlin.math.abs

/** Minimum swipe length before a drag counts as a move. */
private val SwipeThreshold = 32.dp

@Composable
fun GameScreen(
    state: GameState,
    /** Applies a change to the latest game state, so quick inputs never act on a stale board. */
    onUpdate: ((GameState) -> GameState) -> Unit,
    tileStyle: TileStyle,
    onTileStyleChange: (TileStyle) -> Unit,
) {
    var pickingStyle by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val update by rememberUpdatedState(onUpdate)
    val onMove: (Direction) -> Unit = { dir -> update { it.move(dir) } }

    if (pickingStyle) {
        BackHandler { pickingStyle = false }
        StyleScreen(selected = tileStyle, onSelect = onTileStyleChange, onBack = { pickingStyle = false })
        return
    }

    // The ⋮ sheet draws over the whole screen, so the game sits in a Box under it.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            KompaktTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    // Undo is the one action used mid-game, so it stays on the bar; the rest
                    // open in a bottom sheet behind ⋮, as in the stock apps.
                    BarIcon(UndoIcon, "Undo", enabled = state.canUndo) { onUpdate { it.undo() } }
                    BarIcon(MoreIcon, "Menu") { menuOpen = true }
                },
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Swipes are read anywhere between the bars, not just on the board,
                    // so a slightly-off swipe still lands.
                    .swipeToMove(onMove)
                    .padding(horizontal = Kompakt.TextInset, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextMMD("Score ${state.score}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    TextMMD("Best ${state.best}", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    Board(state, tileStyle)
                    when {
                        state.hasWon -> Overlay(
                            title = "You made $GOAL_TILE",
                            body = "${state.moves} moves. Keep going?",
                            primary = "Keep going" to { onUpdate { it.continuePastGoal() } },
                            secondary = "New game" to { onUpdate { it.restart() } },
                        )
                        state.isGameOver -> Overlay(
                            title = "No moves left",
                            body = "Score ${state.score}.",
                            primary = "New game" to { onUpdate { it.restart() } },
                            secondary = if (state.canUndo) "Undo" to { onUpdate { it.undo() } } else null,
                        )
                    }
                }
            }

            KompaktBottomBar { DirectionPad(onMove) }
        }

        if (menuOpen) {
            KompaktSheet(title = "Menu", onDismiss = { menuOpen = false }) {
                SheetButton("New game") {
                    menuOpen = false
                    onUpdate { it.restart() }
                }
                SheetButton("Tile style") {
                    menuOpen = false
                    pickingStyle = true
                }
            }
        }
    }
}

@Composable
private fun Board(state: GameState, style: TileStyle) {
    val size = state.grid.size
    Column(
        Modifier
            .fillMaxSize()
            .border(3.dp, Ink)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until size) {
                    StyledTile(state.grid[row][col], style, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

/** The in-game result card over the board (kept as an overlay so the final board stays visible). */
@Composable
private fun Overlay(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(3.dp, Ink)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextMMD(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            TextMMD(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (secondary != null) {
                    KompaktButton(secondary.first, filled = false, modifier = Modifier.weight(1f), onClick = secondary.second)
                }
                KompaktButton(primary.first, filled = true, modifier = Modifier.weight(1f), onClick = primary.second)
            }
        }
    }
}

/**
 * Tile style choice as its own screen: a back arrow in the top bar (and the system back
 * gesture) returns to the game. Picking a style applies it at once.
 */
@Composable
private fun StyleScreen(selected: TileStyle, onSelect: (TileStyle) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        KompaktTopBar(title = "Tile style", onBack = onBack)
        TileStyle.entries.forEach { style ->
            // Radio rows as in stock Settings: radio at 16 dp, label at about 61 dp, a dashed
            // divider from the label.
            Row(
                Modifier
                    .height(StyleRowHeight)
                    .fillMaxWidth()
                    .selectable(selected = style == selected, onClick = { onSelect(style) })
                    .padding(start = Kompakt.TextInset, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(StyleLabelInset - Kompakt.TextInset)) {
                    RadioButtonMMD(selected = style == selected, onClick = null)
                }
                Column(Modifier.weight(1f)) {
                    TextMMD(style.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    TextMMD(style.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(2, 256, 2048).forEach {
                        StyledTile(it, style, Modifier.size(34.dp), scale = 0.42f)
                    }
                }
            }
            DashedRowDivider(start = StyleLabelInset)
        }
    }
}

/** Where radio-row labels start, as in the stock Settings radio screens. */
private val StyleLabelInset = 61.dp

/** Two-line radio rows: label and description, with room for the tile previews. */
private val StyleRowHeight = 72.dp

/**
 * On-screen arrows for people who prefer tapping to swiping: the skill's outlined icon button
 * (40 dp square, 2 dp stroke, 10 dp corners, 24 dp icon, 48 dp target) with MMD's chevrons.
 */
@Composable
private fun RowScope.DirectionPad(onMove: (Direction) -> Unit) {
    listOf(
        MmdR.drawable.chevron_filled_left to Direction.WEST,
        MmdR.drawable.chevron_filled_up to Direction.NORTH,
        MmdR.drawable.chevron_filled_down to Direction.SOUTH,
        MmdR.drawable.chevron_filled_right to Direction.EAST,
    ).forEach { (chevron, dir) ->
        val label = "Move ${dir.name.lowercase()}"
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(onClickLabel = label) { onMove(dir) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp).border(2.dp, Ink, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(chevron), contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Reads one direction per drag, from the dominant axis of the whole gesture. */
@Composable
private fun Modifier.swipeToMove(onMove: (Direction) -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val move by rememberUpdatedState(onMove)
    return pointerInput(threshold) {
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = { total = Offset.Zero },
            onDragEnd = {
                val (dx, dy) = total
                if (maxOf(abs(dx), abs(dy)) >= threshold) {
                    move(
                        if (abs(dx) > abs(dy)) {
                            if (dx > 0) Direction.EAST else Direction.WEST
                        } else {
                            if (dy > 0) Direction.SOUTH else Direction.NORTH
                        },
                    )
                }
            },
        ) { change, drag ->
            change.consume()
            total += drag
        }
    }
}
