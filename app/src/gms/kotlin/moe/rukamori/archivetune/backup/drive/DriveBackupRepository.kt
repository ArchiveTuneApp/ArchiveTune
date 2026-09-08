package moe.rukamori.archivetune.backup.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.backup.BackupArchiveRepository
import moe.rukamori.archivetune.db.InternalDatabase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

@Singleton
class DriveBackupRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun createTemporaryArchive(forRestore: Boolean = false): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "drive-backups")
        if (!directory.isDirectory && !directory.mkdirs()) throw DriveBackupException(DriveBackupFailure.STORAGE)
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > TimeUnit.DAYS.toMillis(1) }
            ?.forEach { it.delete() }
        if (forRestore) File(directory, "restore.backup") else File.createTempFile("archive-", ".backup", directory)
    }

    suspend fun deleteTemporaryArchive(archive: File) = withContext(Dispatchers.IO) {
        if (archive.exists() && !archive.delete()) throw DriveBackupException(DriveBackupFailure.STORAGE)
    }

    suspend fun latest(token: String): DriveBackupMetadata? = withContext(Dispatchers.IO) {
        val url = "$FILES_URL".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files($METADATA_FIELDS)")
            .build()
        execute(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).use { response ->
            val files = JSONObject(requireNotNull(response.body).string()).getJSONArray("files")
            if (files.length() == 0) null else metadata(files.getJSONObject(0))
        }
    }

    suspend fun upload(token: String, archive: File): DriveBackupMetadata = withContext(Dispatchers.IO) {
        if (archive.length() !in 1..MAX_ARCHIVE_BYTES) throw DriveBackupException(DriveBackupFailure.STORAGE)
        val existing = latest(token)
        val metadataJson = JSONObject().put("name", FILE_NAME).put("mimeType", "application/zip")
        if (existing == null) metadataJson.put("parents", JSONArray().put("appDataFolder"))
        val url = UPLOAD_URL.toHttpUrl().newBuilder().apply {
            if (existing != null) addPathSegment(existing.id)
        }.addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", METADATA_FIELDS).build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", "application/zip")
            .header("X-Upload-Content-Length", archive.length().toString())
            .method(if (existing == null) "POST" else "PATCH", metadataJson.toString().toRequestBody(JSON_TYPE))
            .build()
        val session = execute(request).use {
            it.header("Location")?.toHttpUrl() ?: throw DriveBackupException(DriveBackupFailure.UNKNOWN)
        }
        if (session.scheme != "https" || session.host != "www.googleapis.com") {
            throw DriveBackupException(DriveBackupFailure.UNKNOWN)
        }
        val checksum = checksum(archive)
        execute(
            Request.Builder().url(session)
                .header("Authorization", "Bearer $token")
                .put(archive.asRequestBody(ZIP_TYPE)).build(),
        ).use {
            val uploaded = metadata(JSONObject(requireNotNull(it.body).string()))
            if (uploaded.size != archive.length() || !uploaded.checksum.equals(checksum, ignoreCase = true)) {
                throw DriveBackupException(DriveBackupFailure.INVALID)
            }
            uploaded
        }
    }

    suspend fun download(token: String, backup: DriveBackupMetadata, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (backup.size !in 1..MAX_ARCHIVE_BYTES) throw DriveBackupException(DriveBackupFailure.STORAGE)
            if (context.cacheDir.usableSpace < backup.size * 2 + STORAGE_RESERVE_BYTES) {
                throw DriveBackupException(DriveBackupFailure.STORAGE)
            }
            val archive = createTemporaryArchive(forRestore = true)
            var complete = false
            try {
                val url = FILES_URL.toHttpUrl().newBuilder().addPathSegment(backup.id)
                    .addQueryParameter("alt", "media").build()
                execute(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).use { response ->
                    requireNotNull(response.body).byteStream().use { input ->
                        archive.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            var lastProgress = -1
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > backup.size) throw DriveBackupException(DriveBackupFailure.INVALID)
                                output.write(buffer, 0, count)
                                val progress = (copied * 100 / backup.size).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                if (archive.length() != backup.size || !checksum(archive).equals(backup.checksum, ignoreCase = true)) {
                    throw DriveBackupException(DriveBackupFailure.INVALID)
                }
                validateArchive(archive)
                complete = true
                archive
            } finally {
                if (!complete) archive.delete()
            }
        }

    private suspend fun validateArchive(archive: File) {
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            val names = HashSet<String>()
            var expandedSize = 0L
            val buffer = ByteArray(64 * 1024)
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                if (entry.name !in ARCHIVE_ENTRIES || !names.add(entry.name) || entry.isDirectory) {
                    throw DriveBackupException(DriveBackupFailure.INVALID)
                }
                zip.getInputStream(entry).use { input ->
                    var entrySize = 0L
                    if (entry.name == InternalDatabase.DB_NAME) {
                        val header = ByteArray(16)
                        java.io.DataInputStream(input).readFully(header)
                        if (!header.contentEquals(SQLITE_HEADER)) throw DriveBackupException(DriveBackupFailure.INVALID)
                        expandedSize += header.size
                    }
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        expandedSize += count
                        entrySize += count
                        if (entry.name == BackupArchiveRepository.SETTINGS_XML_FILENAME && entrySize > MAX_SETTINGS_BYTES) {
                            throw DriveBackupException(DriveBackupFailure.INVALID)
                        }
                        if (expandedSize > MAX_EXPANDED_BYTES ||
                            expandedSize + STORAGE_RESERVE_BYTES > context.filesDir.usableSpace
                        ) throw DriveBackupException(DriveBackupFailure.STORAGE)
                    }
                }
            }
            if (InternalDatabase.DB_NAME !in names || BackupArchiveRepository.SETTINGS_XML_FILENAME !in names) {
                throw DriveBackupException(DriveBackupFailure.INVALID)
            }
            zip.getInputStream(zip.getEntry(BackupArchiveRepository.SETTINGS_XML_FILENAME)).use { input ->
                val parser = android.util.Xml.newPullParser()
                parser.setInput(input, Charsets.UTF_8.name())
                if (parser.nextTag() != org.xmlpull.v1.XmlPullParser.START_TAG || parser.name != "ArchiveTuneBackup") {
                    throw DriveBackupException(DriveBackupFailure.INVALID)
                }
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    private suspend fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun metadata(json: JSONObject): DriveBackupMetadata = DriveBackupMetadata(
        id = json.getString("id"),
        modifiedAt = Instant.parse(json.getString("modifiedTime")).toEpochMilli(),
        size = json.getString("size").toLong(),
        checksum = json.getString("md5Checksum"),
    )

    private suspend fun execute(request: Request): Response {
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(DriveBackupException(DriveBackupFailure.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        if (response.isSuccessful) return response
        val failure = response.use {
            val error = it.body?.string().orEmpty()
            when {
                it.code == 401 -> DriveBackupFailure.AUTHORIZATION
                it.code == 404 -> DriveBackupFailure.MISSING
                "storageQuotaExceeded" in error -> DriveBackupFailure.QUOTA
                "accessNotConfigured" in error || "SERVICE_DISABLED" in error -> DriveBackupFailure.CONFIGURATION
                it.code == 429 || it.code >= 500 || "rateLimitExceeded" in error -> DriveBackupFailure.NETWORK
                it.code == 403 -> DriveBackupFailure.AUTHORIZATION
                else -> DriveBackupFailure.UNKNOWN
            }
        }
        throw DriveBackupException(failure)
    }

    private companion object {
        const val FILE_NAME = "ArchiveTune.backup"
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val METADATA_FIELDS = "id,modifiedTime,size,md5Checksum"
        const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        const val STORAGE_RESERVE_BYTES = 32L * 1024 * 1024
        const val MAX_SETTINGS_BYTES = 8L * 1024 * 1024
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val ZIP_TYPE = "application/zip".toMediaType()
        val ARCHIVE_ENTRIES = setOf(
            BackupArchiveRepository.SETTINGS_XML_FILENAME,
            InternalDatabase.DB_NAME,
            "${InternalDatabase.DB_NAME}-wal",
            "${InternalDatabase.DB_NAME}-shm",
            "${InternalDatabase.DB_NAME}-journal",
        )
    }
}
