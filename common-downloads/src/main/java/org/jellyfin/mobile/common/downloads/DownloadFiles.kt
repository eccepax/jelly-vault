package org.jellyfin.mobile.common.downloads

import java.io.File

/** Where each item's file lives: one plain file per item id in [root]. */
class DownloadFiles(val root: File) {
    /** The file [id] is (or will be) downloaded to. */
    fun fileOf(id: String): File = File(root, downloadFileName(id))

    /** [id]'s file if it is complete; a download in progress is kept elsewhere until it finishes. */
    fun finished(id: String): File? = fileOf(id).takeIf { it.isFile }
}
