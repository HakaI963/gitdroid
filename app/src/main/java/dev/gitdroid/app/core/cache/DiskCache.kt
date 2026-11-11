package dev.gitdroid.app.core.cache

import android.content.Context
import dev.gitdroid.app.core.api.GitHubJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.File

/**
 * A tiny JSON-file cache.
 *
 * The point of this class is the app's central promise: **a screen never
 * starts empty**. When a repo list, a file tree or an issue list has been
 * seen before, it is on disk and can be shown in a few milliseconds, then
 * reconciled against the network in the background.
 *
 * Entries carry a timestamp so the UI can tell the user how stale the data
 * is instead of silently pretending it is live.
 */
class DiskCache(context: Context) {

    private val root: File = File(context.applicationContext.filesDir, "cache").apply { mkdirs() }

    /** A cached value together with when it was written. */
    data class Entry<T>(val value: T, val savedAt: Long)

    /** Reads a cached value, or `null` when it is missing or unreadable. */
    suspend fun <T> read(key: String, deserializer: KSerializer<T>): Entry<T>? =
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!file.exists()) return@withContext null
            runCatching {
                val envelope = GitHubJson.decodeFromString(
                    CacheEnvelope.serializer(),
                    file.readText(),
                )
                val payload = GitHubJson.decodeFromJsonElement(deserializer, envelope.payload)
                Entry(payload, envelope.savedAt)
            }.getOrNull()
        }

    /** Reads a cached value using the reified serializer of [T]. */
    suspend inline fun <reified T> read(key: String): Entry<T>? =
        read(key, serializer())

    /** Writes a value to the cache. Failures are ignored: a cache miss is cheap. */
    suspend fun <T> write(key: String, value: T, serializer: KSerializer<T>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = GitHubJson.encodeToJsonElement(serializer, value)
                val envelope = CacheEnvelope(payload, System.currentTimeMillis())
                val file = fileFor(key)
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeText(GitHubJson.encodeToString(CacheEnvelope.serializer(), envelope))
                temp.renameTo(file)
            }
        }
    }

    /** Writes a value to the cache using the reified serializer of [T]. */
    suspend inline fun <reified T> write(key: String, value: T) = write(key, value, serializer())

    /** Removes one entry. */
    suspend fun evict(key: String) = withContext(Dispatchers.IO) {
        fileFor(key).delete()
        Unit
    }

    /** Empties the whole cache. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    /** Total bytes currently held on disk. */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Removes entries older than [maxAgeMillis]. */
    suspend fun prune(maxAgeMillis: Long) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        root.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
        Unit
    }

    @PublishedApi
    internal fun fileFor(key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, safe.ifEmpty { "entry" } + ".json")
    }
}

/** The on-disk envelope: the payload plus the time it was captured. */
@kotlinx.serialization.Serializable
internal data class CacheEnvelope(
    val payload: kotlinx.serialization.json.JsonElement,
    val savedAt: Long,
)
