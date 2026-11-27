package com.mudita.mmd.samples.twofold

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.samples.twofold.game.GameCodec
import com.mudita.mmd.samples.twofold.game.GameState

private const val PREFS = "twofold"
private const val KEY_GAME = "game"
/** The older single tile-style setting, read once to carry a saved choice over. */
private const val KEY_LEGACY_STYLE = "tileStyle"
private const val KEY_MARKS = "tileMarks"
private const val KEY_ROUNDED = "tileRounded"
private const val KEY_DARK = "tileDark"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = GameCodec.decode(prefs.getString(KEY_GAME, null))

        setContent {
            ThemeMMD(typography = KompaktTypography) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(saved ?: GameState.new()) }
                    // Save after every move so the game survives the app being killed.
                    LaunchedEffect(state) {
                        prefs.edit().putString(KEY_GAME, GameCodec.encode(state)).apply()
                    }
                    var look by remember {
                        mutableStateOf(
                            TileLook.load(
                                marks = prefs.getString(KEY_MARKS, null),
                                rounded = if (prefs.contains(KEY_ROUNDED)) prefs.getBoolean(KEY_ROUNDED, false) else null,
                                dark = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null,
                                legacyStyle = prefs.getString(KEY_LEGACY_STYLE, null),
                            ),
                        )
                    }
                    GameScreen(
                        state = state,
                        onUpdate = { change -> state = change(state) },
                        tileLook = look,
                        onTileLookChange = {
                            look = it
                            prefs.edit()
                                .putString(KEY_MARKS, it.marks.name)
                                .putBoolean(KEY_ROUNDED, it.rounded)
                                .putBoolean(KEY_DARK, it.dark)
                                .remove(KEY_LEGACY_STYLE)
                                .apply()
                        },
                    )
                }
            }
        }
    }
}
