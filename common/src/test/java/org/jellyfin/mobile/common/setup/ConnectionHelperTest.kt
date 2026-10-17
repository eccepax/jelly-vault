package org.jellyfin.mobile.common.setup

import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.ServerVersion
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [pickBestServer], the precedence rule used to pick which discovered
 * server candidate to connect to.
 */
class ConnectionHelperTest {

    private fun server(
        score: RecommendedServerInfoScore,
        address: String = "http://server",
        issues: List<RecommendedServerIssue> = emptyList(),
    ): RecommendedServerInfo =
        RecommendedServerInfo(
            address = address,
            responseTime = 0L,
            score = score,
            issues = issues,
            systemInfo = Result.failure(RuntimeException("stub, unused by pickBestServer")),
        )

    @Test
    fun `a GREAT server is picked immediately`() {
        val great = server(RecommendedServerInfoScore.GREAT)
        val good = server(RecommendedServerInfoScore.GOOD)

        pickBestServer(listOf(good, great)) shouldBe great
    }

    @Test
    fun `only a GOOD server present is picked`() {
        val good = server(RecommendedServerInfoScore.GOOD)

        pickBestServer(listOf(good)) shouldBe good
    }

    @Test
    fun `multiple GOOD servers pick the first one`() {
        val first = server(RecommendedServerInfoScore.GOOD, address = "http://first")
        val second = server(RecommendedServerInfoScore.GOOD, address = "http://second")

        pickBestServer(listOf(first, second)) shouldBe first
    }

    @Test
    fun `only OK or BAD servers select nothing`() {
        val ok = server(RecommendedServerInfoScore.OK)
        val bad = server(RecommendedServerInfoScore.BAD)

        pickBestServer(listOf(ok, bad)) shouldBe null
    }

    @Test
    fun `an empty candidate list selects nothing`() {
        pickBestServer(emptyList()) shouldBe null
    }

    @Test
    fun `a GREAT server wins over a mix that also includes GOOD`() {
        val good = server(RecommendedServerInfoScore.GOOD)
        val great = server(RecommendedServerInfoScore.GREAT)
        val bad = server(RecommendedServerInfoScore.BAD)

        pickBestServer(listOf(good, bad, great)) shouldBe great
    }

    // --- tooOldServerVersion ---------------------------------------------------------------------

    @Test
    fun `a server too old for the apps is named by its version`() {
        val old = ServerVersion(10, 9, 11)
        val tooOld = server(
            RecommendedServerInfoScore.OK,
            issues = listOf(RecommendedServerIssue.UnsupportedServerVersion(old)),
        )

        tooOldServerVersion(listOf(server(RecommendedServerInfoScore.BAD), tooOld)) shouldBe old
    }

    @Test
    fun `servers failing for other reasons are not called too old`() {
        val unreachable = server(
            RecommendedServerInfoScore.BAD,
            issues = listOf(RecommendedServerIssue.MissingSystemInfo(RuntimeException("down"))),
        )

        tooOldServerVersion(listOf(unreachable)) shouldBe null
    }

    @Test
    fun `the oldest server the apps accept is Jellyfin 10_10`() {
        // The server tests pass on 10.10 and fail throughout on 10.9 (the SDK can't read its
        // responses). An SDK update that moves this needs the README's server support updating.
        Jellyfin.minimumVersion shouldBe ServerVersion(10, 10, 0, 0)
    }
}
