package org.jellyfin.mobile.common.server

import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.junit.jupiter.api.Test
import java.io.IOException

class NetworkFailureTest {

    @Test
    fun `no route to the server is worth trying again`() {
        IOException("offline").worthRetrying() shouldBe true
        TimeoutException("slow").worthRetrying() shouldBe true
    }

    @Test
    fun `a server not ready for the request is worth trying again`() {
        // Signed out, overloaded, or restarting behind a proxy
        for (status in listOf(401, 403, 408, 429, 500, 502, 503, 504)) {
            InvalidStatusException(status).worthRetrying() shouldBe true
        }
    }

    @Test
    fun `a request the server refuses as such isn't`() {
        for (status in listOf(400, 404, 405, 409, 422)) {
            InvalidStatusException(status).worthRetrying() shouldBe false
        }
    }

    @Test
    fun `a bug here isn't`() {
        IllegalStateException("bug").worthRetrying() shouldBe false
    }
}
