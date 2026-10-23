package org.jellyfin.mobile.common.server

import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

/** Where the server keeps the user's place in each item. */
interface ServerPositions {
    /** When the user last played [itemId] anywhere, per the server; null if never. */
    suspend fun lastPlayed(itemId: UUID): Instant?

    /** Records the user at [position] in [itemId], or done with it if [finished], as of [at]. */
    suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant)
}
