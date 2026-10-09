package com.wildtwins.deerdeck.ui.observe

import com.wildtwins.deerdeck.data.settings.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveBackupStatusTest {
    @Test
    fun detectsStoredDriveFailureEvenWhenPermissionStillExists() {
        val message = "The selected Google Drive backup folder is not writable."

        assertEquals(message, BackupStatus(lastSucceeded = false, message = message).googleDriveIssue())
    }

    @Test
    fun detectsPartialBackupDriveWarning() {
        val warning = "Google Drive: Folder access expired."

        assertEquals(warning, BackupStatus(lastSucceeded = true, warning = warning).googleDriveIssue())
    }

    @Test
    fun ignoresHealthyAndPhoneOnlyStatuses() {
        assertNull(BackupStatus(lastSucceeded = true, message = "Backup verified").googleDriveIssue())
        assertNull(BackupStatus(lastSucceeded = false, message = "Phone backup failed.").googleDriveIssue())
    }
}
