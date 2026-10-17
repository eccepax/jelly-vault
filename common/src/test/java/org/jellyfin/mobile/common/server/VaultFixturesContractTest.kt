package org.jellyfin.mobile.common.server

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultFixtures
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.VaultFixtures.SignalPath
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The test server holds every fixture the other contract and e2e tests name (VaultFixtures,
 * from test-server/fixtures.json). A stale or half-seeded server fails here, once and plainly,
 * instead of failing dozens of tests in confusing ways. Re-run test-server/up.sh to fix it.
 */
class VaultFixturesContractTest {

    private val api get() = VaultTestServer.apiClient

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
    }

    private suspend fun items(vararg kinds: BaseItemKind, searchTerm: String? = null): List<BaseItemDto> =
        api.itemsApi.getItems(
            includeItemTypes = kinds.toList(),
            recursive = true,
            searchTerm = searchTerm,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.PATH),
        ).content.items

    @Test
    fun `reports which Jellyfin version the tests ran against`() = runBlocking<Unit> {
        val info = api.systemApi.getPublicSystemInfo().content
        println("Test server: Jellyfin ${info.version} at ${VaultTestServer.hostname}")

        info.version.shouldNotBeNull().shouldNotBeBlank()
    }

    @Test
    fun `the music fixtures are on the server`() = runBlocking<Unit> {
        val albums = items(BaseItemKind.MUSIC_ALBUM).associateBy { it.name }
        val suite = albums[PatternSuite.TITLE]
        val songs = suite?.let { album ->
            api.itemsApi.getItems(
                parentId = album.id,
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
            ).content.items.map { it.name }
        }

        assertSoftly {
            albums.keys shouldContainAll listOf(
                PatternSuite.TITLE, SignalPath.TITLE,
                VaultFixtures.PLAYLIST_ALBUM, VaultFixtures.FAVORITES_ALBUM, VaultFixtures.PLAY_STATE_ALBUM,
            )
            withClue(PatternSuite.TITLE) {
                suite?.albumArtist shouldBe PatternSuite.ARTIST
                songs.orEmpty() shouldHaveSize PatternSuite.TRACK_COUNT
                songs?.firstOrNull() shouldBe PatternSuite.FIRST_TRACK
                songs?.dropWhile { it != PatternSuite.KNOWN_SONG }?.take(4) shouldBe listOf(
                    PatternSuite.KNOWN_SONG, PatternSuite.AFTER_KNOWN_SONG,
                    PatternSuite.SECOND_AFTER_KNOWN_SONG, PatternSuite.THIRD_AFTER_KNOWN_SONG,
                )
            }
            albums[SignalPath.TITLE]?.albumArtist shouldBe SignalPath.ARTIST
            items(BaseItemKind.AUDIO).size shouldBeGreaterThan VaultFixtures.MIN_MUSIC_TRACKS
            items(BaseItemKind.PLAYLIST).map { it.path.orEmpty().lowercase() }.filter { it.endsWith(".m3u") }
                .shouldNotBeEmpty()
        }
    }

    @Test
    fun `the audiobook fixtures are on the server, each search term matching only its book`() = runBlocking<Unit> {
        assertSoftly {
            withClue(Audiobooks.M4B) {
                items(BaseItemKind.AUDIO_BOOK, searchTerm = Audiobooks.M4B_SEARCH_TERM).map { it.name } shouldBe
                    listOf(Audiobooks.M4B)
            }
            withClue(Audiobooks.MP3) {
                items(BaseItemKind.AUDIO_BOOK, searchTerm = Audiobooks.MP3_SEARCH_TERM).map { it.name } shouldBe
                    listOf(Audiobooks.MP3)
            }
            withClue(Audiobooks.MULTI_PART) {
                val parts = items(BaseItemKind.AUDIO_BOOK).filter { it.album == Audiobooks.MULTI_PART }
                parts.size shouldBeGreaterThan 1
            }
            items(BaseItemKind.AUDIO_BOOK, searchTerm = Audiobooks.SEARCH_TERM_MATCHING_AN_AUDIOBOOK)
                .map { it.name } shouldContain Audiobooks.TITLE_MATCHING_SEARCH_TERM
        }
    }

    @Test
    fun `the book fixtures are on the server`() = runBlocking<Unit> {
        val books = items(BaseItemKind.BOOK)

        assertSoftly {
            books.firstOrNull()?.name shouldBe Books.FIRST_BY_TITLE_PDF
            books.firstOrNull()?.path.orEmpty().lowercase() shouldEndWith ".pdf"
            books.count { it.name.orEmpty().contains(Books.SEARCH_TERM, ignoreCase = true) } shouldBe 1
        }
    }

    @Test
    fun `the live tv fixtures are on the server, with something on now`() = runBlocking<Unit> {
        // Programmes come from a guide written when the server was brought up; it covers two weeks.
        val channels = api.liveTvApi.getLiveTvChannels(addCurrentProgram = true, fields = listOf(ItemFields.TAGS))
            .content.items
        val now = Instant.now()

        assertSoftly {
            val free = channels.filter { LiveTv.FREE_TAG in it.tags.orEmpty() }
            free.shouldNotBeEmpty()
            free.forEach { it.name.orEmpty() shouldStartWith LiveTv.FREE_PREFIX }
            val nonFree = channels.filter { it.name == LiveTv.NON_FREE_CHANNEL }
            nonFree shouldHaveSize 1
            nonFree.forEach { withClue(it.name) { (LiveTv.FREE_TAG in it.tags.orEmpty()) shouldBe false } }
            channels.forEach { channel ->
                withClue("${channel.name} has a programme on now (re-run test-server/up.sh to renew the guide)") {
                    val end = channel.currentProgram?.endDate
                    (end != null && end.toServerInstant().isAfter(now)) shouldBe true
                }
            }
        }
    }
}
