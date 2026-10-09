package com.wildtwins.deerdeck.data.export

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.wildtwins.deerdeck.data.backup.BackupArchive
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

object DataPackageExporter {
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    fun write(
        context: Context,
        backupArchive: BackupArchive,
        csvExport: CsvExport,
        media: List<ObservationPhotoEntity>,
        createdAt: Long,
    ): Uri = writePackage(
        context = context,
        backupArchive = backupArchive,
        csvExport = csvExport,
        media = media,
        createdAt = createdAt,
        privateShare = false,
    )

    fun writePrivateShare(
        context: Context,
        csvExport: CsvExport,
        media: List<ObservationPhotoEntity>,
        createdAt: Long,
    ): Uri = writePackage(
        context = context,
        backupArchive = null,
        csvExport = csvExport,
        media = media,
        createdAt = createdAt,
        privateShare = true,
    )

    private fun writePackage(
        context: Context,
        backupArchive: BackupArchive?,
        csvExport: CsvExport,
        media: List<ObservationPhotoEntity>,
        createdAt: Long,
        privateShare: Boolean,
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val packagePrefix = if (privateShare) {
            "withrow-deerdeck-private-share"
        } else {
            "withrow-deerdeck-data-package"
        }
        val file = File(
            exportDir,
            "$packagePrefix-${fileTimestamp.format(Instant.ofEpochMilli(createdAt))}.zip",
        )

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            backupArchive?.let { archive -> zip.putTextEntry(archive.fileName, archive.content) }
            zip.putTextEntry(csvExport.fileName, csvExport.content)
            val mediaManifest = JSONArray()
            media.forEach { item ->
                val extension = item.fileExtension(context)
                val entryName = "media/${item.id}-${item.capturedAt}.$extension"
                val stagedFile = File(exportDir, "staged-media-${item.id}.$extension")
                var locationMetadataStripped = false
                val stagedMedia = runCatching {
                    item.openInputStream(context).use { input ->
                        stagedFile.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    if (privateShare && item.mediaType.equals("photo", ignoreCase = true)) {
                        locationMetadataStripped = stagedFile.stripPhotoLocationMetadata()
                    }
                    stagedFile
                }.onFailure { stagedFile.delete() }.getOrNull()
                val included = if (stagedMedia == null) {
                    false
                } else {
                    try {
                        zip.putNextEntry(ZipEntry(entryName))
                        stagedMedia.inputStream().buffered().use { input -> input.copyTo(zip) }
                        true
                    } finally {
                        runCatching { zip.closeEntry() }
                        stagedMedia.delete()
                    }
                }
                mediaManifest.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("entry", entryName)
                        .put("included", included)
                        .put("mediaType", item.mediaType)
                        .put("locationMetadataStripped", locationMetadataStripped),
                )
            }
            zip.putTextEntry("media-manifest.json", mediaManifest.toString(2))
            zip.putTextEntry(
                "README.txt",
                if (privateShare) {
                    """
                    Withrow DeerDeck private share package
                    Created: ${Instant.ofEpochMilli(createdAt)}

                    Files:
                    - ${csvExport.fileName}: privacy-filtered observation export.
                    - media/: readable session media copies. GPS EXIF is removed from supported photos.
                    - media-manifest.json: packaged media IDs and metadata-removal status.

                    This sharing package intentionally does not contain the restorable DeerDeck database backup.
                    Use Export full archive in Settings for a complete disaster-recovery package.
                    """.trimIndent()
                } else {
                    requireNotNull(backupArchive)
                    """
                Withrow DeerDeck data package
                Created: ${Instant.ofEpochMilli(createdAt)}

                Files:
                - ${backupArchive.fileName}: full JSON database backup with sessions, wildlife buttons, observations, media references, settings metadata, counts, and checksums.
                - ${csvExport.fileName}: spreadsheet-friendly observation export.
                - media/: copies of every photo or video DeerDeck could still read when this package was created.
                - media-manifest.json: maps database media IDs to packaged files and records unavailable items.

                Media note:
                Camera-roll media remains in the phone gallery. On restore, packaged media is copied into DeerDeck's app media folder. Any unavailable item keeps its original Android reference.

                Backup file SHA-256:
                ${backupArchive.fileChecksumSha256}
                """.trimIndent()
                },
            )
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun File.stripPhotoLocationMetadata(): Boolean {
        return runCatching {
            val exif = ExifInterface(absolutePath)
            LocationExifTags.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
            true
        }.getOrDefault(false)
    }

    private fun ZipOutputStream.putTextEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ObservationPhotoEntity.openInputStream(context: Context): InputStream {
        val parsedUri = Uri.parse(uri)
        return if (parsedUri.scheme.equals("file", ignoreCase = true)) {
            File(requireNotNull(parsedUri.path)).inputStream().buffered()
        } else {
            context.contentResolver.openInputStream(parsedUri)?.buffered()
                ?: throw IllegalStateException("Media is no longer readable: $uri")
        }
    }

    private fun ObservationPhotoEntity.fileExtension(context: Context): String {
        val parsedUri = Uri.parse(uri)
        val mimeExtension = runCatching { context.contentResolver.getType(parsedUri) }.getOrNull()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val pathExtension = parsedUri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
        return mimeExtension ?: pathExtension ?: when (mediaType.lowercase(Locale.US)) {
            "video" -> "mp4"
            "audio" -> "m4a"
            else -> "jpg"
        }
    }

    private val LocationExifTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    )
}
