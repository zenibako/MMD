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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = GameCodec.decode(prefs.getString(KEY_GAME, null))

        setContent {
            ThemeMMD {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(saved ?: GameState.new()) }
                    // Save after every move so the game survives the app being killed.
                    LaunchedEffect(state) {
                        prefs.edit().putString(KEY_GAME, GameCodec.encode(state)).apply()
                    }
                    GameScreen(state = state, onStateChange = { state = it })
                }
            }
        }
    }
}
