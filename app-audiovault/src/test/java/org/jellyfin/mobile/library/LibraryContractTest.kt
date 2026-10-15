package org.jellyfin.mobile.library

import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.ServerUser
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.data.dao.AudiobookEntryDao
import org.jellyfin.mobile.data.dao.HiddenItemDao
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The library (flat mode, the default) built from the real Vault: books grouped from tracks, and
 * the queue a book plays. Read-only: nothing it caches is kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryContractTest {

    private val api get() = VaultTestServer.apiClient
    private lateinit var viewModel: LibraryViewModel

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val me = api.userApi.getCurrentUser().content.id.toString()
        val user = ServerUser(
            user = UserEntity(
                id = 1L,
                serverId = 1L,
                userId = me,
                accessToken = api.accessToken,
                lastLoginTimestamp = 0L,
            ),
            server = ServerEntity(id = 1L, hostname = requireNotNull(api.baseUrl), lastUsedTimestamp = 0L),
        )
        viewModel = LibraryViewModel(
            apiClient = api,
            appPreferences = mockk<AudioVaultAppPreferences>(relaxed = true) {
                every { libraryGlobalFlattened } returns true
                every { librarySortOption } returns SortOption.TITLE.name
                every { currentServerId } returns 1L
                every { currentUserId } returns 1L
            },
            userDao = mockk<UserDao> { every { getServerUser(1L, 1L) } returns user },
            audiobookEntryDao = mockk<AudiobookEntryDao>(relaxed = true),
            downloadedTrackIds = MutableStateFlow(emptySet()),
            hiddenItemDao = mockk<HiddenItemDao>(relaxed = true) { every { getAll() } returns flowOf(emptyList()) },
            downloadedTrack = { null },
            syncListening = {},
        )
        withTimeout(30.seconds) { viewModel.items.first { it.isNotEmpty() } }
        Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val books get() = viewModel.items.value.filterIsInstance<LibraryItem.AudiobookBundle>()

    private fun book(title: String) = books.single { it.displayName == title }

    @Test
    fun `a book in several files is one entry, its tracks in reading order`() = runBlocking<Unit> {
        val caste = book("Caste: The Origins of Our Discontents")

        caste.trackIds.size shouldBeGreaterThan 1
        caste.trackIds shouldBe tracksInOrder(caste.trackIds)
    }

    @Test
    fun `a book plays the same tracks, in the same order, that the library shows`() = runBlocking<Unit> {
        val caste = book("Caste: The Origins of Our Discontents")

        val queue = viewModel.fetchBundleQueue(caste)

        queue?.ids shouldBe caste.trackIds
    }

    @Test
    fun `every book has a real title`() {
        books.shouldNotBeEmpty()
        books.filter { it.displayName == "Unknown" } shouldBe emptyList()
    }

    @Test
    fun `each track is in exactly one book`() {
        books.flatMap { it.trackIds }.shouldBeUnique()
    }

    @Test
    fun `every audiobook track on the server is in some book`() = runBlocking<Unit> {
        val onServer = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
            recursive = true,
        ).content.items.orEmpty().mapNotNull { it.id }

        books.flatMap { it.trackIds } shouldContainAll onServer
    }

    /** [ids] as the server orders them by track number, then name. */
    private suspend fun tracksInOrder(ids: List<java.util.UUID>) = api.itemsApi.getItems(
        ids = ids,
        sortBy = listOf(ItemSortBy.INDEX_NUMBER, ItemSortBy.NAME),
        sortOrder = listOf(SortOrder.ASCENDING),
    ).content.items.orEmpty().mapNotNull { it.id }
}
