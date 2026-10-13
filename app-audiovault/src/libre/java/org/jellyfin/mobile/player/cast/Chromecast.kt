package org.jellyfin.mobile.player.cast

import android.app.Activity
import org.json.JSONArray

class Chromecast : IChromecast {
    override fun initializePlugin(activity: Activity) = Unit
    override fun execute(action: String, args: JSONArray, cbContext: CastCallback) = false
    override fun destroy() = Unit
}
