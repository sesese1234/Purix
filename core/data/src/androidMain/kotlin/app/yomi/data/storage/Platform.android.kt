package app.yomi.data.storage

import android.content.Context
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Android has no ambient "home directory", so the application supplies its
 * private files directory once at startup and everything below reads it from
 * here. Failing loudly beats silently writing to the wrong place.
 */
object AndroidStorage {
    private var directory: Path? = null

    fun initialize(context: Context) {
        directory = context.filesDir.absolutePath.toPath()
    }

    internal fun require(): Path = directory
        ?: error("AndroidStorage.initialize(context) must run before the data layer is built")
}

actual fun defaultDataDirectory(appName: String): Path = AndroidStorage.require()

actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
