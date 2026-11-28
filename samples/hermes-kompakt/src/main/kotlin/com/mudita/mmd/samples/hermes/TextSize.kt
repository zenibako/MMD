package com.mudita.mmd.samples.hermes

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * The Settings "Text size" choice. It scales message text (bubbles, the "Used …" lines and
 * failure lines); bars, buttons and sheets keep the stock Kompakt sizes. Default is 18 sp.
 */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.85f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.17f),
    LARGEST("Largest", 1.33f),
    ;

    companion object {
        fun fromKey(key: String?): TextSize = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

/** The current choice, read by the chat; loaded at start and updated from Settings. */
object MessageTextSize {
    var current by mutableStateOf(TextSize.DEFAULT)

    private const val KEY = "textSize"

    fun load(context: Context) {
        current = TextSize.fromKey(context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun set(context: Context, size: TextSize) {
        current = size
        context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE).edit().putString(KEY, size.name).apply()
    }
}

private fun TextUnit.times(f: Float): TextUnit = if (isSpecified) this * f else this

/** [this] style with its size and line height times [factor] (unspecified ones left alone). */
fun TextStyle.scaledBy(factor: Float): TextStyle =
    if (factor == 1f) this else copy(fontSize = fontSize.times(factor), lineHeight = lineHeight.times(factor))

/** [this] style scaled for message text by the Settings choice. */
fun TextStyle.scaledForMessages(size: TextSize = MessageTextSize.current): TextStyle = scaledBy(size.scale)
