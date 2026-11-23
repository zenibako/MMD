package com.mudita.mmd.samples.hermes

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    data object Inbox : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        Inbox.ensureLoaded(this)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        NtfyListenerService.start(this)

        val start: Screen = when {
            !SettingsStore.load(this).isHermesConfigured -> Screen.Settings
            intent.getBooleanExtra(EXTRA_OPEN_INBOX, false) -> Screen.Inbox
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

    companion object {
        const val EXTRA_OPEN_INBOX = "open_inbox"
    }
}

@Composable
private fun App(start: Screen) {
    var screen by remember { mutableStateOf(start) }
    BackHandler(enabled = screen != Screen.Sessions) { screen = Screen.Sessions }
    when (val s = screen) {
        Screen.Sessions -> SessionsScreen(
            onOpen = { id, title, profile, source -> screen = Screen.Chat(id, title, profile, source) },
            onInbox = { screen = Screen.Inbox },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Chat -> ChatScreen(
            sessionId = s.sessionId,
            title = s.title,
            profileSlug = s.profileSlug,
            source = s.source,
            onOpenSession = { id, title -> screen = Screen.Chat(id, title, s.profileSlug) },
            onBack = { screen = Screen.Sessions },
        )
        Screen.Inbox -> InboxScreen(onBack = { screen = Screen.Sessions })
        Screen.Settings -> SettingsScreen(onDone = { screen = Screen.Sessions })
    }
}
