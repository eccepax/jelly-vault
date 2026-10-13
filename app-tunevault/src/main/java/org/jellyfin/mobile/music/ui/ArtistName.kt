package org.jellyfin.mobile.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.music.R

/** [artist], or "Unknown artist" when the file's tags didn't name one. */
@Composable
fun artistOrUnknown(artist: String): String = artist.ifBlank { stringResource(R.string.unknown_artist) }
