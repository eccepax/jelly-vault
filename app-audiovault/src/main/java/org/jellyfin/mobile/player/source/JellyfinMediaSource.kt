package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

/**
 * A track as the server offers it to play: its source, how it plays ([playMethod]), and where
 * the listener left it ([startTime]).
 */
sealed class JellyfinMediaSource(
    val itemId: UUID,
    val item: BaseItemDto?,
    val sourceInfo: MediaSourceInfo,
    val playSessionId: String,
    startTime: Duration?,
) {
    val id: String = requireNotNull(sourceInfo.id) { "Media source has no id" }

    abstract val playMethod: PlayMethod

    var startTime: Duration = startTime ?: Duration.ZERO
    val runTime: Duration = sourceInfo.runTimeTicks?.ticks ?: Duration.ZERO

    /** The track's name, or the source's when the track's details didn't come with it. */
    val name: String get() = item?.name ?: sourceInfo.name.orEmpty()
}
