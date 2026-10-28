package org.jellyfin.mobile.cast

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import org.jellyfin.mobile.player.session.playableUri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Items as the Default Media Receiver takes them, and back. Each goes the way Media3's own converter
 * sends it, which also brings it back with its id, uri and metadata: the app's item, its own uri
 * and all, while the cast device fetches the stream the app chose. The queue item's custom data,
 * which the cast device sends back, holds the rest: the entry's key, the stream it was sent (where
 * a transcode starts), and what the converter leaves out of the item (its length, its extras and
 * its request), so a queue taken up from the cast device is the one that was sent. A live item goes
 * as a live stream, so the cast device shows no seek bar or duration.
 */
@UnstableApi
internal object CastQueueItems {

    /** Where the entry's key goes in a queue item's custom data, and the rest beside it. */
    private const val KEY = "vaultEntry"
    private const val STREAM = "stream"
    private const val DURATION = "durationMs"
    private const val EXTRAS = "extras"
    private const val REQUEST_URI = "requestUri"
    private const val REQUEST_EXTRAS = "requestExtras"

    private val converter = DefaultMediaItemConverter()

    fun queueItemOf(sent: SentItem, live: Boolean = false): MediaQueueItem {
        // Media3's converter needs a uri and a type; an item with no uri at all goes with the stream's
        val asGiven = sent.item.buildUpon()
            .setUri(sent.item.playableUri?.toString() ?: sent.stream.url)
            .setMimeType(sent.stream.mimeType)
            .build()
        val converted = converter.toMediaQueueItem(asGiven)
        converted.media?.writer?.apply {
            setContentId(sent.stream.url)
            setContentUrl(sent.stream.url)
            setContentType(sent.stream.mimeType)
            if (live) setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        }
        return MediaQueueItem.Builder(converted)
            .setCustomData(customDataOf(sent))
            .setAutoplay(true)
            .build()
    }

    private fun customDataOf(sent: SentItem): JSONObject {
        val item = sent.item
        return JSONObject()
            .put(KEY, sent.key)
            .put(
                STREAM,
                JSONObject()
                    .put("url", sent.stream.url)
                    .put("mimeType", sent.stream.mimeType)
                    .put("startMs", sent.stream.startMs)
                    .put("seekable", sent.stream.seekable),
            )
            .putOpt(DURATION, item.mediaMetadata.durationMs)
            .putOpt(EXTRAS, item.mediaMetadata.extras?.let(BundleJson::of))
            .putOpt(REQUEST_URI, item.requestMetadata.mediaUri?.toString())
            .putOpt(REQUEST_EXTRAS, item.requestMetadata.extras?.let(BundleJson::of))
    }

    /** The key of the entry it was sent for; null for an item another app sent. */
    fun keyOf(item: MediaQueueItem): String? = item.customData?.optString(KEY)?.takeIf { it.isNotEmpty() }

    /**
     * The entry it was sent for, as it was sent: the app's item and its stream. Null for one another
     * app sent, or that didn't come from Media3's converter.
     */
    // Another app's item can hold anything: none of it is this app's then
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun sentItemOf(queueItem: MediaQueueItem): SentItem? = try {
        val key = keyOf(queueItem)
        val data = queueItem.customData
        if (key == null || data == null) {
            null
        } else {
            SentItem(key, restored(converter.toMediaItem(queueItem), data), streamOf(queueItem, data))
        }
    } catch (e: RuntimeException) {
        null
    }

    private fun restored(item: MediaItem, data: JSONObject): MediaItem {
        val metadata = item.mediaMetadata.buildUpon()
            .setDurationMs(data.optLong(DURATION, -1L).takeIf { it >= 0 })
            .setExtras(data.optJSONObject(EXTRAS)?.let(BundleJson::bundleOf))
            .build()
        val request = MediaItem.RequestMetadata.Builder()
            .setMediaUri(data.optString(REQUEST_URI).takeIf { it.isNotEmpty() }?.toUri())
            .setExtras(data.optJSONObject(REQUEST_EXTRAS)?.let(BundleJson::bundleOf))
            .build()
        return item.buildUpon().setMediaMetadata(metadata).setRequestMetadata(request).build()
    }

    /** The stream it was sent; what the cast device was told to fetch, where an item doesn't say more. */
    private fun streamOf(queueItem: MediaQueueItem, data: JSONObject): CastStream {
        val stream = data.optJSONObject(STREAM)
        val media = queueItem.media
        return CastStream(
            url = stream?.optString("url")?.takeIf { it.isNotEmpty() } ?: media?.contentUrl ?: media?.contentId.orEmpty(),
            mimeType = stream?.optString("mimeType")?.takeIf { it.isNotEmpty() } ?: media?.contentType.orEmpty(),
            startMs = stream?.optLong("startMs") ?: 0L,
            seekable = stream?.optBoolean("seekable", true) ?: true,
        )
    }
}

/**
 * A Bundle as JSON and back, for what an app keeps in an item's extras: strings, numbers, booleans
 * and arrays of them, each with its type, so it comes back as it went. Anything else is left out.
 */
internal object BundleJson {
    private const val TYPE = "t"
    private const val VALUE = "v"

    fun of(bundle: Bundle): JSONObject {
        val json = JSONObject()
        for (name in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val typed = typedOf(bundle.get(name)) ?: continue
            json.put(name, JSONObject().put(TYPE, typed.first).put(VALUE, typed.second))
        }
        return json
    }

    private fun typedOf(value: Any?): Pair<String, Any>? = when (value) {
        is String -> "s" to value
        is Boolean -> "b" to value
        is Int -> "i" to value
        is Long -> "l" to value
        is Float -> "f" to value.toDouble()
        is Double -> "d" to value
        is IntArray -> "ia" to JSONArray(value.toList())
        is LongArray -> "la" to JSONArray(value.toList())
        is BooleanArray -> "ba" to JSONArray(value.toList())
        is Array<*> -> if (value.all { it is String }) "sa" to JSONArray(value.toList()) else null
        else -> null
    }

    fun bundleOf(json: JSONObject): Bundle {
        val bundle = Bundle()
        for (name in json.keys()) {
            val typed = json.optJSONObject(name) ?: continue
            put(bundle, name, typed.optString(TYPE), typed.opt(VALUE))
        }
        return bundle
    }

    // One branch per type a Bundle holds
    @Suppress("CyclomaticComplexMethod")
    private fun put(bundle: Bundle, name: String, type: String, value: Any?) {
        val number = value as? Number
        val array = value as? JSONArray
        when (type) {
            "s" -> bundle.putString(name, value as? String)
            "b" -> bundle.putBoolean(name, value == true)
            "i" -> bundle.putInt(name, number?.toInt() ?: 0)
            "l" -> bundle.putLong(name, number?.toLong() ?: 0L)
            "f" -> bundle.putFloat(name, number?.toFloat() ?: 0f)
            "d" -> bundle.putDouble(name, number?.toDouble() ?: 0.0)
            "ia" -> array?.let { bundle.putIntArray(name, IntArray(it.length(), it::getInt)) }
            "la" -> array?.let { bundle.putLongArray(name, LongArray(it.length(), it::getLong)) }
            "ba" -> array?.let { bundle.putBooleanArray(name, BooleanArray(it.length(), it::getBoolean)) }
            "sa" -> array?.let { bundle.putStringArray(name, Array(it.length(), it::getString)) }
        }
    }
}
