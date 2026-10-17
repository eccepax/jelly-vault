package org.jellyfin.mobile.livetv.catalog

import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * Runs [JellyfinLiveTvCatalog] against the test server's Live TV (an M3U tuner with an XMLTV guide).
 * Read-only apart from one channel's favorite flag, which is restored afterwards. Never plays a
 * stream: any other channel would take the household's single provider login.
 */
class JellyfinLiveTvCatalogContractTest {

    private val api get() = VaultTestServer.apiClient
    private lateinit var catalog: LiveTvCatalog
    private val originalFavorites = mutableMapOf<String, Boolean>()

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinLiveTvCatalog(api)
    }

    @AfterEach
    fun restoreFavorites() = runBlocking<Unit> {
        originalFavorites.forEach { (id, favorite) -> catalog.setFavorite(id, favorite) }
    }

    @Test
    fun `channels come from the tuner`() = runBlocking<Unit> {
        val channels = catalog.channels()

        channels.map { it.name } shouldContain LiveTv.NON_FREE_CHANNEL
        channels.forEach {
            it.id.shouldNotBeBlank()
            it.name.shouldNotBeBlank()
            it.number.shouldNotBeBlank()
        }
    }

    @Test
    fun `each channel is listed once`() = runBlocking<Unit> {
        catalog.channels().map { it.id }.shouldBeUnique()
    }

    @Test
    fun `channels are in channel number order`() = runBlocking<Unit> {
        catalog.channels().map { it.number } shouldBeSortedWith channelNumberOrder
    }

    @Test
    fun `channels carry the server's channel tags`() = runBlocking<Unit> {
        // Read from the plain items listing: the server's tag filters can't be relied on, as the
        // legacy one lists no tags on Jellyfin 12, and the newer one none on 10.11.
        val serverTags = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.TV_CHANNEL),
            recursive = true,
            fields = listOf(ItemFields.TAGS),
        ).content.items.flatMap { it.tags.orEmpty() }.distinct()

        serverTags.shouldNotBeEmpty()
        catalog.channels().tags() shouldContainAll serverTags
    }

    @Test
    fun `free channels are tagged Free`() = runBlocking<Unit> {
        // The free channels are the only ones tests and fallbacks may play.
        catalog.channels().filter { it.name.startsWith(LiveTv.FREE_PREFIX) }.shouldNotBeEmpty().forEach {
            it.tags shouldContain LiveTv.FREE_TAG
        }
    }

    @Test
    fun `channel logo urls serve images`() = runBlocking<Unit> {
        // A sample: on a real server with hundreds of channels, fetching every logo is slow and proves nothing more.
        val logos = catalog.channels().mapNotNull { it.logoUrl }

        logos.shouldNotBeEmpty()
        logos.take(LOGO_SAMPLE).forEach { url ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.responseCode shouldBe 200
            connection.contentType shouldStartWith "image/"
        }
    }

    @Test
    fun `what is on now is airing now`() = runBlocking<Unit> {
        val now = Instant.now()
        val airing = catalog.channels().mapNotNull { it.nowPlaying }

        airing.shouldNotBeEmpty()
        airing.forEach { program ->
            program.title.shouldNotBeBlank()
            program.end shouldBeGreaterThan program.start
            // Slack for a program ending between the server's answer and this check.
            program.start shouldBeLessThanOrEqualTo now
            program.end shouldBeGreaterThan now.minus(CLOCK_SLACK)
        }
    }

    @Test
    fun `favoriting a channel is reported by the channel list`() = runBlocking<Unit> {
        val channel = someChannel()

        catalog.setFavorite(channel.id, true)
        catalog.channels().first { it.id == channel.id }.isFavorite shouldBe true

        catalog.setFavorite(channel.id, false)
        catalog.channels().first { it.id == channel.id }.isFavorite shouldBe false
    }

    private suspend fun someChannel(): LiveChannel =
        catalog.channels().first { it.name == LiveTv.NON_FREE_CHANNEL }.also { originalFavorites[it.id] = it.isFavorite }
}

private const val LOGO_SAMPLE = 5
private val CLOCK_SLACK = Duration.ofMinutes(1)
