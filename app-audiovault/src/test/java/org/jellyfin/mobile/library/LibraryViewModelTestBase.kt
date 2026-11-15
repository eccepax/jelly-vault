package org.jellyfin.mobile.library

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.cancelAndJoin
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.ServerUser
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.data.dao.AudiobookEntryDao
import org.jellyfin.mobile.data.dao.HiddenItemDao
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

/**
 * What [LibraryViewModel]'s tests share: its collaborators as mocks, the server stubbed through
 * them, and helpers to wait for the loads (see each subclass for what it covers).
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class LibraryViewModelTestBase {

    protected val apiClient = mockk<ApiClient>(relaxed = true)
    protected val itemsApi = mockk<ItemsApi>(relaxed = true)
    protected val userViewsApi = mockk<UserViewsApi>(relaxed = true)
    protected val playStateApi = mockk<PlayStateApi>(relaxed = true)
    protected val appPreferences = mockk<AudioVaultAppPreferences>(relaxed = true)
    protected val userDao = mockk<UserDao>(relaxed = true)
    protected val audiobookEntryDao = mockk<AudiobookEntryDao>(relaxed = true)
    protected val downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    protected val hiddenItemDao = mockk<HiddenItemDao>(relaxed = true)
    protected val downloads = mutableMapOf<UUID, LocalJellyfinMediaSource>()
    protected val events = mutableListOf<String>()

    /** What waiting for a stopped book's listening to be reported does; nothing to wait for by default. */
    protected var listeningReported: suspend () -> Unit = {}

    protected val serverUser = ServerUser(
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
        every { apiClient.playStateApi } returns playStateApi

        every { appPreferences.currentServerId } returns 1L
        every { appPreferences.currentUserId } returns 1L
        every { userDao.getServerUser(1L, 1L) } returns serverUser
        every { appPreferences.librarySortOption } returns "TITLE"
        every { appPreferences.libraryGlobalFlattened } returns false

        every { hiddenItemDao.getAll() } returns flowOf(emptyList())
        every { audiobookEntryDao.separateBooks() } returns flowOf(emptyList())

        stubItems(emptyList())
        coEvery { userViewsApi.getUserViews(userId = any(), includeHidden = any()) } returns queryResult(emptyList())
    }

    /** The view models made, stopped after each test: a load still going would call the API unmocked. */
    private val created = mutableListOf<LibraryViewModel>()

    @AfterEach
    fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    protected fun createViewModel(): LibraryViewModel = LibraryViewModel(
        apiClient = apiClient,
        appPreferences = appPreferences,
        userDao = userDao,
        audiobookEntryDao = audiobookEntryDao,
        downloadedTrackIds = downloadedTrackIds,
        hiddenItemDao = hiddenItemDao,
        downloadedTrack = { downloads[it] },
        listening = ListeningUpdates(
            sendSavedOnDevice = { events += "sync" },
            stoppedBookReported = { listeningReported() },
        ),
    ).also { created += it }

    /**
     * The loads run in a detached coroutine with real `Dispatchers.IO` hops, which
     * `advanceUntilIdle()` doesn't wait for. Turbine's `awaitItem()` suspends until the result
     * actually arrives, so tests don't race the background work.
     */
    protected suspend fun LibraryViewModel.awaitSettled() {
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
    protected suspend fun LibraryViewModel.itemNamesShouldBecome(expected: List<String>) {
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

    protected fun queryResult(items: List<BaseItemDto>) =
        Response(BaseItemDtoQueryResult(items, items.size, 0), 200, emptyMap())

    /** Matches every `getItems` call site in [LibraryViewModel] — see the class KDoc. */
    protected fun stubItems(items: List<BaseItemDto>) {
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

    protected fun libraryView(name: String, collectionType: CollectionType?) = BaseItemDto(
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
    protected fun audioFile(id: UUID = UUID.randomUUID(), name: String = "Track") = BaseItemDto(
        id = id,
        name = name,
        type = BaseItemKind.AUDIO_BOOK,
        isFolder = false,
        path = "/books/$name/file.m4b",
    )
}
