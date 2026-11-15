package org.jellyfin.mobile.player.interaction

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlin.time.Duration

@Parcelize
data class PlayOptions(
    val ids: List<UUID>,
    val mediaSourceId: String?,
    val startIndex: Int,
    val startPosition: Duration?,
    val playFromDownloads: Boolean?,
    val trackNames: List<String> = emptyList(),
) : Parcelable
