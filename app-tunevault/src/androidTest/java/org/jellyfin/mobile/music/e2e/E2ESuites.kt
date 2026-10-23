package org.jellyfin.mobile.music.e2e

// Tags for running part of the e2e suite (scripts/run-e2e-tests.sh, E2E_SUITE=...). Every e2e test
// class carries exactly one area (checked as it runs, by LoggedInE2ETest's SuiteAreaRule), and a few
// critical-path tests are also tagged Smoke: single tests, never a whole class, so a slow test added
// to a class later can't join the quick pass unnoticed. The runner's `annotation` filter reads both
// class and method annotations.

/** Every area: each e2e test class carries exactly one. */
val SUITE_AREAS = setOf(
    BrowseArea::class.java,
    PlaybackArea::class.java,
    CollectionsArea::class.java,
    AccountArea::class.java,
    DownloadsArea::class.java,
)

/** A quick pass over the critical paths: sign in, browse, play, search, offline. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
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
