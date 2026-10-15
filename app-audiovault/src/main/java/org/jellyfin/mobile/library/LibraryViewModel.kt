package org.jellyfin.mobile.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.data.dao.AudiobookEntryDao
import org.jellyfin.mobile.data.dao.HiddenItemDao
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.mobile.data.entity.HiddenItemEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel for browsing audiobooks and audio content from Jellyfin server
 *
 * Suppresses TooManyFunctions: this is the single ViewModel backing the whole library screen
 * (browsing/search/sort, download tracking, hidden-item tracking, queue building). Splitting it
 * for a lint count would spread closely related list-filtering state across multiple classes.
 */
@Suppress("TooManyFunctions")
class LibraryViewModel(
    private val apiClient: ApiClient,
    private val appPreferences: AudioVaultAppPreferences,
    private val userDao: UserDao,
    private val audiobookEntryDao: AudiobookEntryDao,
    /** Tracks whose download is complete (see AudiobookDownloads). */
    private val downloadedTrackIds: Flow<Set<String>>,
    private val hiddenItemDao: HiddenItemDao,
) : ViewModel() {

    private val _rawItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _filterQuery = MutableStateFlow("")
    val showDownloadedOnly = MutableStateFlow(false)
    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    /** When false (default), items hidden via [toggleHidden] are excluded from [items]. */
    val showHiddenItems = MutableStateFlow(false)
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    private val filterState = combine(
        showDownloadedOnly,
        _downloadedIds,
        showHiddenItems,
        _hiddenIds,
    ) { downloadedOnly, downloadedIds, showHidden, hiddenIds ->
        LibraryFilterState(downloadedOnly, downloadedIds, showHidden, hiddenIds)
    }

    val items: StateFlow<List<LibraryItem>> = combine(
        _rawItems,
        _filterQuery,
        filterState,
    ) { items, query, filter ->
        filterLibraryItems(items, query, filter)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _title = MutableStateFlow<String>("Library")
    val title = _title.asStateFlow()

    private val _canGoUp = MutableStateFlow(false)
    val canGoUp = _canGoUp.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.fromName(appPreferences.librarySortOption))
    val sortOption = _sortOption.asStateFlow()

    private val folderStack = ArrayDeque<Pair<UUID, String>>() // (folderId, folderName)

    // Track setting at last load to detect changes on resume
    private var lastGlobalFlattenState: Boolean = appPreferences.libraryGlobalFlattened

    // In-memory flat list cache; avoids re-fetching on sort option change
    private var cachedFlatEntries: List<AudiobookEntryEntity>? = null

    fun setFilterQuery(query: String) {
        _filterQuery.update { query }
    }

    data class AudioQueue(
        val ids: List<UUID>,
        val startIndex: Int,
        val trackNames: List<String>,
        val startPosition: Duration? = null,
    )

    /**
     * Returns the playback queue for [clickedId] within the current folder, including
     * a resume position fetched from Jellyfin for the clicked track.
     */
    suspend fun getAudioQueueWithResume(clickedId: UUID): AudioQueue {
        val audioItems = _rawItems.value.filterIsInstance<LibraryItem.AudioFile>()
        val audioIds = if (audioItems.isEmpty()) listOf(clickedId) else audioItems.map { it.id }
        val audioNames = if (audioItems.isEmpty()) listOf("Unknown") else audioItems.map { it.displayName }
        val index = audioIds.indexOf(clickedId).takeIf { it >= 0 } ?: 0
        val startPosition = fetchSingleTrackPosition(clickedId)
        return AudioQueue(audioIds, index, audioNames, startPosition)
    }

    private suspend fun fetchSingleTrackPosition(trackId: UUID): Duration? {
        return try {
            val userId = resolveCurrentUserId() ?: return null
            val items = withContext(Dispatchers.IO) {
                apiClient.itemsApi.getItems(userId = userId, ids = listOf(trackId)).content
            }
            items.items.orEmpty().firstOrNull()?.userData?.playbackPositionTicks
                ?.takeIf { it > 0L }
                ?.ticks
                ?.minus(15.seconds)
                ?.takeIf { it > Duration.ZERO }
        } catch (e: ApiClientException) {
            null
        }
    }

    /** Called by LibraryFragment when the player stops, to refresh stale progress data. */
    fun onPlaybackStopped() {
        cachedFlatEntries = null
        if (appPreferences.libraryGlobalFlattened) {
            loadGlobalFlat(forceRefresh = true)
        } else {
            val current = folderStack.lastOrNull()
            if (current != null) loadChildren(current.first, current.second)
        }
    }

    init {
        viewModelScope.launch {
            downloadedTrackIds.collect { ids -> _downloadedIds.value = ids }
        }
        viewModelScope.launch {
            hiddenItemDao.getAll().collect { records ->
                _hiddenIds.value = records.map { it.itemId }.toSet()
            }
        }
        loadRoot()
    }

    /** Hides [itemId] if currently visible, or unhides it if already hidden. */
    fun toggleHidden(itemId: UUID) {
        viewModelScope.launch {
            val key = itemId.toString()
            if (key in _hiddenIds.value) {
                hiddenItemDao.delete(key)
            } else {
                hiddenItemDao.insert(HiddenItemEntity(key))
            }
        }
    }

    /**
     * Resolves the current user's UUID from preferences + DB.
     * Returns null if no server/user is selected or the DB record is missing.
     */
    private suspend fun resolveCurrentUserId(): UUID? {
        val serverId = appPreferences.currentServerId ?: return null
        val userIdDbId = appPreferences.currentUserId ?: return null
        val serverUser = withContext(Dispatchers.IO) {
            userDao.getServerUser(serverId, userIdDbId)
        }
        return serverUser?.user?.userId?.let { UUID.fromString(it) }
    }

    /**
     * Fetches the tracks of a [MusicAlbum][BaseItemKind.MUSIC_ALBUM] or bundle folder and
     * computes a resume position from each track's UserData.
     *
     * Resume logic:
     * - Start at the first track that hasn't been marked played.
     * - Seek to that track's saved playbackPositionTicks (0 if never started).
     * - If every track is played, restart from the beginning.
     *
     * Returns null if the folder has no audio children or the API call fails.
     */
    suspend fun fetchAlbumQueue(albumId: UUID): AudioQueue? {
        _isLoading.update { true }
        return try {
            val userId = resolveCurrentUserId() ?: return null
            val result = withContext(Dispatchers.IO) {
                apiClient.itemsApi.getItems(
                    userId = userId,
                    parentId = albumId,
                    includeItemTypes = listOf(BaseItemKind.AUDIO, BaseItemKind.AUDIO_BOOK),
                    sortBy = listOf(ItemSortBy.INDEX_NUMBER, ItemSortBy.NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    recursive = false,
                ).content
            }
            val tracks = result.items.orEmpty()
            if (tracks.isEmpty()) return null

            buildResumeQueue(tracks)
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to fetch album tracks for $albumId")
            null
        } finally {
            _isLoading.update { false }
        }
    }

    /**
     * Builds a playback queue for a flat-list [LibraryItem.AudiobookBundle].
     *
     * - Single-file standalones play the lone track directly.
     * - Multi-track bundles with a known [LibraryItem.AudiobookBundle.parentFolderId] delegate to
     *   [fetchAlbumQueue] so resume position is computed from server-side UserData.
     * - Multi-track bundles without a parentFolderId (parentId was null from Jellyfin) fetch tracks
     *   directly by ID via [fetchTracksByIds], which computes the same resume position from each
     *   track's UserData.
     */
    suspend fun fetchBundleQueue(bundle: LibraryItem.AudiobookBundle): AudioQueue? {
        if (!bundle.isBundle || bundle.trackIds.size == 1) {
            val trackId = bundle.trackIds.firstOrNull() ?: return null
            val startPosition = fetchSingleTrackPosition(trackId)
            return AudioQueue(listOf(trackId), 0, listOf(bundle.displayName), startPosition)
        }
        val folderId = bundle.parentFolderId
        return if (folderId != null) {
            fetchAlbumQueue(folderId)
        } else {
            fetchTracksByIds(bundle.trackIds)
        }
    }

    private suspend fun fetchTracksByIds(trackIds: List<UUID>): AudioQueue? {
        if (trackIds.isEmpty()) return null
        return try {
            val userId = resolveCurrentUserId() ?: return null
            val result = withContext(Dispatchers.IO) {
                apiClient.itemsApi.getItems(
                    userId = userId,
                    ids = trackIds,
                    sortBy = listOf(ItemSortBy.INDEX_NUMBER, ItemSortBy.NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                ).content
            }
            val tracks = result.items.orEmpty().ifEmpty { return null }
            buildResumeQueue(tracks)
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to fetch track names for bundle")
            AudioQueue(trackIds, 0, trackIds.indices.map { "Track ${it + 1}" })
        }
    }

    /**
     * Computes resume index/position from each track's [BaseItemDto.userData]. Works whether the
     * tracks were fetched via `parentId` or via an explicit `ids` list — `userData` is present
     * either way, unlike `parentId`, which Jellyfin often omits in recursive responses.
     */
    private fun buildResumeQueue(tracks: List<BaseItemDto>): AudioQueue {
        val ids = tracks.mapNotNull { it.id }
        val names = tracks.map { it.name ?: "Unknown" }
        val (resumeIndex, resumePosition) = AudiobookProgress.resumePoint(tracks)
        return AudioQueue(ids, resumeIndex, names, resumePosition)
    }

    /**
     * Load root views (top-level libraries) and show audiobook-relevant ones.
     * If the global flatten setting is enabled, loads a flat list of all audiobooks instead.
     */
    private fun loadRoot() {
        val globalFlattened = appPreferences.libraryGlobalFlattened
        lastGlobalFlattenState = globalFlattened
        if (globalFlattened) {
            loadGlobalFlat()
            return
        }

        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            folderStack.clear()
            _canGoUp.update { false }
            _title.update { "Library" }

            try {
                val userId = resolveCurrentUserId() ?: run {
                    _error.update { "User not found" }
                    _isLoading.update { false }
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    apiClient.userViewsApi.getUserViews(
                        userId = userId,
                        includeHidden = false,
                    ).content
                }

                val audiobookCollectionTypes = setOf(
                    CollectionType.BOOKS,
                )
                val views = result.items.orEmpty()
                    .filter { it.collectionType in audiobookCollectionTypes }
                    .ifEmpty { result.items.orEmpty() } // fall back to all libraries if server has no matching libraries
                    .sortedBy { it.name.orEmpty().lowercase() }

                _rawItems.update { views.map(LibraryItem::from) }
                Timber.d("Loaded ${views.size} library views")
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load root views")
                _error.update { e.message ?: "Failed to load library" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    /**
     * Loads all audiobooks from every [CollectionType.BOOKS] library into a single flat sorted
     * list, with no folder items visible.
     *
     * Uses [recursive] = true and groups returned audio files by parent folder. Files sharing a
     * parent folder with the same album tag are collapsed into a single [LibraryItem.AudiobookBundle].
     * Files that are direct children of the library root, or that have distinct album tags among
     * their siblings, are shown as individual entries.
     *
     * Results are cached in the Room database so subsequent loads (e.g. after a sort option change
     * or after returning from Settings) are instant. Pass [forceRefresh] = true from pull-to-refresh
     * to bypass the cache and re-fetch from Jellyfin.
     */
    private fun loadGlobalFlat(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            folderStack.clear()
            _canGoUp.update { false }
            _title.update { "All Audiobooks" }

            // Serve from cache immediately for a snappy paint, but fall through to a live
            // server fetch below regardless — cached progress/resume data goes stale as soon as
            // playback happens elsewhere, so every load revalidates in the background.
            val servedFromCache = !forceRefresh && serveGlobalFlatFromCache()

            try {
                val userId = resolveCurrentUserId() ?: run {
                    if (!servedFromCache) _error.update { "User not found" }
                    _isLoading.update { false }
                    return@launch
                }
                refreshGlobalFlatFromServer(userId, appPreferences.currentServerId)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load global flat view")
                // If we already painted cached data, keep showing it rather than replacing it
                // with an error — the cache just couldn't be revalidated this time (e.g. offline).
                if (!servedFromCache) _error.update { e.message ?: "Failed to load library" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    /** Paints from the in-memory or Room cache if available. Returns true if a cache hit occurred. */
    private suspend fun serveGlobalFlatFromCache(): Boolean {
        val inMemory = cachedFlatEntries
        if (inMemory != null) {
            _rawItems.update { inMemory.toLibraryItems(_sortOption.value) }
            return true
        }
        val serverId = appPreferences.currentServerId
        if (serverId != null) {
            val roomCached = withContext(Dispatchers.IO) { audiobookEntryDao.getAll(serverId) }
            if (roomCached.isNotEmpty()) {
                cachedFlatEntries = roomCached
                _rawItems.update { roomCached.toLibraryItems(_sortOption.value) }
                return true
            }
        }
        return false
    }

    /** Fetches every [CollectionType.BOOKS] library's audio files, flattens, and persists them. */
    private suspend fun refreshGlobalFlatFromServer(userId: UUID, serverId: Long?) {
        val views = withContext(Dispatchers.IO) {
            apiClient.userViewsApi.getUserViews(userId = userId, includeHidden = false).content
        }.items.orEmpty()
            .filter { it.collectionType in setOf(CollectionType.BOOKS) }
            .ifEmpty { emptyList() }

        val allItems = mutableListOf<BaseItemDto>()
        for (view in views) {
            val viewId = view.id ?: continue
            val result = withContext(Dispatchers.IO) {
                apiClient.itemsApi.getItems(
                    userId = userId,
                    parentId = viewId,
                    includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO),
                    fields = listOf(ItemFields.PATH),
                    sortBy = listOf(ItemSortBy.INDEX_NUMBER, ItemSortBy.NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    recursive = true,
                    limit = 2000,
                ).content
            }
            allItems.addAll(result.items.orEmpty())
        }

        val entries = AudiobookEntryBuilder.build(allItems, serverId ?: -1L)

        if (serverId != null) {
            withContext(Dispatchers.IO) {
                audiobookEntryDao.clearForServer(serverId)
                audiobookEntryDao.insertAll(entries)
            }
        }

        cachedFlatEntries = entries
        _rawItems.update { entries.toLibraryItems(_sortOption.value) }
        Timber.d("Loaded ${entries.size} audiobook entries in global flat view")
    }

    /**
     * Called from the Fragment's onResume to detect if the global flatten setting changed
     * while the user was in Settings, and reload if needed.
     */
    fun checkAndReloadIfSettingChanged() {
        if (folderStack.isEmpty() && appPreferences.libraryGlobalFlattened != lastGlobalFlattenState) {
            cachedFlatEntries = null // clear stale flat cache when switching modes
            loadRoot()
        }
    }

    /**
     * Refresh current view/folder.
     *
     * @param force When true in global-flat mode, bypasses the in-memory and Room caches and
     *   re-fetches from Jellyfin. Used by the SwipeRefreshLayout pull-to-refresh gesture.
     */
    fun refresh(force: Boolean = false) {
        val current = folderStack.lastOrNull()
        when {
            current != null -> loadChildren(current.first, current.second)
            appPreferences.libraryGlobalFlattened -> {
                if (force) cachedFlatEntries = null
                loadGlobalFlat(forceRefresh = force)
            }
            else -> loadRoot()
        }
    }

    /**
     * Navigate into a folder/view.
     */
    fun openFolder(folderId: UUID, folderName: String) {
        _filterQuery.update { "" }
        folderStack.addLast(folderId to folderName)
        _canGoUp.update { true }
        loadChildren(folderId, folderName)
    }

    /**
     * Navigate up (back) in the folder stack.
     */
    fun goUp(): Boolean {
        if (folderStack.isEmpty()) return false
        _filterQuery.update { "" }
        folderStack.removeLast()
        val current = folderStack.lastOrNull()
        return if (current == null) {
            loadRoot()
            true
        } else {
            _canGoUp.update { true }
            loadChildren(current.first, current.second)
            true
        }
    }

    /**
     * Change the sort order. Persists across sessions.
     * In global-flat mode, re-sorts the in-memory cache instantly without a network call.
     */
    fun setSortOption(option: SortOption) {
        _sortOption.update { option }
        appPreferences.librarySortOption = option.name
        val current = folderStack.lastOrNull()
        when {
            current != null -> loadChildren(current.first, current.second)
            appPreferences.libraryGlobalFlattened -> {
                val cached = cachedFlatEntries
                if (cached != null) {
                    _rawItems.update { cached.toLibraryItems(option) }
                } else {
                    loadGlobalFlat()
                }
            }
            else -> loadRoot()
        }
    }

    private fun loadChildren(folderId: UUID, folderName: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            _title.update { folderName }
            _canGoUp.update { folderStack.isNotEmpty() }

            try {
                val userId = resolveCurrentUserId() ?: run {
                    _error.update { "User not found" }
                    _isLoading.update { false }
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    apiClient.itemsApi.getItems(
                        userId = userId,
                        parentId = folderId,
                        includeItemTypes = listOf(
                            BaseItemKind.FOLDER,
                            BaseItemKind.COLLECTION_FOLDER,
                            BaseItemKind.AUDIO_BOOK,
                            BaseItemKind.AUDIO,
                            BaseItemKind.MUSIC_ALBUM,
                        ),
                        fields = listOf(ItemFields.PATH),
                        sortBy = listOf(_sortOption.value.sortBy),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        recursive = false,
                        limit = 500,
                    ).content
                }

                // Folders before files, preserving API sort order within each group
                val children = result.items.orEmpty().sortedBy { it.isFolder != true }
                _rawItems.update { children.map(LibraryItem::from) }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load items from folder $folderId")
                _error.update { e.message ?: "Failed to load folder" }
            } finally {
                _isLoading.update { false }
            }
        }
    }
}

/** Bundles the download/hidden filter inputs so [LibraryViewModel.items] only needs a 3-way combine. */
internal data class LibraryFilterState(
    val downloadedOnly: Boolean,
    val downloadedIds: Set<String>,
    val showHidden: Boolean,
    val hiddenIds: Set<String>,
)

/**
 * Applies the search query, "downloaded only", and hidden-item filters to the raw library list.
 *
 * A bundle passes the downloaded-only filter if *any* of its tracks are downloaded; other item
 * types (albums, folders) always pass since download status isn't tracked for them individually.
 * Hidden items are excluded unless [LibraryFilterState.showHidden] is set, in which case they're
 * shown alongside everything else so the user can unhide them.
 */
internal fun filterLibraryItems(
    items: List<LibraryItem>,
    query: String,
    filter: LibraryFilterState,
): List<LibraryItem> = items
    .filter { item ->
        query.isBlank() ||
            item.displayName.contains(query, ignoreCase = true) ||
            item.description.contains(query, ignoreCase = true)
    }
    .filter { item ->
        if (!filter.downloadedOnly) {
            true
        } else {
            when (item) {
                is LibraryItem.AudiobookBundle -> item.trackIds.any { it.toString() in filter.downloadedIds }
                is LibraryItem.AudioFile -> item.id.toString() in filter.downloadedIds
                else -> true
            }
        }
    }
    .filter { item -> filter.showHidden || item.id.toString() !in filter.hiddenIds }

// ---------------------------------------------------------------------------
// Entity → LibraryItem helpers
// ---------------------------------------------------------------------------

internal fun List<AudiobookEntryEntity>.toLibraryItems(sortOption: SortOption): List<LibraryItem.AudiobookBundle> {
    val sorted = when (sortOption) {
        SortOption.TITLE -> sortedBy { it.title.lowercase() }
        SortOption.AUTHOR -> sortedBy { it.author.orEmpty().lowercase() }
        SortOption.DATE_ADDED -> sortedByDescending { it.dateAdded }
        SortOption.LAST_PLAYED -> sortedByDescending { it.lastPlayed }
    }
    return sorted.map { it.toLibraryItem() }
}

internal fun AudiobookEntryEntity.toLibraryItem(): LibraryItem.AudiobookBundle {
    val trackUUIDs = trackIds.split(",").mapNotNull { segment ->
        segment.trim().takeIf { it.isNotEmpty() }?.let {
            try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null }
        }
    }
    // Stable DiffUtil ID: deterministic UUID hashed from the entity's string key for bundles,
    // or the file's own UUID for standalone entries.
    val stableId = if (isBundle) {
        UUID.nameUUIDFromBytes(id.toByteArray())
    } else {
        trackUUIDs.firstOrNull() ?: UUID.nameUUIDFromBytes(id.toByteArray())
    }
    val folderUUID = parentId?.let { try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null } }
    return LibraryItem.AudiobookBundle(
        id = stableId,
        displayName = title,
        description = author ?: "",
        imageItemId = imageItemId?.let { try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null } },
        imageTag = imageTag,
        runTimeTicks = runtimeTicks.takeIf { it > 0L },
        progressPercent = playedPercentage.takeIf { it > 0f },
        trackIds = trackUUIDs,
        isBundle = isBundle,
        parentFolderId = folderUUID,
    )
}
