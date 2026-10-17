package org.jellyfin.mobile.common.setup

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Plain HTTP is fine at home, where the password never leaves the house; to a server out on the
 * internet it crosses networks anyone along the way can read. Only that case warns.
 */
class UnencryptedSignInTest {

    private fun warns(vararg urls: String, expected: Boolean) = assertSoftly {
        urls.forEach { url -> withClue(url) { signsInUnencryptedOverInternet(url) shouldBe expected } }
    }

    @Test
    fun `plain HTTP to a server on the internet warns`() = warns(
        "http://vault8494.duckdns.org:8494",
        "http://jellyfin.example.com",
        "HTTP://Jellyfin.Example.com:8096/",
        "http://8.8.8.8:8096",
        "http://172.32.0.1:8096",
        expected = true,
    )

    @Test
    fun `so does one whose name java's URI can't read as a host`() = warns(
        "http://my_server.duckdns.org:8494",
        "http://jellyfín.example.com",
        "http://user@my_server.duckdns.org:8494/web",
        expected = true,
    )

    @Test
    fun `HTTPS never warns`() = warns(
        "https://vault8494.duckdns.org:8920",
        "https://8.8.8.8",
        expected = false,
    )

    @Test
    fun `plain HTTP on a home network doesn't`() = warns(
        "http://192.168.1.5:8096",
        "http://10.0.2.2:8494",
        "http://172.16.0.9",
        "http://172.31.255.1",
        "http://169.254.10.1",
        "http://127.0.0.1:8096",
        "http://localhost:8096",
        "http://jellyfin:8096",
        "http://nas.local:8096",
        "http://media.lan",
        "http://media.home.arpa",
        "http://[::1]:8096",
        "http://[fd12:3456::1]:8096",
        "http://[fe80::1]:8096",
        "http://[fe80::1%25wlan0]:8096",
        "http://my_nas:8096",
        expected = false,
    )

    @Test
    fun `nor over Tailscale, which encrypts on its own`() = warns(
        "http://100.101.102.103:8096",
        "http://vault.tailnet-name.ts.net:8096",
        expected = false,
    )

    @Test
    fun `an address that can't be read doesn't warn`() = warns(
        "",
        "not a url",
        expected = false,
    )
}
