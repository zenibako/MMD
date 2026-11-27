/*
 * Sliding and merging logic adapted from android-2048-compose
 * (https://github.com/alexjlockwood/android-2048-compose), GameViewModel.kt and domain/.
 *
 * MIT License
 *
 * Copyright (c) 2020 Alex Lockwood
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Changes for 2048K: no Compose/ViewModel/serialization dependencies, tiles are plain
 * values (no animation ids), the random source is injectable for tests, and the grid
 * size is a parameter.
 */
package com.mudita.mmd.samples.twofold.game

import kotlin.random.Random

/** A location in the grid. */
data class Cell(val row: Int, val col: Int)

/** The four swipe directions. */
enum class Direction { NORTH, SOUTH, EAST, WEST }

/** A grid of tile values; `null` is an empty cell. Always square. */
typealias Grid = List<List<Int?>>

private data class GridTile(val cell: Cell, val value: Int)

/** How a tile moved during a move. `from == null` means the tile was created (merge or spawn). */
private data class GridTileMovement(val from: GridTile?, val to: GridTile)

/** Result of a move. [changed] is false when nothing could slide that way. */
data class MoveResult(val grid: Grid, val scoreGained: Int, val changed: Boolean)

object Engine {

    fun emptyGrid(size: Int): Grid = List(size) { List<Int?>(size) { null } }

    /** A fresh board with two starting tiles. */
    fun newGrid(size: Int, random: Random = Random): Grid {
        var grid = emptyGrid(size)
        repeat(2) { grid = spawnTile(grid, random) }
        return grid
    }

    /**
     * Slides every tile towards [direction], merging equal neighbours once per move.
     * Does not spawn a new tile; see [spawnTile].
     */
    fun slide(grid: Grid, direction: Direction): MoveResult {
        val (newGrid, movements) = makeMove(grid, direction)
        val changed = movements.any { it.from == null || it.from.cell != it.to.cell }
        // Every tile created by a merge adds its value to the score.
        val gained = movements.filter { it.from == null }.sumOf { it.to.value }
        return MoveResult(newGrid, gained, changed)
    }

    /** Places a 2 (90%) or 4 (10%) in a random empty cell. Returns [grid] unchanged if full. */
    fun spawnTile(grid: Grid, random: Random = Random): Grid {
        val empty = grid.flatMapIndexed { r, row -> row.indices.filter { row[it] == null }.map { c -> Cell(r, c) } }
        if (empty.isEmpty()) return grid
        val cell = empty[random.nextInt(empty.size)]
        val value = if (random.nextFloat() < 0.9f) 2 else 4
        return grid.mapCells { r, c, v -> if (r == cell.row && c == cell.col) value else v }
    }

    /** No move in any direction would change the board. */
    fun isGameOver(grid: Grid): Boolean = Direction.entries.none { slide(grid, it).changed }

    fun maxTile(grid: Grid): Int = grid.flatten().filterNotNull().maxOrNull() ?: 0
}

private fun makeMove(grid: Grid, direction: Direction): Pair<Grid, List<GridTileMovement>> {
    val size = grid.size
    val numRotations = when (direction) {
        Direction.WEST -> 0
        Direction.SOUTH -> 1
        Direction.EAST -> 2
        Direction.NORTH -> 3
    }

    // Rotate the grid so that every move can be processed as a swipe from right to left.
    var updatedGrid = grid.rotate(numRotations)
    val movements = mutableListOf<GridTileMovement>()

    updatedGrid = List(updatedGrid.size) { rowIndex ->
        val tiles = updatedGrid[rowIndex].toMutableList()
        var lastSeenTileIndex: Int? = null
        var lastSeenEmptyIndex: Int? = null
        for (colIndex in tiles.indices) {
            val current = tiles[colIndex]
            if (current == null) {
                // Keep track of the first empty index we find.
                if (lastSeenEmptyIndex == null) lastSeenEmptyIndex = colIndex
                continue
            }

            // A tile that could be shifted, merged, or left where it is.
            val currentGridTile = GridTile(rotatedCellAt(rowIndex, colIndex, numRotations, size), current)

            if (lastSeenTileIndex == null) {
                // The first tile in this row.
                if (lastSeenEmptyIndex == null) {
                    movements.add(GridTileMovement(currentGridTile, currentGridTile))
                    lastSeenTileIndex = colIndex
                } else {
                    // Shift to the furthest empty cell.
                    val target = rotatedCellAt(rowIndex, lastSeenEmptyIndex, numRotations, size)
                    movements.add(GridTileMovement(currentGridTile, GridTile(target, current)))
                    tiles[lastSeenEmptyIndex] = current
                    tiles[colIndex] = null
                    lastSeenTileIndex = lastSeenEmptyIndex
                    lastSeenEmptyIndex++
                }
            } else {
                // There is a previous tile in the row to compare against.
                if (tiles[lastSeenTileIndex] == current) {
                    // Merge into the previous tile.
                    val target = rotatedCellAt(rowIndex, lastSeenTileIndex, numRotations, size)
                    movements.add(GridTileMovement(currentGridTile, GridTile(target, current)))
                    val merged = current * 2
                    movements.add(GridTileMovement(null, GridTile(target, merged)))
                    tiles[lastSeenTileIndex] = merged
                    tiles[colIndex] = null
                    lastSeenTileIndex = null
                    if (lastSeenEmptyIndex == null) lastSeenEmptyIndex = colIndex
                } else {
                    if (lastSeenEmptyIndex == null) {
                        movements.add(GridTileMovement(currentGridTile, currentGridTile))
                    } else {
                        // Shift towards the previous tile.
                        val target = rotatedCellAt(rowIndex, lastSeenEmptyIndex, numRotations, size)
                        movements.add(GridTileMovement(currentGridTile, GridTile(target, current)))
                        tiles[lastSeenEmptyIndex] = current
                        tiles[colIndex] = null
                        lastSeenEmptyIndex++
                    }
                    lastSeenTileIndex++
                }
            }
        }
        tiles
    }

    // Rotate back to the original orientation.
    updatedGrid = updatedGrid.rotate(Math.floorMod(-numRotations, 4))
    return updatedGrid to movements
}

private fun <T> List<List<T>>.rotate(numRotations: Int): List<List<T>> =
    mapCells { row, col, _ ->
        val (r, c) = rotatedCellAt(row, col, numRotations, size)
        this[r][c]
    }

private fun rotatedCellAt(row: Int, col: Int, numRotations: Int, size: Int): Cell = when (numRotations) {
    0 -> Cell(row, col)
    1 -> Cell(size - 1 - col, row)
    2 -> Cell(size - 1 - row, size - 1 - col)
    3 -> Cell(col, size - 1 - row)
    else -> throw IllegalArgumentException("numRotations must be in [0,3]")
}

internal fun <T> List<List<T>>.mapCells(transform: (row: Int, col: Int, T) -> T): List<List<T>> =
    mapIndexed { r, row -> row.mapIndexed { c, v -> transform(r, c, v) } }
