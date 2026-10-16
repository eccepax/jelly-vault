@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

inline fun Context.toast(@StringRes text: Int, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun LifecycleOwner.runOnUiThread(noinline block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.Main, block = block)
}

fun View.applyWindowInsetsAsMargins() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        updateLayoutParams<MarginLayoutParams> {
            setMargins(insets.left, insets.top, insets.right, insets.bottom)
        }
        windowInsets
    }
}
