package dev.gitdroid.app.core.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.repo.FileContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Downloads files and repository archives to shared storage.
 *
 * On Android 10 and newer the file goes through [MediaStore], so it lands in
 * the user's Downloads folder where every other app can see it. On older
 * releases it goes to the public Downloads directory directly.
 */
class Downloader(
    private val context: Context,
    private val api: GitHubApi,
) {

    /** The result of a download. */
    sealed interface Result {
        data class Success(val uri: Uri, val displayName: String) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Downloads a file that was read through the Contents API.
     */
    suspend fun downloadFile(content: FileContent, owner: String, repo: String): Result =
        withContext(Dispatchers.IO) {
            val name = content.name.ifBlank { content.path.substringAfterLast('/') }
            try {
                val bytes = if (content.downloadUrl.isNotBlank()) {
                    fetchRaw(content.downloadUrl)
                } else {
                    content.content.toByteArray(Charsets.UTF_8)
                }
                save(name, bytes)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "The download failed")
            }
        }

    /**
     * Downloads the whole repository as a zip archive.
     */
    suspend fun downloadRepositoryArchive(
        owner: String,
        repo: String,
        ref: String = "HEAD",
    ): Result = withContext(Dispatchers.IO) {
        val name = "$repo-$ref.zip"
        try {
            val url = "https://api.github.com/repos/$owner/$repo/zipball/$ref"
            val bytes = fetchRaw(url)
            save(name, bytes)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "The download failed")
        }
    }

    /** Fetches a URL with the user's token attached. */
    private fun fetchRaw(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3.raw")
            .header("User-Agent", "GitDroid/1.0 (Android)")
            .build()

        api.httpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub returned HTTP ${response.code}")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** Writes the bytes into shared storage and returns the resulting uri. */
    private fun save(name: String, bytes: ByteArray): Result {
        val safeName = name.replace(Regex("[/\\\\:]"), "_")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(safeName))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return Result.Failure("Could not create the download")

            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return Result.Failure("Could not write the download")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return Result.Success(uri, safeName)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return Result.Failure(e.message ?: "The download failed")
            }
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Result.Success(uri, safeName)
    }

    /** A share intent for a downloaded file. */
    fun shareIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "zip" -> "application/zip"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "txt" -> "text/plain"
        "kt", "kts" -> "text/x-kotlin"
        "java" -> "text/x-java"
        "js", "jsx", "ts", "tsx" -> "text/javascript"
        "py" -> "text/x-python"
        "go" -> "text/x-go"
        "rs" -> "text/rust"
        "sh", "bash" -> "text/x-shellscript"
        "yml", "yaml" -> "text/yaml"
        "xml" -> "text/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        else -> "application/octet-stream"
    }
}
