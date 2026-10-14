package org.jellyfin.mobile.livetv.catalog

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import org.junit.jupiter.api.Test
import java.time.Instant

class ChannelFilterTest {

    private val bulls = channel("1", "Bulls", tags = listOf("My Teams", "Sports"), now = "Bulls at Pacers")
    private val devils = channel("4.1", "Devils", tags = listOf("My Teams", "Sports"), favorite = true)
    private val espn = channel("43", "ESPN", tags = listOf("Sports"), now = "SportsCenter")
    private val tubiMlb = channel("410", "FREE| MLB", tags = listOf("Free", "Sports"))
    private val cnn = channel("200", "CNN", tags = listOf("News"), favorite = true)
    private val all = listOf(bulls, devils, espn, cnn, tubiMlb)

    @Test
    fun `no filter keeps every channel in order`() {
        all.filteredBy(ChannelFilter()) shouldContainExactly all
    }

    @Test
    fun `a tag keeps only channels carrying it`() {
        all.filteredBy(ChannelFilter(tag = "My Teams")) shouldContainExactly listOf(bulls, devils)
    }

    @Test
    fun `favorites only keeps favorite channels`() {
        all.filteredBy(ChannelFilter(favoritesOnly = true)) shouldContainExactly listOf(devils, cnn)
    }

    @Test
    fun `tag and favorites combine`() {
        all.filteredBy(ChannelFilter(tag = "Sports", favoritesOnly = true)) shouldContainExactly listOf(devils)
    }

    @Test
    fun `search matches channel names ignoring case`() {
        all.filteredBy(ChannelFilter(query = "espn")) shouldContainExactly listOf(espn)
    }

    @Test
    fun `search matches what is on now`() {
        // The matchup of a game only shows in the program title, e.g. on a team channel.
        all.filteredBy(ChannelFilter(query = "pacers")) shouldContainExactly listOf(bulls)
    }

    @Test
    fun `search matches the start of a channel number`() {
        all.filteredBy(ChannelFilter(query = "4")) shouldContainExactly listOf(devils, espn, tubiMlb)
    }

    @Test
    fun `a blank search matches everything`() {
        all.filteredBy(ChannelFilter(query = "  ")) shouldContainExactly all
    }

    @Test
    fun `search ignores surrounding spaces`() {
        all.filteredBy(ChannelFilter(query = " cnn ")) shouldContainExactly listOf(cnn)
    }

    @Test
    fun `search combines with a tag`() {
        all.filteredBy(ChannelFilter(tag = "Free", query = "mlb")) shouldContainExactly listOf(tubiMlb)
        all.filteredBy(ChannelFilter(tag = "News", query = "mlb")).shouldBeEmpty()
    }

    @Test
    fun `tags are every tag in use, once each, sorted`() {
        all.tags() shouldContainExactly listOf("Free", "My Teams", "News", "Sports")
    }

    @Test
    fun `channel numbers sort numerically, sub-channels after their main channel`() {
        val numbers = listOf("10", "4.2", "2", "4", "4.10", "4.1", "100", "5")

        numbers.sortedWith(channelNumberOrder) shouldContainExactly
            listOf("2", "4", "4.1", "4.2", "4.10", "5", "10", "100")
    }

    @Test
    fun `non-numeric channel numbers sort after numbered ones`() {
        listOf("B", "7", "A").sortedWith(channelNumberOrder) shouldContainExactly listOf("7", "A", "B")
    }

    @Test
    fun `equal channel numbers compare equal`() {
        channelNumberOrder.compare("4.1", "4.1") shouldBe 0
    }

    private fun channel(
        number: String,
        name: String,
        tags: List<String>,
        favorite: Boolean = false,
        now: String? = null,
    ) = LiveChannel(
        id = "id-$number",
        number = number,
        name = name,
        logoUrl = null,
        tags = tags,
        isFavorite = favorite,
        nowPlaying = now?.let { LiveProgram(it, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)) },
    )
}
