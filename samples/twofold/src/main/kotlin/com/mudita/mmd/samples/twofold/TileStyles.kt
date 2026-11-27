package com.mudita.mmd.samples.twofold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.samples.twofold.game.GOAL_TILE

val Ink = Color.Black
val Paper = Color.White

/**
 * How a tile shows its value. All are pure black and white with no gradients, shadows or
 * animation, as MMD asks for on E Ink.
 */
enum class TileMarks(val label: String, val description: String) {
    WEIGHT("Weight", "Heavier, then solid"),
    FRAMES("Frames", "Frame per doubling"),
    FOLD("Fold", "Corner folds in"),
    STEPS("Steps", "A dot per doubling"),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: WEIGHT
    }
}

/**
 * The tile settings, each chosen on its own: how values are marked, rounded or square
 * corners, and a light or dark board.
 */
data class TileLook(
    val marks: TileMarks = TileMarks.WEIGHT,
    val rounded: Boolean = false,
    val dark: Boolean = false,
) {
    /** Lines and fills. */
    val fg get() = if (dark) Paper else Ink

    /** The board behind the tiles. */
    val bg get() = if (dark) Ink else Paper

    companion object {
        /**
         * Reads the settings, or converts a choice saved by the older single "tile style"
         * setting: Rounded becomes Weight with rounded corners, Ink becomes Weight on a dark
         * board, and the rest map to their marks (Numbers, now gone, falls back to Weight).
         */
        fun load(marks: String?, rounded: Boolean?, dark: Boolean?, legacyStyle: String?) = when {
            marks != null -> TileLook(TileMarks.fromName(marks), rounded ?: false, dark ?: false)
            legacyStyle == "ROUNDED" -> TileLook(rounded = true)
            legacyStyle == "INK" -> TileLook(dark = true)
            else -> TileLook(TileMarks.fromName(legacyStyle))
        }
    }
}

/** MMD's corner radius for buttons and cards. */
private val TileCorner = 8.dp

/** 2 -> 1, 4 -> 2, 8 -> 3, ... */
private fun level(value: Int) = 31 - Integer.numberOfLeadingZeros(value)

/**
 * Draws one cell on a board of [TileLook.bg]. [scale] shrinks text and strokes for the small
 * previews in the tile settings.
 */
