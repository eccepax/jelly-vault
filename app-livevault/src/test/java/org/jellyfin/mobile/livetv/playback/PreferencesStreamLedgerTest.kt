package org.jellyfin.mobile.livetv.playback

import android.app.Application
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PreferencesStreamLedgerTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val a = StreamTicket("chan-a", "ls-a", "ps-a", "ms-a")
    private val b = StreamTicket("chan-b", "ls-b", null, "ms-b")

    @Test
    fun `starts empty`() {
        PreferencesStreamLedger(context).all().shouldBeEmpty()
    }

    @Test
    fun `remembers open streams across app restarts`() {
        PreferencesStreamLedger(context).apply {
            add(a)
            add(b)
        }

        // A new instance reads what the old one (the killed app) wrote.
        PreferencesStreamLedger(context).all() shouldContainExactlyInAnyOrder listOf(a, b)
    }

    @Test
    fun `forgets a closed stream`() {
        val ledger = PreferencesStreamLedger(context)
        ledger.add(a)
        ledger.add(b)

        ledger.remove(a)

        PreferencesStreamLedger(context).all() shouldContainExactlyInAnyOrder listOf(b)
    }
}
