package org.jellyfin.mobile.common.server

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.serializer.DateTimeSerializer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** The SDK decodes the server's UTC timestamps into LocalDateTimes in the device's time zone. */
class ServerTimeTest {

    private val serverJson = "\"2026-10-14T18:00:00.0000000Z\""
    private val instant = Instant.parse("2026-10-14T18:00:00Z")

    @Test
    fun `a timestamp the SDK decoded is the instant the server sent`() {
        val decoded = Json.decodeFromString(DateTimeSerializer(), serverJson)

        decoded.toServerInstant() shouldBe instant
    }

    @Test
    fun `holds in a time zone away from UTC`() {
        val newYork = ZoneId.of("America/New_York")
        val decoded = Json.decodeFromString(DateTimeSerializer(newYork), serverJson)

        decoded.toServerInstant(newYork) shouldBe instant
    }

    @Test
    fun `an instant sent to the server arrives unchanged`() {
        val newYork = ZoneId.of("America/New_York")

        val sent = Json.encodeToString(DateTimeSerializer(newYork), instant.toServerDateTime(newYork))

        // The SDK sends the local time with its offset, e.g. "2026-10-14T14:00:00-04:00".
        OffsetDateTime.parse(sent.trim('"')).toInstant() shouldBe instant
    }
}
