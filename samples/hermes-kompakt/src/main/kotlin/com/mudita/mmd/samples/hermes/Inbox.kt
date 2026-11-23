package com.mudita.mmd.samples.hermes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class InboxItem(
    val id: String,
    val time: Long,
    val title: String,
    val message: String,
    val priority: Int,
    val read: Boolean = false,
)

/**
 * Where non-urgent agent messages go instead of the (absent) notification tray.
 * The person reads them when they choose to open the app, which is the Kompakt way.
 */
object Inbox {
    private const val PREFS = "hermes_inbox"
    private const val MAX_ITEMS = 200

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = prefs(context).getString("items", "[]").orEmpty()
            _items.value = runCatching { JSONArray(raw).objects().map(::fromJson) }.getOrDefault(emptyList())
            loaded = true
        }
    }

    fun lastId(context: Context): String? = prefs(context).getString("lastId", null)

    fun add(context: Context, item: InboxItem) {
        ensureLoaded(context)
        synchronized(this) {
            if (_items.value.any { it.id == item.id }) return
            _items.value = (listOf(item) + _items.value).take(MAX_ITEMS)
            persist(context, lastId = item.id)
        }
    }

    fun markAllRead(context: Context) {
        ensureLoaded(context)
        synchronized(this) {
            _items.value = _items.value.map { it.copy(read = true) }
            persist(context)
        }
    }

    private fun persist(context: Context, lastId: String? = null) {
        val arr = JSONArray()
        _items.value.forEach { arr.put(toJson(it)) }
        prefs(context).edit().apply {
            putString("items", arr.toString())
            if (lastId != null) putString("lastId", lastId)
        }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(i: InboxItem) = JSONObject()
        .put("id", i.id).put("time", i.time).put("title", i.title)
        .put("message", i.message).put("priority", i.priority).put("read", i.read)

    private fun fromJson(o: JSONObject) = InboxItem(
        id = o.optString("id"),
        time = o.optLong("time"),
        title = o.optString("title"),
        message = o.optString("message"),
        priority = o.optInt("priority", 3),
        read = o.optBoolean("read"),
    )
}
