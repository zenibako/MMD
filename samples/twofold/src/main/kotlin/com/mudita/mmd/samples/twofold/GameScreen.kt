package com.mudita.mmd.samples.twofold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onStateChange: (GameState) -> Unit,
    tileStyle: TileStyle,
    onTileStyleChange: (TileStyle) -> Unit,
) {
    var pickingStyle by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(state)
    val emit by rememberUpdatedState(onStateChange)
    val onMove: (Direction) -> Unit = { if (!pickingStyle) emit(current.move(it)) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(
            title = { TextMMD("Twofold", fontWeight = FontWeight.Bold) },
            actions = {
                // Undo is the one action used mid-game, so it stays on the bar; the rest live in
                // the overflow menu, as in MMD's own menu sample.
                IconButton(onClick = { onStateChange(state.undo()) }, enabled = state.canUndo) {
                    Icon(UndoIcon, contentDescription = "Undo", tint = if (state.canUndo) Ink else Ink.copy(alpha = 0.35f))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(MoreIcon, contentDescription = "More options", tint = Ink)
                    }
                    DropdownMenuMMD(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItemMMD(
                            text = { TextMMD("New game") },
                            onClick = {
                                menuOpen = false
                                pickingStyle = false
                                onStateChange(state.restart())
                            },
                        )
                        HorizontalDividerMMD()
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // Swipes are read anywhere below the app bar, not just on the board,
                // so a slightly-off swipe still lands.
                .swipeToMove(onMove),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreBox("Score", state.score, Modifier.weight(1f))
                ScoreBox("Best", state.best, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Board(state, tileStyle)
                when {
                    pickingStyle -> StylePicker(
                        selected = tileStyle,
                        onSelect = onTileStyleChange,
                        onDone = { pickingStyle = false },
                    )
                    state.hasWon -> Overlay(
                        title = "You made $GOAL_TILE",
                        body = "${state.moves} moves. Keep going for a bigger tile?",
                        primary = "Keep going" to { onStateChange(state.continuePastGoal()) },
                        secondary = "New game" to { onStateChange(state.restart()) },
                    )
                    state.isGameOver -> Overlay(
                        title = "No moves left",
                        body = "Score ${state.score}. Undo the last move or start again.",
                        primary = "New game" to { onStateChange(state.restart()) },
                        secondary = if (state.canUndo) "Undo" to { onStateChange(state.undo()) } else null,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            DirectionPad(onMove)
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .border(2.dp, Ink)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(label.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp)
        TextMMD(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
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

/** Lists the tile styles with a preview row each. Picking one applies it at once. */
@Composable
private fun StylePicker(selected: TileStyle, onSelect: (TileStyle) -> Unit, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .border(3.dp, Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TileStyle.entries.forEach { style ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = style == selected, onClick = { onSelect(style) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButtonMMD(selected = style == selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    TextMMD(style.label, fontWeight = FontWeight.Bold)
                    TextMMD(style.description, fontSize = 11.sp, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(2, 16, 256, 2048).forEach {
                        StyledTile(it, style, Modifier.size(30.dp), scale = 0.36f)
                    }
                }
            }
        }
        ButtonMMD(onClick = onDone, modifier = Modifier.align(Alignment.End)) { TextMMD("Done") }
    }
}

/** On-screen arrows for people who prefer tapping to swiping. */
@Composable
private fun DirectionPad(onMove: (Direction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "←" to Direction.WEST,
            "↑" to Direction.NORTH,
            "↓" to Direction.SOUTH,
            "→" to Direction.EAST,
        ).forEach { (arrow, dir) ->
            OutlinedButtonMMD(
                onClick = { onMove(dir) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Move ${dir.name.lowercase()}" },
            ) {
                TextMMD(arrow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
