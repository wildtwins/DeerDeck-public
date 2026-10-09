package com.wildtwins.deerdeck.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveDocumentProviderTest {
    @Test
    fun recognizesGoogleDrivePackage() {
        assertTrue(
            GoogleDriveDocumentProvider.matchesProvider(
                providerPackage = GoogleDriveDocumentProvider.PackageName,
                authority = "alternate.authority",
            ),
        )
    }

    @Test
    fun recognizesKnownGoogleDriveAuthority() {
        assertTrue(
            GoogleDriveDocumentProvider.matchesProvider(
                providerPackage = null,
                authority = GoogleDriveDocumentProvider.DefaultAuthority,
            ),
        )
    }

    @Test
    fun rejectsPhoneStorageProvider() {
        assertFalse(
            GoogleDriveDocumentProvider.matchesProvider(
                providerPackage = "com.google.android.documentsui",
                authority = "com.android.externalstorage.documents",
            ),
        )
    }
}
