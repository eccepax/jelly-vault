package org.jellyfin.mobile.livetv.playback

import android.content.Context
import androidx.core.content.edit

/**
 * [StreamLedger] in SharedPreferences, so it survives the app being killed mid-stream. Written
 * with commit(): a stream recorded just before a crash must still be there on the next launch.
 */
class PreferencesStreamLedger(context: Context) : StreamLedger {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    override fun add(ticket: StreamTicket) = update { it + ticket.encode() }

    @Synchronized
    override fun remove(ticket: StreamTicket) = update { it - ticket.encode() }

    @Synchronized
    override fun all(): List<StreamTicket> = entries().mapNotNull(::decode)

    private fun entries(): Set<String> = preferences.getStringSet(KEY, null).orEmpty()

    private fun update(change: (Set<String>) -> Set<String>) {
        // A copy: the set getStringSet returns must not be modified.
        preferences.edit(commit = true) { putStringSet(KEY, change(entries().toSet())) }
    }

    private fun StreamTicket.encode() = listOf(
        channelId,
        liveStreamId,
        playSessionId.orEmpty(),
        mediaSourceId,
    ).joinToString(SEPARATOR)

    private fun decode(entry: String): StreamTicket? {
        val parts = entry.split(SEPARATOR)
        if (parts.size != FIELDS) return null
        return StreamTicket(
            channelId = parts[0],
            liveStreamId = parts[1],
            playSessionId = parts[2].ifEmpty { null },
            mediaSourceId = parts[3],
        )
    }

    private companion object {
        const val FILE = "live_streams"
        const val KEY = "open"
        const val SEPARATOR = "\t"
        const val FIELDS = 4
    }
}
