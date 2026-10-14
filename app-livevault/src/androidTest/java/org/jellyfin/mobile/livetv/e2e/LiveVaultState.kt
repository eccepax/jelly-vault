package org.jellyfin.mobile.livetv.e2e

import org.jellyfin.mobile.livetv.data.LiveDatabase
import org.jellyfin.mobile.livetv.viewing.ViewingMemory
import org.koin.core.context.GlobalContext

/** Signed out with nothing remembered: where every LiveVault e2e test starts. */
fun clearLiveVaultState() {
    val koin = GlobalContext.get()
    koin.get<LiveDatabase>().clearAllTables()
    koin.get<ViewingMemory>().forget()
}
