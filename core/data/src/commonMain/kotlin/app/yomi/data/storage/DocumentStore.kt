package app.yomi.data.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/** Where Yomi keeps its files, and what they are called. */
class YomiPaths(val root: Path) {
    val settings: Path get() = root / "settings.json"
    val catalog: Path get() = root / "catalog.json"
    val journalDir: Path get() = root / "days"

    /** One file per month keeps writes small and backups readable. */
    fun journalShard(year: Int, month: Int): Path =
        journalDir / "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}.json"
}

/** The JSON dialect used for every file the app writes. */
val YomiJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    allowStructuredMapKeys = true
}

/**
 * Reads and writes serialisable documents on disk.
 *
 * Writes go to a temporary file first and are then moved into place, so a crash
 * or a full disk can never leave a half-written journal behind. Reads that hit
 * a corrupt file fall back to the caller's default instead of taking the app
 * down with them — a broken settings file must not cost you your history.
 */
class DocumentStore(
    private val fileSystem: FileSystem,
    private val json: Json = YomiJson,
    private val onError: (String, Throwable) -> Unit = { _, _ -> }
) {

    fun <T> read(path: Path, serializer: KSerializer<T>): T? {
        return try {
            if (!fileSystem.exists(path)) return null
            val text = fileSystem.read(path) { readUtf8() }
            if (text.isBlank()) null else json.decodeFromString(serializer, text)
        } catch (e: IOException) {
            onError("read:$path", e)
            null
        } catch (e: SerializationFailure) {
            onError("decode:$path", e)
            null
        } catch (e: Exception) {
            onError("decode:$path", e)
            null
        }
    }

    fun <T> write(path: Path, serializer: KSerializer<T>, value: T): Boolean {
        return try {
            path.parent?.let { fileSystem.createDirectories(it) }
            val text = json.encodeToString(serializer, value)
            val temporary = path.parent?.let { it / "${path.name}.tmp" } ?: path
            fileSystem.write(temporary) { writeUtf8(text) }
            if (temporary != path) fileSystem.atomicMove(temporary, path)
            true
        } catch (e: IOException) {
            onError("write:$path", e)
            false
        } catch (e: Exception) {
            onError("encode:$path", e)
            false
        }
    }

    fun delete(path: Path) {
        try {
            if (fileSystem.exists(path)) fileSystem.delete(path)
        } catch (e: IOException) {
            onError("delete:$path", e)
        }
    }

    fun listFiles(directory: Path, extension: String = ".json"): List<Path> = try {
        if (!fileSystem.exists(directory)) {
            emptyList()
        } else {
            fileSystem.list(directory).filter { it.name.endsWith(extension) }.sortedBy { it.name }
        }
    } catch (e: IOException) {
        onError("list:$directory", e)
        emptyList()
    }

    fun ensureRoot(root: Path) {
        try {
            fileSystem.createDirectories(root)
        } catch (e: IOException) {
            onError("mkdir:$root", e)
        }
    }
}

/** Marker used so decoding problems read clearly in logs. */
class SerializationFailure(message: String, cause: Throwable?) : Exception(message, cause)
