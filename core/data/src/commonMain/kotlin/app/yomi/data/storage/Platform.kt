package app.yomi.data.storage

import okio.FileSystem
import okio.Path

/** The per-user directory Yomi stores its data in, following each OS's conventions. */
expect fun defaultDataDirectory(appName: String = "Yomi"): Path

/** The file system to use on this platform. */
expect fun platformFileSystem(): FileSystem
