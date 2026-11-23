package com.mudita.mmd.samples.hermes

import java.io.BufferedReader

data class SseEvent(val event: String, val data: String)

/**
 * Lazily parses a Server-Sent Events stream. Comment lines (`: keepalive`) are skipped,
 * multi-line `data:` fields are joined with newlines, and an event is emitted on each
 * blank line.
 */
fun BufferedReader.sseEvents(): Sequence<SseEvent> = sequence {
    var event = ""
    val data = StringBuilder()
    while (true) {
        val line = readLine() ?: break
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty() || event.isNotEmpty()) {
                    yield(SseEvent(event, data.toString()))
                }
                event = ""
                data.setLength(0)
            }
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
    }
    if (data.isNotEmpty()) yield(SseEvent(event, data.toString()))
}