@Composable
fun StyledTile(value: Int?, look: TileLook, modifier: Modifier, scale: Float = 1f) {
    fun d(dp: Dp) = (dp.value * scale).coerceAtLeast(1f).dp

    val fg = look.fg
    val bg = look.bg
    val corner = if (look.rounded) d(TileCorner) else 0.dp
    val shape: Shape = if (look.rounded) RoundedCornerShape(corner) else RectangleShape

    if (value == null) {
        // Empty cells: a dashed outline instead of a grey one, so the board stays pure black and white.
        Box(modifier.dashedOutline(d(1.dp), corner, fg))
        return
    }

    var solid = false
    val tile = modifier.clip(shape)
    val box: Modifier = when (look.marks) {
        TileMarks.WEIGHT -> when {
            value >= 128 -> {
                solid = true
                tile.background(fg).goalFrame(value, d(4.dp), d(2.dp), bg, shape)
                    .pips(level(value) - 7, ::d, bg)
            }
            value >= 8 -> tile.border(d(3.dp), fg, shape)
            else -> tile.border(d(1.dp), fg, shape)
        }

        TileMarks.FRAMES -> {
            // 2 has one frame, 4 two, 8 three, 16 four; from 32 the tile fills in,
            // and from 2048 a frame sits inside the fill.
            val n = level(value)
            if (n <= 4) {
                var m = tile
                repeat(n) { m = m.border(d(2.dp), fg, shape).padding(d(3.dp)) }
                m
            } else {
                solid = true
                tile.background(fg).goalFrame(value, d(4.dp), d(2.dp), bg, shape)
                    .pips(n - 5, ::d, bg)
            }
        }

        TileMarks.FOLD -> {
            // 2 to 64: a corner grows on an open tile. From 128 the tile fills in and a corner
            // of the board's colour grows again; the goal tile also gets an inner frame.
            val n = level(value)
            solid = n >= 7
            // Previews start the corner larger so the smallest fold is still visible.
            val start = if (scale < 1f) 0.24f else 0.14f
            val fraction = start + 0.06f * (if (solid) minOf(n, 11) - 7 else n - 1)
            val base = if (solid) tile.background(fg) else tile.border(d(1.dp), fg, shape)
            base.foldedCorner(fraction, if (solid) bg else fg).goalFrame(value, d(4.dp), d(2.dp), bg, shape)
        }

        // Dots never shrink below 3dp, so they stay visible in the small previews.
        TileMarks.STEPS -> tile.border(d(if (value >= 128) 3.dp else 1.dp), fg, shape)
            .stepDots(level(value), maxOf(d(4.dp), 3.dp), maxOf(d(2.dp), 1.dp), fg)
    }

    val digits = value.toString().length
    val baseSize = when {
        digits <= 2 -> 34f
        digits == 3 -> 28f
        digits == 4 -> 22f
        else -> 17f
    }
    val weight = if (value >= 8) FontWeight.Bold else FontWeight.Medium
    // Shrink digits inside nested frames so they stay clear of the lines.
    val frameShrink = if (look.marks == TileMarks.FRAMES && !solid) 1f - 0.08f * (level(value) - 1) else 1f

    Box(box.semantics { contentDescription = "Tile $value" }, contentAlignment = Alignment.Center) {
        TextMMD(
            text = value.toString(),
            color = if (solid) bg else fg,
            fontWeight = weight,
            fontSize = (baseSize * scale * frameShrink).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A 1 dp dashed (2/2) outline, like the stock list dividers. */
private fun Modifier.dashedOutline(stroke: Dp, corner: Dp, color: Color): Modifier = drawBehind {
    val w = stroke.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
    val inset = w / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = w, pathEffect = dash),
    )
}

/** Fills a right triangle in the top-right corner, [fraction] of the tile's shorter side. */
private fun Modifier.foldedCorner(fraction: Float, color: Color): Modifier = drawBehind {
    val side = size.minDimension * fraction
    val path = Path().apply {
        moveTo(size.width - side, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, side)
        close()
    }
    drawPath(path, color)
}

/**
 * Centred rows of [count] dots along the bottom edge. When a row would run past the tile's
 * sides (past 2048 in Steps), the rest wrap onto a row above, so no dot is ever cut off.
 */
private fun Modifier.stepDots(count: Int, dot: Dp, gap: Dp, color: Color): Modifier = drawBehind {
    if (count <= 0) return@drawBehind
    val d = dot.toPx()
    val r = d / 2
    val step = d + gap.toPx()
    val perRow = (((size.width - 2 * d) + gap.toPx()) / step).toInt().coerceAtLeast(1)
    var left = count
    var row = 0
    while (left > 0) {
        val n = minOf(left, perRow)
        val startX = (size.width - (n * step - gap.toPx())) / 2 + r
        val y = size.height - r - d - row * step
        repeat(n) { drawCircle(color, radius = r, center = Offset(startX + it * step, y)) }
        left -= n
        row++
    }
}

/**
 * Dots in the board's colour on a solid tile, one per doubling after the tile turns solid, so
 * 128, 256, 512 and 1024 no longer look alike. They never shrink below 3 dp, to stay visible
 * in previews.
 */
private fun Modifier.pips(count: Int, d: (Dp) -> Dp, color: Color): Modifier =
    stepDots(count, maxOf(d(4.dp), 3.dp), maxOf(d(2.dp), 1.dp), color)

/** On a filled tile, adds an inner frame in the board's colour once the goal tile is reached. */
private fun Modifier.goalFrame(value: Int, inset: Dp, stroke: Dp, color: Color, shape: Shape): Modifier =
    if (value >= GOAL_TILE) padding(inset).border(stroke, color, shape) else this
