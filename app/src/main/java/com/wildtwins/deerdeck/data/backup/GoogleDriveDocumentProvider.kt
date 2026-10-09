package com.wildtwins.deerdeck.data.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract

object GoogleDriveDocumentProvider {
    internal const val PackageName = "com.google.android.apps.docs"
    internal const val DefaultAuthority = "com.google.android.apps.docs.storage"

    fun isGoogleDriveTreeUri(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false

        val providerPackage = uri.authority?.let { authority ->
            context.packageManager.resolveContentProvider(authority, PackageManager.MATCH_ALL)?.packageName
        }
        return matchesProvider(providerPackage, uri.authority)
    }

    fun hasPersistedReadWriteAccess(context: Context, uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
    }

    fun initialPickerUri(context: Context): Uri? {
        val authority = findProviderAuthority(context) ?: return null
        return DocumentsContract.buildRootsUri(authority)
    }

    internal fun matchesProvider(providerPackage: String?, authority: String?): Boolean {
        return providerPackage == PackageName || authority == DefaultAuthority
    }

    private fun findProviderAuthority(context: Context): String? {
        val packageManager = context.packageManager
        val knownProvider = packageManager.resolveContentProvider(DefaultAuthority, PackageManager.MATCH_ALL)
        if (knownProvider?.packageName == PackageName) return DefaultAuthority

        val intent = Intent(DocumentsContract.PROVIDER_INTERFACE).setPackage(PackageName)
        return packageManager.queryIntentContentProviders(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { it.providerInfo }
            .firstOrNull { it.packageName == PackageName }
            ?.authority
            ?.substringBefore(';')
    }
}
