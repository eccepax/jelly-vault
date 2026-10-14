package org.jellyfin.mobile.common.server

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// The server speaks UTC, but the SDK's DateTimeSerializer turns every timestamp into a LocalDateTime
// in the device's time zone (and back). Treating those as UTC is off by the device's UTC offset.

/** The instant a LocalDateTime decoded by the SDK stands for. */
fun LocalDateTime.toServerInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = atZone(zone).toInstant()

/** The LocalDateTime the SDK will send to the server as [this] instant. */
fun Instant.toServerDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = LocalDateTime.ofInstant(this, zone)
