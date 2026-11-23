package com.mudita.mmd.samples.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * Full-screen, alarm-style card for urgent agent messages. Static layout, black on white,
 * no animation: one E Ink refresh to show it, one to dismiss it.
 */
class UrgentAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "From your agent" }
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        setContent {
            ThemeMMD {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextMMD("URGENT", style = MaterialTheme.typography.labelLarge)
                        TextMMD(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextMMD(
                            message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        )
                        ButtonMMD(onClick = ::openApp, modifier = Modifier.fillMaxWidth()) { TextMMD("Open Hermes") }
                        OutlinedButtonMMD(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { TextMMD("Dismiss") }
                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }

    private fun openApp() {
        startActivity(android.content.Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_INBOX, true))
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }
}
