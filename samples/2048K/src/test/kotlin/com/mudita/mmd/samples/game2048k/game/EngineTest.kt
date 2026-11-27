package com.mudita.mmd.samples.game2048k.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private fun grid(vararg rows: String): Grid =
        rows.map { row -> row.trim().split(Regex("\\s+")).map { if (it == ".") null else it.toInt() } }

    @Test
    fun slideWestMergesOncePerPair() {
        val r = Engine.slide(grid("2 2 2 2", "4 . 4 .", "2 4 8 16", ". . . 2"), Direction.WEST)
        assertEquals(grid("4 4 . .", "8 . . .", "2 4 8 16", "2 . . ."), r.grid)
        assertEquals(4 + 4 + 8, r.scoreGained)
        assertTrue(r.changed)
    }

    @Test
    fun slideEastAndVertical() {
        val g = grid("2 . 2 4", ". . . .", "2 . . .", "2 . . 4")
        assertEquals(grid(". . 4 4", ". . . .", ". . . 2", ". . 2 4"), Engine.slide(g, Direction.EAST).grid)
        assertEquals(grid("4 . 2 8", "2 . . .", ". . . .", ". . . ."), Engine.slide(g, Direction.NORTH).grid)
        assertEquals(grid(". . . .", ". . . .", "2 . . .", "4 . 2 8"), Engine.slide(g, Direction.SOUTH).grid)
    }

    @Test
    fun mergedTileDoesNotMergeAgainInSameMove() {
        val three = Engine.slide(grid("4 4 4 .", ". . . .", ". . . .", ". . . ."), Direction.WEST)
        assertEquals(listOf(8, 4, null, null), three.grid[0])
        val r = Engine.slide(grid("4 4 8 .", ". . . .", ". . . .", ". . . ."), Direction.WEST)
        assertEquals(listOf(8, 8, null, null), r.grid[0])
    }

    @Test
    fun noChangeWhenBlocked() {
        val r = Engine.slide(grid("2 4 8 16", ". . . .", ". . . .", ". . . ."), Direction.WEST)
        assertFalse(r.changed)
        assertEquals(0, r.scoreGained)
    }

    @Test
    fun gameOverDetection() {
        assertTrue(Engine.isGameOver(grid("2 4 2 4", "4 2 4 2", "2 4 2 4", "4 2 4 2")))
        assertFalse(Engine.isGameOver(grid("2 4 2 4", "4 2 4 2", "2 4 2 4", "4 2 4 4")))
    }

    @Test
    fun moveSpawnsOneTileAndUndoRestores() {
        val start = GameState(grid("2 2 . .", ". . . .", ". . . .", ". . . ."), score = 0, best = 10)
        val after = start.move(Direction.WEST, Random(1))
        assertEquals(2, after.grid.flatten().count { it != null })
        assertEquals(4, after.score)
        assertEquals(10, after.best)
        val undone = after.undo()
        assertEquals(start.grid, undone.grid)
        assertEquals(0, undone.score)
        assertNull(undone.previous)
    }

    @Test
    fun restartCanBeUndone() {
        val s = GameState(grid("2 2 . .", ". . . .", ". . . .", ". . . ."), score = 40, best = 40, moves = 9)
        val fresh = s.restart(Random(5))
        assertEquals(0, fresh.score)
        assertEquals(40, fresh.best)
        assertEquals(s.grid, fresh.undo().grid)
        assertEquals(40, fresh.undo().score)
    }

    @Test
    fun undoingNewGameKeepsContinuePastGoal() {
        val won = GameState(grid("2048 . . .", ". . . .", ". . . .", ". . . ."), 0, 0).continuePastGoal()
        val restored = won.restart(Random(1)).undo()
        assertFalse(restored.hasWon)
        assertEquals(won.grid, restored.grid)
    }

    @Test
    fun codecRoundTrip() {
        val s = GameState.new(4, best = 512, random = Random(7)).move(Direction.NORTH, Random(3)).move(Direction.WEST, Random(4))
        val decoded = GameCodec.decode(GameCodec.encode(s))!!
        assertEquals(s.grid, decoded.grid)
        assertEquals(s.score, decoded.score)
        assertEquals(s.best, decoded.best)
        assertEquals(s.moves, decoded.moves)
        assertNull(GameCodec.decode("garbage"))
    }

    @Test
    fun reachingGoalPausesUntilContinue() {
        val s = GameState(grid("1024 1024 . .", ". . . .", ". . . .", ". . . ."), 0, 0).move(Direction.WEST, Random(2))
        assertTrue(s.hasWon)
        assertEquals(s, s.move(Direction.EAST))
        assertFalse(s.continuePastGoal().hasWon)
    }
}
