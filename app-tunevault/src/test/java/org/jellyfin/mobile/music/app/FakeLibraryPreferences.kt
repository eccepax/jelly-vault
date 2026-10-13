package org.jellyfin.mobile.music.app

import org.jellyfin.mobile.music.domain.AlbumSort

class FakeLibraryPreferences(override var albumSort: AlbumSort = AlbumSort.TITLE) : LibraryPreferences
