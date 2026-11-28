package com.mudita.mmd.samples.hermes

import java.io.BufferedReader
import java.io.IOException

data class SseEvent(val event: String, val data: String)

/** Caps so a faulty server can't exhaust the phone's memory with one endless line or event. */
private const val MAX_LINE_CHARS = 256 * 1024
private const val MAX_EVENT_CHARS = 1024 * 1024

/**
 * Lazily parses a Server-Sent Events stream. Comment lines (`: keepalive`) are skipped,
 * multi-line `data:` fields are joined with newlines, and an event is emitted on each
 * blank line.
 */
fun BufferedReader.sseEvents(): Sequence<SseEvent> = sequence {
    var event = ""
    val data = StringBuilder()
    val lines = BoundedLineReader(this@sseEvents, MAX_LINE_CHARS)
    while (true) {
        val line = lines.next() ?: break
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
                if (data.length > MAX_EVENT_CHARS) throw IOException("SSE event over $MAX_EVENT_CHARS chars")
            }
        }
    }
    // An event without its closing blank line is incomplete (the connection dropped
    // mid-frame), so it is dropped rather than dispatched.
}

/**
 * Like [BufferedReader.readLine] (lines end at `\n`, `\r` or `\r\n`), but fails once a line
 * passes [max] chars instead of buffering an unterminated line without bound. A line ends as
 * soon as `\r` arrives; a `\n` right after it is skipped on the next read, so a CR-only server
 * isn't kept waiting for one more byte.
 */
private class BoundedLineReader(private val reader: BufferedReader, private val max: Int) {
    private var skipLf = false

    fun next(): String? {
        val line = StringBuilder()
        while (true) {
            val c = reader.read()
            if (skipLf) {
                skipLf = false
                if (c == '\n'.code) continue
            }
            when {
                c == -1 -> return if (line.isEmpty()) null else line.toString()
                c == '\n'.code -> return line.toString()
                c == '\r'.code -> {
                    skipLf = true
                    return line.toString()
                }
                line.length >= max -> throw IOException("SSE line over $max chars")
                else -> line.append(c.toChar())
            }
        }
    }
}
