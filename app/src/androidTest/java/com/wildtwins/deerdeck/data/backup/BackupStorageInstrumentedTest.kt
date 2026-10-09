package com.wildtwins.deerdeck.data.backup

import android.content.ContentUris
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.BackupDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupStorageInstrumentedTest {
    @Test
    fun bothKeepsVerifiedPhoneBackupWhenDriveIsUnavailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = DeerDeckBackupJson.build(
            createdAt = System.currentTimeMillis(),
            appVersionName = "instrumented-test",
            appVersionCode = 1,
            themeMode = AppThemeMode.System,
            wildlife = emptyList(),
            sessions = emptyList(),
            observations = emptyList(),
            media = emptyList(),
        )

        try {
            val result = DeerDeckBackupStorage.writeVerified(
                context = context,
                archive = archive,
                destination = BackupDestination.Both,
                googleDriveFolderUri = null,
            )

            assertEquals(archive.fileChecksumSha256, result.fileChecksumSha256)
            assertTrue(result.displayPath.startsWith("Download/Withrow DeerDeck/"))
            assertTrue(result.warnings.any { it.startsWith("Google Drive:") })
        } finally {
            deleteTestBackup(archive.fileName)
        }
    }

    private fun deleteTestBackup(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn),
                )
                resolver.delete(uri, null, null)
            }
        }
    }
}
