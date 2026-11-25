package com.mudita.mmd.samples.twofold.game

import kotlin.random.Random

const val GOAL_TILE = 2048

/** Everything needed to draw and resume a game. Immutable; every move returns a new one. */
data class GameState(
    val grid: Grid,
    val score: Int,
    val best: Int,
    /** The state before the last move, for a single-step undo. */
    val previous: Snapshot? = null,
    /** True once the player chose to continue past [GOAL_TILE]. */
    val keepGoing: Boolean = false,
    val moves: Int = 0,
) {
    data class Snapshot(val grid: Grid, val score: Int, val moves: Int)

    val isGameOver: Boolean get() = Engine.isGameOver(grid)
    val hasWon: Boolean get() = !keepGoing && Engine.maxTile(grid) >= GOAL_TILE
    val canUndo: Boolean get() = previous != null

    companion object {
        fun new(size: Int = 4, best: Int = 0, random: Random = Random) =
            GameState(grid = Engine.newGrid(size, random), score = 0, best = best)
    }
}

/** Applies a swipe. Returns the same state when nothing moves, so the screen does not redraw. */
fun GameState.move(direction: Direction, random: Random = Random): GameState {
    if (hasWon) return this
    val result = Engine.slide(grid, direction)
    if (!result.changed) return this
    val newScore = score + result.scoreGained
    return copy(
        grid = Engine.spawnTile(result.grid, random),
        score = newScore,
        best = maxOf(best, newScore),
        previous = GameState.Snapshot(grid, score, moves),
        moves = moves + 1,
    )
}

/** Steps back one move. Best score is kept, since it was really reached. */
fun GameState.undo(): GameState {
    val p = previous ?: return this
    return copy(grid = p.grid, score = p.score, moves = p.moves, previous = null)
}

/** Starts over. The abandoned game stays reachable through [undo], so no confirmation is needed. */
fun GameState.restart(random: Random = Random): GameState =
    GameState.new(grid.size, best, random).copy(previous = GameState.Snapshot(grid, score, moves))

fun GameState.continuePastGoal(): GameState = copy(keepGoing = true)

/** Compact text form for SharedPreferences: `size|cells|score|best|keepGoing|moves`. */
object GameCodec {
    fun encode(s: GameState): String = listOf(
        s.grid.size,
        s.grid.flatten().joinToString(",") { (it ?: 0).toString() },
        s.score,
        s.best,
        if (s.keepGoing) 1 else 0,
        s.moves,
    ).joinToString("|")

    fun decode(text: String?): GameState? = runCatching {
        val parts = text!!.split("|")
        val size = parts[0].toInt()
        val cells = parts[1].split(",").map { it.toInt().takeIf { v -> v > 0 } }
        require(cells.size == size * size)
        GameState(
            grid = cells.chunked(size),
            score = parts[2].toInt(),
            best = parts[3].toInt(),
            keepGoing = parts[4] == "1",
            moves = parts[5].toInt(),
        )
    }.getOrNull()
}
