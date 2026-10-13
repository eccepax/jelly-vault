package org.jellyfin.mobile.books.utils

import android.util.Log
import org.jellyfin.mobile.books.BuildConfig
import timber.log.Timber

class BookTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (BuildConfig.DEBUG || priority >= Log.INFO) super.log(priority, tag, message, t)
    }
}
