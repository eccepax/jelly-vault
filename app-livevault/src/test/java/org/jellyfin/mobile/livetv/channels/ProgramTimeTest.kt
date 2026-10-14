package org.jellyfin.mobile.livetv.channels

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.catalog.program
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ProgramTimeTest {

    private val show = program("Game", start = "2026-10-14T18:00:00Z", end = "2026-10-14T20:00:00Z")

    @Test
    fun `progress is how much of the program has aired`() {
        show.progressAt(Instant.parse("2026-10-14T18:30:00Z")) shouldBe (0.25f plusOrMinus 0.001f)
    }

    @Test
    fun `progress stays between empty and full`() {
        show.progressAt(Instant.parse("2026-10-14T17:00:00Z")) shouldBe 0f
        show.progressAt(Instant.parse("2026-10-14T21:00:00Z")) shouldBe 1f
    }

    @Test
    fun `the end time is shown in the device's time zone`() {
        // ofLocalizedTime(SHORT) puts a narrow no-break space before AM/PM on recent JDKs.
        show.endTimeText(ZoneId.of("America/New_York"), Locale.US).replace(' ', ' ') shouldBe "4:00 PM"
    }

    @Test
    fun `free channels are shown without their FREE prefix`() {
        channel("410", "FREE| MLB").displayName shouldBe "MLB"
    }

    @Test
    fun `other names are shown as they are`() {
        channel("43", "ESPN").displayName shouldBe "ESPN"
        channel("1", "FREEDOM TV").displayName shouldBe "FREEDOM TV"
    }
}
