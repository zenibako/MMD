package com.mudita.mmd.samples.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mudita.mmd.ThemeMMD

sealed interface Screen {
    data object Sessions : Screen
    data class Chat(
        val sessionId: String,
        val title: String,
        val profileSlug: String,
        val source: String = "",
    ) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start: Screen = when {
            !SettingsStore.load(this).isHermesConfigured -> Screen.Settings
            else -> Screen.Sessions
        }
        setContent {
            ThemeMMD {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    App(start)
                }
            }
        }
    }
}

@Composable
private fun App(start: Screen) {
    var screen by remember { mutableStateOf(start) }
    BackHandler(enabled = screen != Screen.Sessions) { screen = Screen.Sessions }
    when (val s = screen) {
        Screen.Sessions -> SessionsScreen(
            onOpen = { id, title, profile, source -> screen = Screen.Chat(id, title, profile, source) },
            onSettings = { screen = Screen.Settings },
        )
        // Keyed by session, so a fork opened from a chat starts with fresh state.
        is Screen.Chat -> key(s.profileSlug, s.sessionId) {
            ChatScreen(
                sessionId = s.sessionId,
                title = s.title,
                profileSlug = s.profileSlug,
                source = s.source,
                onOpenSession = { id, title -> screen = Screen.Chat(id, title, s.profileSlug) },
                onBack = { screen = Screen.Sessions },
            )
        }
        Screen.Settings -> SettingsScreen(onDone = { screen = Screen.Sessions })
    }
}
