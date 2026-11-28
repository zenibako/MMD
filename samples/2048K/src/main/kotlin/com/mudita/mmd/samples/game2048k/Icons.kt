package com.mudita.mmd.samples.game2048k

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Path data from Material Symbols (Apache License 2.0), inlined so the sample does not need
// the material-icons artifacts.

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Ink))
        .build()

val UndoIcon: ImageVector = icon(
    "Undo",
    "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
        "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
)

val MoreIcon: ImageVector = icon(
    "More",
    "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 " +
        "2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
)

/** Back arrow drawn like the stock one: 2 dp strokes, round caps. */
val BackIcon: ImageVector = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Ink), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(20f, 12f); lineTo(4f, 12f); moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f)
    }
}.build()
