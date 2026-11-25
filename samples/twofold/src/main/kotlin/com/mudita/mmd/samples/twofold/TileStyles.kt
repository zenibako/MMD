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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.samples.twofold.game.GOAL_TILE

internal val Ink = Color.Black
internal val Paper = Color.White

/** MMD's corner radius for buttons and cards. */
private val MmdCorner = RoundedCornerShape(8.dp)

/**
 * Ways to draw tiles. All of them are pure black and white with no gradients, shadows or
 * animation, as MMD asks for on E Ink; they differ in how a value's size is shown.
 */
enum class TileStyle(val label: String, val description: String) {
    WEIGHT("Weight", "Heavier, then solid"),
    ROUNDED("Rounded", "Weight, rounded"),
    FRAMES("Frames", "A frame per doubling"),
    INK("Ink", "Solid, framed"),
    NUMBERS("Numbers", "Plain grid, bolder digits"),
    FOLD("Fold", "Corner folds in"),
    STEPS("Steps", "A dot per doubling"),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: WEIGHT
    }
}

/** 2 -> 1, 4 -> 2, 8 -> 3, ... */
private fun level(value: Int) = 31 - Integer.numberOfLeadingZeros(value)

/**
 * Draws one cell. [scale] shrinks text and strokes for the small previews in the style picker.
 */
@Composable
fun StyledTile(value: Int?, style: TileStyle, modifier: Modifier, scale: Float = 1f) {
    fun d(dp: Dp) = (dp.value * scale).coerceAtLeast(1f).dp

    if (value == null) {
        val empty = when (style) {
            TileStyle.NUMBERS -> modifier.border(d(1.dp), Ink)
            TileStyle.ROUNDED -> modifier.border(d(1.dp), Ink.copy(alpha = 0.25f), MmdCorner)
            else -> modifier.border(d(1.dp), Ink.copy(alpha = 0.25f))
        }
        Box(empty)
        return
    }

    var solid = false
    var underline = false
    val shape: Shape = if (style == TileStyle.ROUNDED) MmdCorner else RectangleShape
    val box: Modifier = when (style) {
        TileStyle.WEIGHT, TileStyle.ROUNDED -> when {
            value >= 128 -> {
                solid = true
                modifier.clip(shape).background(Ink).goalFrame(value, d(4.dp), d(2.dp), shape)
            }
            value >= 8 -> modifier.border(d(3.dp), Ink, shape)
            else -> modifier.border(d(1.dp), Ink, shape)
        }

        TileStyle.FRAMES -> {
            // 2 has one frame, 4 two, 8 three, 16 four; from 32 the tile fills in,
            // and from 2048 a white frame sits inside the fill.
            val n = level(value)
            if (n <= 4) {
                var m = modifier
                repeat(n) { m = m.border(d(2.dp), Ink).padding(d(3.dp)) }
                m
            } else {
                solid = true
                modifier.background(Ink).goalFrame(value, d(4.dp), d(2.dp), RectangleShape)
            }
        }

        TileStyle.INK -> {
            solid = true
            var m = modifier.background(Ink)
            // One white inner frame from 128, two from the goal tile.
            if (value >= 128) m = m.padding(d(4.dp)).border(d(2.dp), Paper)
            if (value >= GOAL_TILE) m = m.padding(d(4.dp)).border(d(2.dp), Paper)
            m
        }

        TileStyle.FOLD -> {
            // 2 to 64: a black corner grows on a white tile. From 128 the tile turns black and a
            // white corner grows again; the goal tile also gets a white inner frame.
            val n = level(value)
            solid = n >= 7
            // Previews start the corner larger so the smallest fold is still visible.
            val start = if (scale < 1f) 0.24f else 0.14f
            val fraction = start + 0.06f * (if (solid) minOf(n, 11) - 7 else n - 1)
            val base = if (solid) modifier.background(Ink) else modifier.border(d(1.dp), Ink)
            base.foldedCorner(fraction, if (solid) Paper else Ink).goalFrame(value, d(4.dp), d(2.dp), RectangleShape)
        }

        // Dots never shrink below 3dp, so they stay visible in the small previews.
        TileStyle.STEPS -> modifier.border(d(if (value >= 128) 3.dp else 1.dp), Ink)
            .stepDots(level(value), maxOf(d(4.dp), 3.dp), maxOf(d(2.dp), 1.dp))

        TileStyle.NUMBERS -> {
            underline = value >= 128
            if (value >= GOAL_TILE) modifier.border(d(1.dp), Ink).padding(d(4.dp)).border(d(3.dp), Ink) else modifier.border(d(1.dp), Ink)
        }
    }

    val digits = value.toString().length
    val baseSize = when {
        digits <= 2 -> 34f
        digits == 3 -> 28f
        digits == 4 -> 22f
        else -> 17f
    }
    val weight = when (style) {
        TileStyle.NUMBERS -> when {
            value >= 64 -> FontWeight.Black
            value >= 8 -> FontWeight.Bold
            else -> FontWeight.Light
        }
        TileStyle.INK -> FontWeight.Bold
        else -> if (value >= 8) FontWeight.Bold else FontWeight.Normal
    }
    // Shrink digits inside nested frames so they stay clear of the lines.
    val frameShrink = if (style == TileStyle.FRAMES && !solid) 1f - 0.08f * (level(value) - 1) else 1f

    Box(box.semantics { contentDescription = "Tile $value" }, contentAlignment = Alignment.Center) {
        TextMMD(
            text = value.toString(),
            color = if (solid) Paper else Ink,
            fontWeight = weight,
            fontSize = (baseSize * scale * frameShrink).sp,
            textDecoration = if (underline) TextDecoration.Underline else null,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
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

/** A centred row of [count] dots along the bottom edge. */
private fun Modifier.stepDots(count: Int, dot: Dp, gap: Dp): Modifier = drawBehind {
    val r = dot.toPx() / 2
    val step = dot.toPx() + gap.toPx()
    val total = count * step - gap.toPx()
    val startX = (size.width - total) / 2 + r
    val y = size.height - r - dot.toPx()
    repeat(count) { drawCircle(Ink, radius = r, center = Offset(startX + it * step, y)) }
}

/** On a filled tile, adds a white inner frame once the goal tile is reached. */
private fun Modifier.goalFrame(value: Int, inset: Dp, stroke: Dp, shape: Shape): Modifier =
    if (value >= GOAL_TILE) padding(inset).border(stroke, Paper, shape) else this
