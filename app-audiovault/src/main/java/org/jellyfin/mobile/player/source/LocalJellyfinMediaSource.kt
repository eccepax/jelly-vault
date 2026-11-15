package org.jellyfin.mobile.player.source

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

@Serializable(with = JellyfinMediaSourceSerializer::class)
class LocalJellyfinMediaSource(
    itemId: UUID,
    item: BaseItemDto?,
    sourceInfo: MediaSourceInfo,
    playSessionId: String,
    startTime: Duration? = null,
    val localDirectoryUri: String,
    val remoteFileUri: String,
    val downloadSize: Long,
) : JellyfinMediaSource(itemId, item, sourceInfo, playSessionId, startTime) {
    override val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY

    /**
     * When the listener's position ([startTime]) was saved on the device without the server
     * hearing about it, e.g. listening offline; null once the server has it.
     */
    var positionSavedAt: Instant? = null

    /** Whether that listening reached the track's end ([startTime] is then back at zero). */
    var finished: Boolean = false

    constructor(source: JellyfinMediaSource, downloadFolder: String, downloadUrl: String, downloadSize: Long) : this(
        source.itemId,
        source.item,
        source.sourceInfo,
        source.playSessionId,
        source.startTime,
        downloadFolder,
        downloadUrl,
        downloadSize,
    )
}
