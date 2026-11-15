package org.jellyfin.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import androidx.media3.session.R as Media3R
import org.jellyfin.mobile.common.R as CommonR

/**
 * The home-screen widget: the book playing (its title and part), back 10 seconds and play/pause, or
 * that nothing is; tapping it opens the app. The playback service keeps it up to date ([show]).
 */
class AudiobookWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { NowPlayingContent(nowPlayingFrom(currentState<Preferences>()), WidgetLabels.of(context)) }
        }
    }

    companion object {
        /** Shows [now] (or that nothing plays) on every AudioVault widget on the home screen. */
        suspend fun show(context: Context, now: NowPlaying?) {
            val widget = AudiobookWidget()
            GlanceAppWidgetManager(context).getGlanceIds(AudiobookWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                    state.toMutablePreferences().apply { keep(now) }
                }
                widget.update(context, id)
            }
        }
    }
}

class AudiobookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AudiobookWidget()
}

/** What the widget says, in the app's language. */
data class WidgetLabels(val nothingPlaying: String, val rewind: String, val play: String, val pause: String) {
    companion object {
        fun of(context: Context) = WidgetLabels(
            nothingPlaying = context.getString(R.string.widget_nothing_playing),
            rewind = context.getString(R.string.player_rewind),
            play = context.getString(CommonR.string.vault_play),
            pause = context.getString(CommonR.string.vault_pause),
        )
    }
}

/** The widget's content for [now]: the book with its buttons, or that nothing plays. */
@Composable
fun NowPlayingContent(now: NowPlaying?, labels: WidgetLabels) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = now?.title ?: labels.nothingPlaying,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            )
            now?.detail?.let {
                Text(text = it, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            }
        }
        if (now != null) {
            Image(
                provider = ImageProvider(Media3R.drawable.media3_icon_skip_back_10),
                contentDescription = labels.rewind,
                modifier = GlanceModifier.size(BUTTON_SIZE).padding(8.dp).clickable(actionRunCallback<RewindAction>()),
            )
            Image(
                provider = ImageProvider(
                    if (now.isPlaying) Media3R.drawable.media3_icon_pause else Media3R.drawable.media3_icon_play,
                ),
                contentDescription = if (now.isPlaying) labels.pause else labels.play,
                modifier = GlanceModifier.size(
                    BUTTON_SIZE,
                ).padding(8.dp).clickable(actionRunCallback<PlayPauseAction>()),
            )
        }
    }
}

private val BUTTON_SIZE = 48.dp
