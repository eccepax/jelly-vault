package org.jellyfin.mobile.livetv.app

import org.jellyfin.mobile.common.app.AppPreferences
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val liveAppModule = module {
    single { AppPreferences(androidApplication()) }
}
