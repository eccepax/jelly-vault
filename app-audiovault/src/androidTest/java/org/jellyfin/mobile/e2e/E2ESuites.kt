package org.jellyfin.mobile.e2e

// Tags for running part of the e2e suite (scripts/run-e2e-tests.sh, E2E_SUITE=...). Every e2e test
// class carries exactly one area; a few critical-path tests are also tagged Smoke. The runner's
// `annotation` filter reads both class and method annotations. Named as TuneVault's are, so the
// script's suite names mean the same in both apps.

/** A quick pass over the critical paths: sign in, browse and play, search, downloads. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Smoke

/** The library: browsing, search, sort and hiding items. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BrowseArea

/** Playing a book: the player's controls, speed, the sleep timer and playing in the background. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PlaybackArea

/** Signing in and out, and Settings. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AccountArea

/** Downloads: the slowest area. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DownloadsArea
