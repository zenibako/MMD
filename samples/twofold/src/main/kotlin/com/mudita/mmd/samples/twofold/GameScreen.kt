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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.R
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
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

    Column(Modifier.fillMaxSize()) {
        KompaktTopBar(
            label = "Twofold · Best ${state.best}",
            title = "Score ${state.score}",
            actions = {
                // Undo is the one action used mid-game, so it stays on the bar; the rest live in
                // the overflow menu, as in MMD's own menu sample.
                BarIcon(UndoIcon, "Undo", enabled = state.canUndo) { onUpdate { it.undo() } }
                Box {
                    BarIcon(MoreIcon, "More options") { menuOpen = true }
                    DropdownMenuMMD(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItemMMD(
                            text = { TextMMD("New game") },
                            onClick = {
                                menuOpen = false
                                onUpdate { it.restart() }
                            },
                        )
                        DashedRowDivider()
                        DropdownMenuItemMMD(
                            text = { TextMMD("Tile style") },
                            onClick = {
                                menuOpen = false
                                pickingStyle = true
                            },
                        )
                    }
                }
            },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Swipes are read anywhere between the bars, not just on the board,
                // so a slightly-off swipe still lands.
                .swipeToMove(onMove)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Board(state, tileStyle)
                when {
                    state.hasWon -> Overlay(
                        title = "You made $GOAL_TILE",
                        body = "${state.moves} moves. Keep going for a bigger tile?",
                        primary = "Keep going" to { onUpdate { it.continuePastGoal() } },
                        secondary = "New game" to { onUpdate { it.restart() } },
                    )
                    state.isGameOver -> Overlay(
                        title = "No moves left",
                        body = "Score ${state.score}. Undo the last move or start again.",
                        primary = "New game" to { onUpdate { it.restart() } },
                        secondary = if (state.canUndo) "Undo" to { onUpdate { it.undo() } } else null,
                    )
                }
            }
        }

        BottomBar { DirectionPad(onMove) }
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

@Composable
private fun Overlay(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(3.dp, Ink)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextMMD(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            TextMMD(body, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (secondary != null) {
                    OutlinedButtonMMD(onClick = secondary.second) { TextMMD(secondary.first) }
                }
                ButtonMMD(onClick = primary.second) { TextMMD(primary.first) }
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
        KompaktTopBar(label = "Twofold", title = "Tile style", onBack = onBack)
        TileStyle.entries.forEachIndexed { i, style ->
            if (i > 0) DashedRowDivider()
            // Rows share the screen height evenly, so all styles fit without scrolling.
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .selectable(selected = style == selected, onClick = { onSelect(style) })
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButtonMMD(selected = style == selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    TextMMD(style.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    TextMMD(style.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(2, 16, 256, 2048).forEach {
                        StyledTile(it, style, Modifier.size(34.dp), scale = 0.42f)
                    }
                }
            }
        }
    }
}

/**
 * On-screen arrows for people who prefer tapping to swiping. Drawn like the system Messages
 * app's send button: a 40dp outlined rounded square with a 24dp icon in a 48dp touch target,
 * using MMD's own chevron drawables.
 */
@Composable
private fun RowScope.DirectionPad(onMove: (Direction) -> Unit) {
    listOf(
        R.drawable.chevron_filled_left to Direction.WEST,
        R.drawable.chevron_filled_up to Direction.NORTH,
        R.drawable.chevron_filled_down to Direction.SOUTH,
        R.drawable.chevron_filled_right to Direction.EAST,
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
                Modifier.size(40.dp).border(1.5.dp, Ink, RoundedCornerShape(9.dp)),
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
