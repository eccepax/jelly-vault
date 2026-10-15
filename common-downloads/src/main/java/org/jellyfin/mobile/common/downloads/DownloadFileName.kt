package org.jellyfin.mobile.common.downloads

import java.security.MessageDigest

private val PLAIN_NAME = Regex("[A-Za-z0-9_-]{1,64}")

/**
 * The file name to keep a download called [name] under. Names come from the server, so anything but
 * a plain id (letters, digits, `-`, `_`) is replaced by its SHA-256: it can't leave the downloads
 * folder, and two different names never share a file.
 */
fun downloadFileName(name: String): String =
    if (PLAIN_NAME.matches(name)) {
        name
    } else {
        MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
    }
