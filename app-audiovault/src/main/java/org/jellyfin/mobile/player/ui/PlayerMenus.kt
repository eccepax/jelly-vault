package org.jellyfin.mobile.player.ui

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.playermenuhelper.PlayerMenuHelper
import org.jellyfin.mobile.player.ui.playermenuhelper.SkipMediaSegmentButton
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.core.component.KoinComponent
/**
 *  Provides a menu UI for audio, subtitle and video stream selection
 */
class PlayerMenus(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsBinding: ExoPlayerControlViewBinding,
) : KoinComponent {

    private val context = playerBinding.root.context

    private val previousChapterButton: View by playerControlsBinding::previousChapterButton
    private val nextChapterButton: View by playerControlsBinding::nextChapterButton
    private val toolbar: View by playerBinding::toolbar
    private val playbackInfo: TextView by playerBinding::playbackInfo
    private val speedMenu: PopupMenu = createSpeedMenu()
    private val chapterMarkingContainer: ConstraintLayout by playerControlsBinding::chapterMarkingContainer
    private val skipSegmentButton: Button by playerBinding::skipSegmentButton

    private val playerMenuHelper: PlayerMenuHelper = PlayerMenuHelper(
        skipMediaSegmentButton = SkipMediaSegmentButton(skipSegmentButton, fragment::onSkipMediaSegment),
    )

    init {
        playbackInfo.setOnClickListener {
            dismissPlaybackInfo()
        }

        fragment.setPlayerMenuHelper(playerMenuHelper)
    }

    fun showSpeedMenu(currentSpeed: Float) {
        for (i in 0 until speedMenu.menu.size()) {
            val item = speedMenu.menu.getItem(i)
            item.isChecked = kotlin.math.abs(item.itemId * SPEED_MENU_STEP_SIZE - currentSpeed) < SPEED_COMPARISON_EPSILON
        }
        speedMenu.show()
    }

    fun togglePlaybackInfo() {
        playbackInfo.isVisible = !playbackInfo.isVisible
    }

    fun onQueueItemChanged(mediaSource: JellyfinMediaSource, hasNext: Boolean, hasPrevious: Boolean) {
        val chapters = mediaSource.item?.chapters
        val hasEmbeddedChapters = !chapters.isNullOrEmpty()
        updateLayoutConstraints(hasEmbeddedChapters, hasPrevious, hasNext)
        val runTimeTicks = mediaSource.item?.runTimeTicks
        setChapterMarkings(chapters, runTimeTicks)

        val audioStreams = mediaSource.audioStreams

        val playMethod = context.getString(R.string.playback_info_play_method, mediaSource.playMethod)
        val audioTracksInfo = buildMediaStreamsInfo(
            mediaStreams = audioStreams,
            prefix = R.string.playback_info_audio_streams,
            maxStreams = MAX_AUDIO_STREAMS_DISPLAY,
            streamSuffix = { stream ->
                stream.language?.let { lang -> " ($lang)" }.orEmpty()
            },
        )

        playbackInfo.text = listOf(
            playMethod,
            audioTracksInfo,
        ).joinToString("\n\n")
    }

    private fun updateLayoutConstraints(hasChapters: Boolean, hasPrevious: Boolean, hasNext: Boolean) {
        // Show chapter/track navigation when there are embedded chapter marks OR multiple queue items
        previousChapterButton.isVisible = hasChapters || hasPrevious
        nextChapterButton.isVisible = hasChapters || hasNext
    }

    fun showTracklist(trackNames: List<String>, currentIndex: Int, onTrackSelected: (Int) -> Unit) {
        if (trackNames.isEmpty()) return
        val items = trackNames.mapIndexed { i, name ->
            if (i == currentIndex) "▶ $name" else "   $name"
        }.toTypedArray()
        android.app.AlertDialog.Builder(context)
            .setTitle(fragment.getString(R.string.player_tracklist))
            .setItems(items) { _, which -> onTrackSelected(which) }
            .show()
    }

    private fun setChapterMarkings(chapters: List<ChapterInfo>?, runTimeTicks: Long?) {
        chapterMarkingContainer.removeAllViews()

        if (chapters.isNullOrEmpty() || runTimeTicks == null || runTimeTicks <= 0) {
            playerMenuHelper.chapterMarkings.setMarkings(emptyList())
            return
        }

        val chapterMarkings = chapters.map { ch ->
            val percent = ch.startPositionTicks.toFloat() / runTimeTicks
            val bias = percent.coerceIn(0f, 1f)
            val marking = ChapterMarking(context, bias)
            chapterMarkingContainer.addView(marking.view) // Add view as side effect to avoid another loop
            marking
        }
        playerMenuHelper.chapterMarkings.setMarkings(chapterMarkings)
    }

    private fun buildMediaStreamsInfo(
        mediaStreams: List<MediaStream>,
        @StringRes prefix: Int,
        maxStreams: Int,
        streamSuffix: (MediaStream) -> String,
    ): String = mediaStreams.joinToString(
        "\n",
        "${fragment.getString(prefix)}:\n",
        limit = maxStreams,
        truncated = fragment.getString(R.string.playback_info_and_x_more, mediaStreams.size - maxStreams),
    ) { stream ->
        val title = stream.displayTitle?.takeUnless(String::isEmpty)
            ?: fragment.getString(R.string.playback_info_stream_unknown_title)
        val suffix = streamSuffix(stream)
        "- $title$suffix"
    }

    private fun createSpeedMenu() = PopupMenu(context, toolbar).apply {
        for (step in SPEED_MENU_STEP_MIN..SPEED_MENU_STEP_MAX) {
            val newSpeed = step * SPEED_MENU_STEP_SIZE
            menu.add(SPEED_MENU_GROUP, step, Menu.NONE, "${newSpeed}x").isChecked = newSpeed == 1f
        }
        menu.setGroupCheckable(SPEED_MENU_GROUP, true, true)
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            fragment.onSpeedSelected(clickedItem.itemId * SPEED_MENU_STEP_SIZE).also { success ->
                if (success) clickedItem.isChecked = true
            }
        }
    }

    fun dismissPlaybackInfo() {
        playbackInfo.isVisible = false
    }

    companion object {
        private const val SPEED_MENU_GROUP = 2

        private const val MAX_AUDIO_STREAMS_DISPLAY = 5

        private const val SPEED_MENU_STEP_SIZE = 0.25f
        private const val SPEED_MENU_STEP_MIN = 2 // → 0.5x
        private const val SPEED_MENU_STEP_MAX = 8 // → 2x
        private const val SPEED_COMPARISON_EPSILON = 0.01f
    }
}
