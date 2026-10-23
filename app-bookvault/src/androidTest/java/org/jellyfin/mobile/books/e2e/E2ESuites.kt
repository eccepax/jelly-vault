package org.jellyfin.mobile.books.e2e

// Tags for running part of the e2e suite (scripts/run-e2e-tests.sh, E2E_SUITE=...). Every e2e test
// class carries exactly one area (checked as it runs, by LoggedInE2ETest's SuiteAreaRule), and a few
// critical-path tests are also tagged Smoke: single tests, never a whole class, so a slow test added
// to a class later can't join the quick pass unnoticed. The runner's `annotation` filter reads both
// class and method annotations. Named as the other apps' are, so the script's suite names mean the
// same in each; reading is BookVault's own area, in place of their playback.

/** Every area: each e2e test class carries exactly one. */
val SUITE_AREAS = setOf(
    BrowseArea::class.java,
    ReadingArea::class.java,
    AccountArea::class.java,
    DownloadsArea::class.java,
)

/** A quick pass over the critical paths: sign in, search, open a book. Downloads are too slow. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Smoke

/** The library: browsing and search. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BrowseArea

/** Reading a book: the reader, its contents, bookmarks, text settings, zoom, and where a book opens. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ReadingArea

/** Signing in and out, and Settings. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AccountArea

/** Downloads: the slowest area. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DownloadsArea
