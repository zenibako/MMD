package com.mudita.mmd.samples.twofold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

private val Ink = Color.Black
private val Paper = Color.White

/** Minimum swipe length before a drag counts as a move. */
private val SwipeThreshold = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(state: GameState, onStateChange: (GameState) -> Unit) {
    val current by rememberUpdatedState(state)
    val emit by rememberUpdatedState(onStateChange)
    val onMove: (Direction) -> Unit = { emit(current.move(it)) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(
            title = { TextMMD("Twofold", fontWeight = FontWeight.Bold) },
            actions = {
                BarAction("Undo", enabled = state.canUndo) { onStateChange(state.undo()) }
                BarAction("New", enabled = true) { onStateChange(state.restart()) }
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
                Board(state)
                when {
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
private fun BarAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextMMD(
        text = label,
        color = if (enabled) Ink else Ink.copy(alpha = 0.35f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
private fun Board(state: GameState) {
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
                    Tile(state.grid[row][col], Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

/**
 * Tiles are told apart by weight instead of colour, since the screen is greyscale:
 * empty is a faint outline, 2 and 4 are thin, 8 to 64 are heavy outlines, 128 and up are
 * solid black, and the goal tile and above get an extra inner frame.
 */
@Composable
private fun Tile(value: Int?, modifier: Modifier) {
    if (value == null) {
        Box(modifier.border(1.dp, Ink.copy(alpha = 0.25f)))
        return
    }
    val solid = value >= 128
    val base = when {
        solid -> modifier.background(Ink)
        value >= 8 -> modifier.border(3.dp, Ink)
        else -> modifier.border(1.dp, Ink)
    }
    val framed = if (value >= GOAL_TILE) base.padding(4.dp).border(2.dp, Paper) else base
    val digits = value.toString().length
    Box(
        framed.semantics { contentDescription = "Tile $value" },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = value.toString(),
            color = if (solid) Paper else Ink,
            fontWeight = if (value >= 8) FontWeight.Bold else FontWeight.Normal,
            fontSize = when {
                digits <= 2 -> 34.sp
                digits == 3 -> 28.sp
                digits == 4 -> 22.sp
                else -> 17.sp
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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
