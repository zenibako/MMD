package com.mudita.mmd.samples.hermes

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mudita.mmd.R

// The Kompakt's stock type scale (kompakt-eink-ui skill): Lato Medium (500) for content and
// Lato Black (900) for titles, labels and buttons. MMD ships no Lato Medium, and Regular reads
// lighter than the stock apps on the panel, so 500 uses MMD's SemiBold.
private val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_semi_bold, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Lato, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = Color.Black)

val KompaktTypography = Typography(
    headlineLarge = style(36, 40, FontWeight.Black), // full-screen alert title
    titleLarge = style(25, 28, FontWeight.Black), // top bar, sheet title, empty-state headline
    titleMedium = style(23, 25, FontWeight.Black), // two-line row title
    titleSmall = style(21, 25, FontWeight.Black), // section header, list name
    bodyLarge = style(21, 25, FontWeight.Medium), // row label, summary, body
    bodyMedium = style(18, 22, FontWeight.Medium), // message text, times, previews
    bodySmall = style(15, 18, FontWeight.Medium), // densest captions only
    labelLarge = style(21, 23, FontWeight.Black), // button label
    labelMedium = style(18, 22, FontWeight.Black), // small labels
    labelSmall = style(15, 15, FontWeight.Medium),
)
