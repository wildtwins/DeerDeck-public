package com.wildtwins.deerdeck.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

data class RestoreResult(
    val counts: BackupCounts,
    val settings: BackupSettings,
    val safetyBackupPath: String,
    val restoredMedia: Int,
    val unavailableMedia: Int,
)

data class PreparedRestore(
    val dataset: BackupDataset,
    val mediaFiles: Map<Long, File>,
    private val temporaryDirectory: File,
) : Closeable {
    override fun close() {
        temporaryDirectory.deleteRecursively()
    }

    fun withPersistedMedia(context: Context): Pair<BackupDataset, Int> {
        var restoredCount = 0
        val restoredMedia = dataset.media.map { item ->
            val source = mediaFiles[item.id] ?: return@map item
            val baseDirectory = when (item.mediaType.lowercase()) {
                "video" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                "audio" -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                else -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            } ?: context.filesDir
            val directory = File(baseDirectory, "DeerDeck/restored").apply { mkdirs() }
            val extension = source.extension.takeIf { it.isNotBlank() } ?: "bin"
            val destination = File(directory, "${item.id}-${item.capturedAt}.$extension")
            source.copyTo(destination, overwrite = true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination,
            )
            restoredCount += 1
            item.copy(uri = uri.toString())
        }
        return dataset.copy(media = restoredMedia) to restoredCount
    }
}

object DeerDeckRestorePackage {
    private val MediaEntryPattern = Regex("^media/(\\d+)-[^/]+$")

    fun prepare(context: Context, sourceUri: Uri): PreparedRestore {
        val temporaryDirectory = File(
            context.cacheDir,
            "restore/${UUID.randomUUID()}",
        ).apply { mkdirs() }
        try {
            val incoming = File(temporaryDirectory, "incoming-package")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                incoming.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: throw IOException("Restore failed: the selected file could not be opened.")

            return if (incoming.isZipFile()) {
                prepareZip(incoming, temporaryDirectory)
            } else {
                val content = incoming.readText(Charsets.UTF_8)
                PreparedRestore(
                    dataset = DeerDeckBackupJson.parse(content),
                    mediaFiles = emptyMap(),
                    temporaryDirectory = temporaryDirectory,
                )
            }
        } catch (error: Throwable) {
            temporaryDirectory.deleteRecursively()
            throw error
        }
    }

    private fun prepareZip(file: File, temporaryDirectory: File): PreparedRestore {
        var backupContent: String? = null
        val mediaFiles = mutableMapOf<Long, File>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (name.contains("../") || name.startsWith('/')) {
                    throw IOException("Restore failed: unsafe ZIP entry.")
                }
                if (!entry.isDirectory && name.startsWith("withrow-deerdeck-backup-") && name.endsWith(".json")) {
                    backupContent = zip.bufferedReader(Charsets.UTF_8).readText()
                } else if (!entry.isDirectory) {
                    val mediaId = MediaEntryPattern.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()
                    if (mediaId != null) {
                        val extension = name.substringAfterLast('.', "bin")
                            .takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) }
                            ?: "bin"
                        val destination = File(temporaryDirectory, "media-$mediaId.$extension")
                        destination.outputStream().buffered().use { output -> zip.copyTo(output) }
                        mediaFiles[mediaId] = destination
                    }
                }
                zip.closeEntry()
            }
        }
        val content = backupContent ?: throw IOException("Restore failed: ZIP does not contain a DeerDeck backup.")
        return PreparedRestore(
            dataset = DeerDeckBackupJson.parse(content),
            mediaFiles = mediaFiles,
            temporaryDirectory = temporaryDirectory,
        )
    }

    private fun File.isZipFile(): Boolean {
        if (length() < 4) return false
        inputStream().use { input ->
            val signature = ByteArray(4)
            if (input.read(signature) != signature.size) return false
            return signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte() &&
                signature[2] in setOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte()) &&
                signature[3] in setOf(0x04.toByte(), 0x06.toByte(), 0x08.toByte())
        }
    }
}
