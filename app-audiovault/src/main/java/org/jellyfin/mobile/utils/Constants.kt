package org.jellyfin.mobile.utils

import android.app.PendingIntent
import android.media.session.PlaybackState

@Suppress("MagicNumber")
object Constants {
    // App Info
    const val APP_INFO_NAME = "AudioVault"

    const val FRAGMENT_CONNECT_EXTRA_ERROR = "org.jellyfin.mobile.intent.extra.ERROR"

    // Preference keys
    const val PREF_SERVER_ID = "pref_server_id"
    const val PREF_USER_ID = "pref_user_id"
    const val PREF_INSTANCE_URL = "pref_instance_url"
    const val PREF_IGNORE_BLUETOOTH_PERMISSION = "pref_ignore_bluetooth_permission"
    const val PREF_DOWNLOAD_METHOD = "pref_download_method"
    const val PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP = "pref_exoplayer_allow_press_speed_up"
    const val PREF_MEDIA_SEGMENT_ACTIONS = "pref_media_segment_actions"
    const val PREF_LIBRARY_SORT_OPTION = "pref_library_sort_option"
    const val PREF_LIBRARY_GLOBAL_FLATTEN = "pref_library_global_flatten"
    const val PREF_CHROMECAST_ENABLED = "pref_chromecast_enabled"
    const val PREF_PLAYBACK_SPEED = "pref_playback_speed"
    const val PREF_SKIP_SILENCE = "pref_skip_silence"

    // Notification
    const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    const val MEDIA_NOTIFICATION_CHANNEL_ID = "org.jellyfin.mobile.media.NOW_PLAYING"

    // Audiobook player constants
    const val SUPPORTED_AUDIOBOOK_PLAYBACK_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_STOP or
        PlaybackState.ACTION_SEEK_TO

    // Notification action intents
    const val ACTION_PLAY = "org.jellyfin.mobile.intent.action.PLAY"
    const val ACTION_PAUSE = "org.jellyfin.mobile.intent.action.PAUSE"
    const val ACTION_REWIND = "org.jellyfin.mobile.intent.action.REWIND"
    const val ACTION_FAST_FORWARD = "org.jellyfin.mobile.intent.action.FAST_FORWARD"
    const val ACTION_PREVIOUS = "org.jellyfin.mobile.intent.action.PREVIOUS"
    const val ACTION_NEXT = "org.jellyfin.mobile.intent.action.NEXT"
    const val ACTION_STOP = "org.jellyfin.mobile.intent.action.STOP"

    // Video player constants
    const val LANGUAGE_UNDEFINED = "und"
    const val PLAYER_TIME_UPDATE_RATE = 10000L
    const val SKIP_MEDIA_SEGMENT_UPDATE_DELAY = 1000L

    // Audiobook-specific skip intervals
    const val AUDIOBOOK_SKIP_BACK_MS = 10000L // 10 seconds
    const val AUDIOBOOK_SKIP_FORWARD_MS = 30000L // 30 seconds
    const val MAX_SKIP_TO_PREV_MS = 3000L
    const val HOLD_SPEEDUP_MULTIPLIER = 3f
    const val PLAYER_NOTIFICATION_ID = 99
    const val MAX_SKIP_TO_PREV_CHAPTER_MS = 10_000L

    // Video player intent extras
    const val EXTRA_MEDIA_PLAY_OPTIONS = "org.jellyfin.mobile.MEDIA_PLAY_OPTIONS"

    // Misc
    const val PERCENT_MAX = 100
    const val DOWNLOAD_THUMBNAIL_FILENAME = "thumbnail.jpg"
}
