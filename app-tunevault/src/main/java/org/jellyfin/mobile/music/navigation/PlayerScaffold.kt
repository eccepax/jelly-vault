package org.jellyfin.mobile.music.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The signed-in shell: [library] above the mini-player while something is loaded, with Now Playing
 * expanded over everything. Whether it's expanded is saved, so it survives rotation.
 */
@Composable
fun PlayerScaffold(
    hasTrack: Boolean,
    library: @Composable () -> Unit,
    miniPlayer: @Composable (onExpand: () -> Unit) -> Unit,
    nowPlaying: @Composable (onCollapse: () -> Unit) -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasTrack) {
        if (!hasTrack) expanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val showNowPlaying = expanded && hasTrack
        // Hidden from accessibility services while covered, so TalkBack doesn't read what's underneath.
        val coveredModifier = if (showNowPlaying) Modifier.clearAndSetSemantics {} else Modifier
        Column(modifier = Modifier.fillMaxSize().then(coveredModifier)) {
            // Whatever sits at the bottom (the bar, else the mini-player) owns the navigation-bar
            // inset, so the content above doesn't pad for it too.
            val bottomOwnsInset = hasTrack || bottomBar != null
            val navigationInset = WindowInsets.navigationBars
            val libraryModifier = Modifier.weight(1f).then(
                if (bottomOwnsInset) Modifier.consumeWindowInsets(navigationInset) else Modifier,
            )
            Box(modifier = libraryModifier) { library() }
            if (hasTrack) {
                val miniModifier = if (bottomBar == null) Modifier.navigationBarsPadding() else Modifier
                Box(modifier = miniModifier) { miniPlayer { expanded = true } }
            }
            bottomBar?.invoke()
        }
        if (showNowPlaying) {
            // Registered only while open, so it's always the newest back callback and wins over any
            // the library registered earlier (e.g. an open album's) underneath.
            BackHandler { expanded = false }
            nowPlaying { expanded = false }
        }
    }
}
