package org.jellyfin.mobile.library

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.ServerUser
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.data.dao.AudiobookEntryDao
import org.jellyfin.mobile.data.dao.HiddenItemDao
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.mobile.data.entity.HiddenItemEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Tests [LibraryViewModel]'s own orchestration: root/folder loading, the global-flat
 * cache-then-revalidate flow, navigation, sort persistence, and hide/download filtering.
 *
 * [ApiClient.itemsApi]/[ApiClient.userViewsApi] are extension properties that break on a plain
 * mock, so they're stubbed with `mockkStatic`; one `any()` stub of `getItems` covers every call site.
 *
 * The ViewModel is created per test because `libraryGlobalFlattened` must be stubbed first: `init`
 * reads it immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val itemsApi = mockk<ItemsApi>(relaxed = true)
    private val userViewsApi = mockk<UserViewsApi>(relaxed = true)
    private val appPreferences = mockk<AudioVaultAppPreferences>(relaxed = true)
    private val userDao = mockk<UserDao>(relaxed = true)
    private val audiobookEntryDao = mockk<AudiobookEntryDao>(relaxed = true)
    private val downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenItemDao = mockk<HiddenItemDao>(relaxed = true)
    private val downloads = mutableMapOf<UUID, LocalJellyfinMediaSource>()
    private val events = mutableListOf<String>()

    private val serverUser = ServerUser(
        user = UserEntity(
            id = 1L,
            serverId = 1L,
            userId = "11111111-1111-1111-1111-111111111111",
            accessToken = "token",
            lastLoginTimestamp = 0L,
        ),
        server = ServerEntity(id = 1L, hostname = "https://example.com", lastUsedTimestamp = 0L),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.itemsApi } returns itemsApi
        every { apiClient.userViewsApi } returns userViewsApi

        every { appPreferences.currentServerId } returns 1L
        every { appPreferences.currentUserId } returns 1L
        every { userDao.getServerUser(1L, 1L) } returns serverUser
        every { appPreferences.librarySortOption } returns "TITLE"
        every { appPreferences.libraryGlobalFlattened } returns false

        every { hiddenItemDao.getAll() } returns flowOf(emptyList())

        stubItems(emptyList())
        coEvery { userViewsApi.getUserViews(userId = any(), includeHidden = any()) } returns queryResult(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun createViewModel() = LibraryViewModel(
        apiClient = apiClient,
        appPreferences = appPreferences,
        userDao = userDao,
        audiobookEntryDao = audiobookEntryDao,
        downloadedTrackIds = downloadedTrackIds,
        hiddenItemDao = hiddenItemDao,
        downloadedTrack = { downloads[it] },
        syncListening = { events += "sync" },
    )

    /**
     * The loads run in a detached coroutine with real `Dispatchers.IO` hops, which
     * `advanceUntilIdle()` doesn't wait for. Turbine's `awaitItem()` suspends until the result
     * actually arrives, so tests don't race the background work.
     */
    private suspend fun LibraryViewModel.awaitSettled() {
        isLoading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }
    }

    /**
     * Waits for [LibraryViewModel.items] to show [expected]. That list is derived through `combine`,
     * which can still be catching up on the IO thread when [awaitSettled] returns or right after a
     * re-sort, so `items.value` may briefly lag behind.
     */
    private suspend fun LibraryViewModel.itemNamesShouldBecome(expected: List<String>) {
        var names: List<String>? = null
        try {
            items.test {
                names = awaitItem().map { it.displayName }
                while (names != expected) names = awaitItem().map { it.displayName }
                cancelAndIgnoreRemainingEvents()
            }
        } catch (e: AssertionError) {
            names shouldBe expected // fails with the last list seen rather than a bare timeout
            throw e
        }
    }

    private fun queryResult(items: List<BaseItemDto>) =
        Response(BaseItemDtoQueryResult(items, items.size, 0), 200, emptyMap())

    /** Matches every `getItems` call site in [LibraryViewModel] — see the class KDoc. */
    private fun stubItems(items: List<BaseItemDto>) {
        coEvery {
            itemsApi.getItems(
                userId = any(),
                parentId = any(),
                includeItemTypes = any(),
                fields = any(),
                sortBy = any(),
                sortOrder = any(),
                recursive = any(),
                limit = any(),
                ids = any(),
            )
        } returns queryResult(items)
    }

    private fun libraryView(name: String, collectionType: CollectionType?) = BaseItemDto(
        id = UUID.randomUUID(),
        name = name,
        type = BaseItemKind.COLLECTION_FOLDER,
        collectionType = collectionType,
        isFolder = true,
    )

    /**
     * A standalone audiobook file. Needs a distinct [path] so `AudiobookEntryBuilder` (used by
     * `loadGlobalFlat`) treats it as its own standalone entry rather than merging every null-path
     * file into one shared-root bundle — see `AudiobookEntryBuilderTest`'s
     * "null path and null parentId both fall back to a shared root bucket".
     */
    private fun audioFile(id: UUID = UUID.randomUUID(), name: String = "Track") = BaseItemDto(
        id = id,
        name = name,
        type = BaseItemKind.AUDIO_BOOK,
        isFolder = false,
        path = "/books/$name/file.m4b",
    )

    // --- loadRoot (init, libraryGlobalFlattened = false) --------------------------------------

    @Test
    fun `loadRoot only shows BOOKS-collectionType libraries, sorted by name`() = runTest {
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(
            listOf(
                libraryView("Zeta Books", CollectionType.BOOKS),
                libraryView("Movies", CollectionType.MOVIES),
                libraryView("Alpha Books", CollectionType.BOOKS),
            ),
        )

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.itemNamesShouldBecome(listOf("Alpha Books", "Zeta Books"))
    }

    @Test
    fun `loadRoot falls back to all libraries when none are BOOKS type`() = runTest {
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Movies", CollectionType.MOVIES)))

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.itemNamesShouldBecome(listOf("Movies"))
    }

    @Test
    fun `loadRoot sets an error when the current user cannot be resolved`() = runTest {
        every { appPreferences.currentServerId } returns null

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.error.value shouldBe "User not found"
        viewModel.isLoading.value shouldBe false
    }

    @Test
    fun `loadRoot sets an error on ApiClientException`() = runTest {
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } throws ApiClientException("boom")

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.error.value shouldBe "boom"
    }

    // --- loadGlobalFlat (init, libraryGlobalFlattened = true) ----------------------------------

    @Test
    fun `init loads the global flat list and persists it to Room when libraryGlobalFlattened is true`() = runTest {
        every { appPreferences.libraryGlobalFlattened } returns true
        val trackId = UUID.randomUUID()
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        stubItems(listOf(audioFile(trackId, "Solo Book")))

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.itemNamesShouldBecome(listOf("Solo Book"))
        coVerify { audiobookEntryDao.clearForServer(1L) }
        coVerify { audiobookEntryDao.insertAll(any()) }
    }

    @Test
    fun `loadGlobalFlat serves from the Room cache immediately, then still revalidates from the server`() = runTest {
        every { appPreferences.libraryGlobalFlattened } returns true
        val cachedEntry = AudiobookEntryEntity(
            id = "cached-1",
            serverId = 1L,
            title = "Cached Book",
            author = null,
            imageItemId = null,
            imageTag = null,
            runtimeTicks = 0L,
            playedPercentage = 0f,
            trackIds = UUID.randomUUID().toString(),
            isBundle = false,
            parentId = null,
            dateAdded = "",
            lastPlayed = "",
        )
        coEvery { audiobookEntryDao.getAll(1L) } returns listOf(cachedEntry)
        // The server answers only once the cached list has been seen: both run on real IO threads,
        // and if both landed first the StateFlow would conflate the cached list away.
        val serverMayAnswer = CompletableDeferred<Unit>()
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } coAnswers {
            serverMayAnswer.await()
            queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        }

        val viewModel = createViewModel()

        viewModel.items.test {
            var painted = awaitItem()
            while (painted.isEmpty()) painted = awaitItem() // StateFlow's initial empty value
            painted.map { it.displayName } shouldBe listOf("Cached Book") // painted from cache

            serverMayAnswer.complete(Unit)
            awaitItem() shouldBe emptyList() // revalidated from the (empty, per this test's stub) server
        }
        coVerify { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    // --- checkAndReloadIfSettingChanged ---------------------------------------------------------

    @Test
    fun `checkAndReloadIfSettingChanged reloads when the flatten setting changed since init`() = runTest {
        val viewModel = createViewModel() // constructed with libraryGlobalFlattened = false
        viewModel.awaitSettled()
        every { appPreferences.libraryGlobalFlattened } returns true

        viewModel.checkAndReloadIfSettingChanged()
        viewModel.awaitSettled()

        coVerify(atLeast = 2) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    @Test
    fun `checkAndReloadIfSettingChanged is a no-op when the setting has not changed`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.checkAndReloadIfSettingChanged()

        // Exactly the one call from init's loadRoot() — no extra reload triggered.
        coVerify(exactly = 1) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    @Test
    fun `checkAndReloadIfSettingChanged is a no-op while browsing inside a folder`() = runTest {
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        val folderId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        viewModel.openFolder(folderId, "Some Folder")
        viewModel.awaitSettled()
        every { appPreferences.libraryGlobalFlattened } returns true

        viewModel.checkAndReloadIfSettingChanged()

        // Still just the one root-level call from init; being inside a folder suppresses the reload.
        coVerify(exactly = 1) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    // --- folder navigation -----------------------------------------------------------------------

    @Test
    fun `openFolder pushes the folder and allows going up`() = runTest {
        val folderId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.openFolder(folderId, "Chapter One")
        viewModel.awaitSettled()

        viewModel.canGoUp.value shouldBe true
        viewModel.title.value shouldBe "Chapter One"
    }

    @Test
    fun `goUp from the only folder on the stack returns to the root list`() = runTest {
        val folderId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        viewModel.openFolder(folderId, "Chapter One")
        viewModel.awaitSettled()

        val result = viewModel.goUp()
        viewModel.awaitSettled()

        result shouldBe true
        viewModel.canGoUp.value shouldBe false
        viewModel.title.value shouldBe "Library"
    }

    @Test
    fun `goUp with nothing on the stack returns false`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.goUp() shouldBe false
    }

    // --- search ----------------------------------------------------------------------------------

    @Test
    fun `the search shown is the one filtering`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.setFilterQuery("potter")

        viewModel.filterQuery.value shouldBe "potter"
    }

    @Test
    fun `opening a folder clears the search, so the box doesn't show one that no longer filters`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        viewModel.setFilterQuery("potter")

        viewModel.openFolder(UUID.randomUUID(), "Chapter One")
        viewModel.awaitSettled()

        viewModel.filterQuery.value shouldBe ""
    }

    @Test
    fun `going up clears the search too`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        viewModel.openFolder(UUID.randomUUID(), "Chapter One")
        viewModel.awaitSettled()
        viewModel.setFilterQuery("potter")

        viewModel.goUp()
        viewModel.awaitSettled()

        viewModel.filterQuery.value shouldBe ""
    }

    // --- sort ------------------------------------------------------------------------------------

    @Test
    fun `setSortOption persists the choice`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.setSortOption(SortOption.AUTHOR)
        viewModel.awaitSettled()

        verify { appPreferences.librarySortOption = "AUTHOR" }
        viewModel.sortOption.value shouldBe SortOption.AUTHOR
    }

    @Test
    fun `setSortOption in global-flat mode with a cache re-sorts instantly without a network call`() = runTest {
        every { appPreferences.libraryGlobalFlattened } returns true
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        stubItems(listOf(audioFile(name = "Bravo"), audioFile(name = "Alpha")))

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        // Re-sorting from the in-memory cache is fully synchronous (see setSortOption) — no
        // coroutine involved, so no further settling is needed before asserting.
        viewModel.setSortOption(SortOption.TITLE)

        // Only the one userViews call from init's loadGlobalFlat — re-sorting the cache triggers
        // no additional server round trip.
        coVerify(exactly = 1) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
        viewModel.itemNamesShouldBecome(listOf("Alpha", "Bravo"))
    }

    // --- hidden items ------------------------------------------------------------------------------

    @Test
    fun `toggleHidden hides a currently-visible item`() = runTest {
        val itemId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.toggleHidden(itemId)

        coVerify { hiddenItemDao.insert(HiddenItemEntity(itemId.toString())) }
        coVerify(exactly = 0) { hiddenItemDao.delete(any()) }
    }

    @Test
    fun `toggleHidden unhides an already-hidden item`() = runTest {
        val itemId = UUID.randomUUID()
        every { hiddenItemDao.getAll() } returns flowOf(listOf(HiddenItemEntity(itemId.toString())))
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.toggleHidden(itemId)

        coVerify { hiddenItemDao.delete(itemId.toString()) }
        coVerify(exactly = 0) { hiddenItemDao.insert(any()) }
    }

    @Test
    fun `downloadedIds are the tracks whose download is complete`() = runTest {
        val itemId = UUID.randomUUID().toString()
        downloadedTrackIds.value = setOf(itemId)

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.downloadedIds.value shouldBe setOf(itemId)
    }

    @Test
    fun `hiddenIds reflects the hidden item dao`() = runTest {
        val itemId = UUID.randomUUID().toString()
        every { hiddenItemDao.getAll() } returns flowOf(listOf(HiddenItemEntity(itemId)))

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.hiddenIds.value shouldBe setOf(itemId)
    }

    // --- fetchBundleQueue routing ------------------------------------------------------------------

    @Test
    fun `fetchBundleQueue plays a single-file bundle directly`() = runTest {
        val trackId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        val bundle = LibraryItem.AudiobookBundle(
            id = trackId,
            displayName = "Solo",
            description = "",
            imageItemId = null,
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = listOf(trackId),
            isBundle = false,
            parentFolderId = UUID.randomUUID(),
        )

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe listOf(trackId)
    }

    @Test
    fun `fetchBundleQueue with a known parent folder delegates to the album queue API`() = runTest {
        val folderId = UUID.randomUUID()
        val trackId = UUID.randomUUID()
        stubItems(listOf(audioFile(trackId, "Chapter 1")))
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        val bundle = LibraryItem.AudiobookBundle(
            id = folderId,
            displayName = "Bundle",
            description = "",
            imageItemId = null,
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = listOf(trackId, UUID.randomUUID()),
            isBundle = true,
            parentFolderId = folderId,
        )

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe listOf(trackId)
    }

    @Test
    fun `fetchBundleQueue without a parent folder fetches tracks directly by id`() = runTest {
        val trackId1 = UUID.randomUUID()
        val trackId2 = UUID.randomUUID()
        stubItems(listOf(audioFile(trackId1, "Track 1"), audioFile(trackId2, "Track 2")))
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        val bundle = LibraryItem.AudiobookBundle(
            id = UUID.randomUUID(),
            displayName = "Bundle",
            description = "",
            imageItemId = null,
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = listOf(trackId1, trackId2),
            isBundle = true,
            parentFolderId = null,
        )

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe listOf(trackId1, trackId2)
    }

    // --- Listening the server hasn't heard about ------------------------------------------------

    @Test
    fun `listening saved on the device reaches the server before a book's resume point is worked out`() = runTest {
        val folderId = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        events.clear()
        coEvery {
            itemsApi.getItems(
                userId = any(),
                parentId = any(),
                includeItemTypes = any(),
                fields = any(),
                sortBy = any(),
                sortOrder = any(),
                recursive = any(),
                limit = any(),
                ids = any(),
            )
        } answers {
            events += "resume point"
            queryResult(listOf(audioFile(name = "Chapter 1")))
        }

        viewModel.fetchBundleQueue(bookOfThree(parentFolderId = folderId).first)

        events shouldBe listOf("sync", "resume point")
    }

    // --- Offline (the server can't be reached) -------------------------------------------------

    @Test
    fun `offline, a book plays from its downloads, resuming at the track left partway through`() = runTest {
        val (bundle, tracks) = bookOfThree(parentFolderId = UUID.randomUUID())
        downloaded(tracks[0], "Chapter 1", left = Duration.ZERO)
        downloaded(tracks[1], "Chapter 2", left = 5.minutes)
        downloaded(tracks[2], "Chapter 3", left = Duration.ZERO)
        val viewModel = offlineViewModel()

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe tracks
        queue?.startIndex shouldBe 1
        queue?.startPosition shouldBe 5.minutes
        queue?.trackNames shouldBe listOf("Chapter 1", "Chapter 2", "Chapter 3")
    }

    @Test
    fun `offline, a book not yet started plays from its first track`() = runTest {
        val (bundle, tracks) = bookOfThree(parentFolderId = UUID.randomUUID())
        tracks.forEachIndexed { i, id -> downloaded(id, "Chapter ${i + 1}", left = Duration.ZERO) }
        val viewModel = offlineViewModel()

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe tracks
        queue?.startIndex shouldBe 0
        queue?.startPosition shouldBe null
    }

    @Test
    fun `offline, a book without a known folder plays from its downloads too`() = runTest {
        val (bundle, tracks) = bookOfThree(parentFolderId = null)
        downloaded(tracks[2], "Chapter 3", left = 1.minutes)
        val viewModel = offlineViewModel()

        val queue = viewModel.fetchBundleQueue(bundle)

        queue?.ids shouldBe tracks
        queue?.startIndex shouldBe 2
        queue?.trackNames shouldBe listOf("Track 1", "Track 2", "Chapter 3")
    }

    private suspend fun offlineViewModel(): LibraryViewModel {
        val viewModel = createViewModel()
        viewModel.awaitSettled()
        coEvery {
            itemsApi.getItems(
                userId = any(),
                parentId = any(),
                includeItemTypes = any(),
                fields = any(),
                sortBy = any(),
                sortOrder = any(),
                recursive = any(),
                limit = any(),
                ids = any(),
            )
        } throws TimeoutException("Vault unreachable")
        return viewModel
    }

    private fun bookOfThree(parentFolderId: UUID?): Pair<LibraryItem.AudiobookBundle, List<UUID>> {
        val tracks = List(3) { UUID.randomUUID() }
        val bundle = LibraryItem.AudiobookBundle(
            id = parentFolderId ?: UUID.randomUUID(),
            displayName = "Dune",
            description = "",
            imageItemId = null,
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = tracks,
            isBundle = true,
            parentFolderId = parentFolderId,
        )
        return bundle to tracks
    }

    private fun downloaded(id: UUID, name: String, left: Duration) {
        downloads[id] = mockk(relaxed = true) {
            every { item } returns BaseItemDto(id = id, type = BaseItemKind.AUDIO, name = name)
            every { startTime } returns left
        }
    }
}
