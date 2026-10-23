package org.jellyfin.mobile.testing

/**
 * The media on the test server (test-server/fixtures.json), named once for every contract and e2e
 * test. Tests reach the server's items only through these, so a fixture renamed in fixtures.json
 * fails VaultFixturesContractTest once instead of dozens of tests in confusing ways.
 */
object VaultFixtures {

    /** A 12-track FLAC album in a fixed order; most music tests play from it. */
    object PatternSuite {
        const val TITLE = "Test Pattern Suite"
        const val ARTIST = "Avery \"Tone\" Adams"

        /** Part of the title, in a different case, for search. */
        const val SEARCH_TERM = "pattern"
        const val TRACK_COUNT = 12

        const val FIRST_TRACK = "First Light (Rolling In)"

        /** The album's "known song": unique, searchable by its full title, and minutes long. */
        const val KNOWN_SONG = "Harbor Bell"

        /** The three songs after [KNOWN_SONG], in order; their names hold an apostrophe and a comma. */
        const val AFTER_KNOWN_SONG = "The Night Dog's Lullaby"
        const val SECOND_AFTER_KNOWN_SONG = "Salem, Oregon"
        const val THIRD_AFTER_KNOWN_SONG = "The River's Edge"
    }

    /** An artist with one album, searchable by part of their name. */
    object SignalPath {
        const val TITLE = "Signal Path"
        const val ARTIST = "Kestrel Lane"
        const val ARTIST_SEARCH_TERM = "kestrel"
    }

    /** An mp3 album of 5 tracks, for playlist tests. */
    const val PLAYLIST_ALBUM = "Square Waves"

    /** An album whose favourite flag tests set and then restore. */
    const val FAVORITES_ALBUM = "Favourite Frequencies"

    /** An album whose play state tests change and then restore. */
    const val PLAY_STATE_ALBUM = "Offline Tones"

    /**
     * One big track: six minutes of white noise, which FLAC can't shrink, so its download lasts
     * long enough to pause part way.
     */
    object LongForm {
        const val TITLE = "Long Form"
        const val TRACK = "The Longest Drone"

        /** Its file is about 45 MB; well under that and the pause test can't catch it mid-download. */
        const val MIN_FILE_BYTES = 30_000_000L
    }

    /** More music tracks than this exist, so "shuffle all" queues more than one album. */
    const val MIN_MUSIC_TRACKS = 100

    /** Audiobooks, filed as AudioBook items in a "books" library. */
    object Audiobooks {
        /**
         * One book in several small mp3 files, sharing an album tag. It is the first by title, so
         * playing the first book has another track to skip to: the server reports no chapters for
         * audio files, so that is all "next chapter" can do there.
         */
        const val MULTI_PART = "A Book in Parts: Lighthouse Keepers"
        const val MULTI_PART_SEARCH_TERM = "Lighthouse"

        /** A single-file m4b, and a term only it matches. Its chapters are in the file; the server doesn't report them. */
        const val M4B = "The Chaptered Audiobook"
        const val M4B_SEARCH_TERM = "Chaptered"

        /** A single-file mp3, and a term only it matches. */
        const val MP3 = "The Single File Story"
        const val MP3_SEARCH_TERM = "Single File"

        /** A word that matches an audiobook, for "never mixes in audiobooks" guards. */
        const val SEARCH_TERM_MATCHING_AN_AUDIOBOOK = "History"
        const val TITLE_MATCHING_SEARCH_TERM = "A Short History of Test Tones"
    }

    /** E-books. The first by title is a PDF. */
    object Books {
        const val FIRST_BY_TITLE_PDF = "Aardvark Atlas (Test PDF)"
        const val FIRST_BY_TITLE_PDF_PAGES = 3

        /** An EPUB with a table of contents and many chapters; [SEARCH_TERM] matches it alone. */
        const val EPUB = "Pride and Prejudice"

        /** Another EPUB, to read while [EPUB] isn't. */
        const val OTHER_EPUB = "The Time Machine"

        /** Matches one book ([EPUB]) and not the others. */
        const val SEARCH_TERM = "Pride"
    }

    /** Live TV, from an M3U tuner with an XMLTV guide. */
    object LiveTv {
        /** The tag on free channels, whose names start with [FREE_PREFIX]: the only ones tests may open. */
        const val FREE_TAG = "Free"
        const val FREE_PREFIX = "FREE|"

        /** A channel that isn't free, with a name only it holds. */
        const val NON_FREE_CHANNEL = "Sports Test Channel"
        const val NON_FREE_SEARCH_TERM = "Sports"
    }
}
