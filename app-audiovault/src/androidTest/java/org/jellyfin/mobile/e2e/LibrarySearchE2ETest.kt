package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * `LibraryViewModel.setFilterQuery` filters the already-loaded list client-side (see
 * `LibraryItemFilterTest` in the unit suite) — no extra network round trip once the library has
 * loaded. Searches for a title known to exist on the real Vault library ("Caste", Isabel
 * Wilkerson's "Caste: The Origins of Our Discontents") rather than a generic term, so the
 * assertion that the list actually narrowed isn't just luck.
 */
class LibrarySearchE2ETest : LoggedInE2ETest() {

    @Test
    fun searchingFiltersTheLibraryList() {
        loginAndWaitForLibrary()
        val firstTitle = libraryTitles().first()

        searchLibrary("Caste")

        assert(firstTitle !in libraryTitles()) { "Expected the search to narrow the list" }
    }
}
