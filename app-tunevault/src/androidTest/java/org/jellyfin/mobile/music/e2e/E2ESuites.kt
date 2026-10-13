package org.jellyfin.mobile.music.e2e

// Tags for running part of the e2e suite (scripts/run-e2e-tests.sh, E2E_SUITE=...). Every e2e test
// class carries exactly one area; a few critical-path tests are also tagged Smoke. The runner's
// `annotation` filter reads both class and method annotations.

/** A quick pass over the critical paths: sign in, browse, play, search, offline. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Smoke

/** Albums, artists, songs, Home and search. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BrowseArea

/** Playing, Now Playing, the queue and play reporting. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PlaybackArea

/** Favorites and playlists: tests that write to the server. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CollectionsArea

/** Signing in and out, and Settings. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AccountArea

/** Downloads and offline mode: the slowest area. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DownloadsArea
