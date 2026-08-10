package app.yomi.data.storage

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Follows the platform conventions rather than dropping a dot-directory in the
 * home folder: `%APPDATA%` on Windows, `~/Library/Application Support` on macOS
 * and `$XDG_DATA_HOME` (or its documented default) elsewhere.
 */
actual fun defaultDataDirectory(appName: String): Path {
    val home = System.getProperty("user.home") ?: "."
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> {
            val appData = System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
            "$appData\\$appName".toPath()
        }

        os.contains("mac") || os.contains("darwin") ->
            "$home/Library/Application Support/$appName".toPath()

        else -> {
            val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: "$home/.local/share"
            "$dataHome/${appName.lowercase()}".toPath()
        }
    }
}

actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
