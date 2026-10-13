package org.jellyfin.mobile.music.catalog

data class Account(val userName: String, val serverName: String, val serverUrl: String)

/** Who is signed in, and where. */
interface AccountInfo {
    suspend fun current(): Account
}
