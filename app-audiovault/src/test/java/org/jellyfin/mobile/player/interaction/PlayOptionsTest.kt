package org.jellyfin.mobile.player.interaction

import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Basic functionality coverage for parsing [PlayOptions] out of the JS-bridge JSON payload. */
class PlayOptionsTest {

    private val validId1 = "ded9d452-b0ef-fd8a-13f9-78d5ba92d1b6"
    private val validId2 = "1f0384f3-2883-e577-81f8-edf6395ed6a4"

    @Test
    fun `parses a full payload`() {
        val json = JSONObject().apply {
            put("ids", JSONArray(listOf(validId1, validId2)))
            put("mediaSourceId", "source-1")
            put("startIndex", 1)
            put("startPositionTicks", 50_000_000L) // 5s
            put("audioStreamIndex", "2")
            put("subtitleStreamIndex", "-1")
        }

        val options = PlayOptions.fromJson(json)

        options?.ids?.map { it.toString() } shouldBe listOf(validId1, validId2)
        options?.mediaSourceId shouldBe "source-1"
        options?.startIndex shouldBe 1
        options?.startPosition shouldBe 5.seconds
        options?.audioStreamIndex shouldBe 2
        options?.subtitleStreamIndex shouldBe -1
    }

    @Test
    fun `ids defaults to empty list when absent`() {
        val options = PlayOptions.fromJson(JSONObject())
        options?.ids shouldBe emptyList()
    }

    @Test
    fun `invalid UUID strings in ids are silently dropped`() {
        val json = JSONObject().apply {
            put("ids", JSONArray(listOf(validId1, "not-a-uuid")))
        }
        PlayOptions.fromJson(json)?.ids?.map { it.toString() } shouldBe listOf(validId1)
    }

    @Test
    fun `startPosition is null when startPositionTicks is absent or zero`() {
        PlayOptions.fromJson(JSONObject())?.startPosition shouldBe null
        PlayOptions.fromJson(JSONObject().apply { put("startPositionTicks", 0L) })?.startPosition shouldBe null
    }

    @Test
    fun `startPosition is null for a negative startPositionTicks`() {
        val json = JSONObject().apply { put("startPositionTicks", -10L) }
        PlayOptions.fromJson(json)?.startPosition shouldBe null
    }

    @Test
    fun `audioStreamIndex and subtitleStreamIndex are null when not numeric`() {
        val json = JSONObject() // optString defaults to "" when absent, which is not numeric
        val options = PlayOptions.fromJson(json)
        options?.audioStreamIndex shouldBe null
        options?.subtitleStreamIndex shouldBe null
    }

    @Test
    fun `playFromDownloads is always false regardless of input`() {
        // fromJson is only used for the streamed-playback bridge; downloads use a separate path.
        val json = JSONObject().apply { put("playFromDownloads", true) }
        PlayOptions.fromJson(json)?.playFromDownloads shouldBe false
    }
}
