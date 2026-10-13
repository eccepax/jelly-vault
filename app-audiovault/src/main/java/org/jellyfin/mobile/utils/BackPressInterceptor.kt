package org.jellyfin.mobile.utils

import androidx.fragment.app.Fragment
import org.jellyfin.mobile.MainActivity

/**
 * Additional hook for handling back presses in [Fragments][Fragment] (see [onInterceptBackPressed]).
 *
 * The [MainActivity] determines the currently visible fragment and passes back press events to it
 * via [onInterceptBackPressed], allowing fragments to intercept navigation before the default
 * back-stack pop behaviour fires.
 */
interface BackPressInterceptor {
    /**
     * Called when a back press is performed while this fragment is currently visible.
     *
     * @return `true` if the event was intercepted by the fragment,
     *         `false` if the back press was not handled by the fragment.
     *         The latter will result in a default action that closes the fragment
     * @see MainActivity.onBackPressedCallback
     */
    fun onInterceptBackPressed(): Boolean = false
}
