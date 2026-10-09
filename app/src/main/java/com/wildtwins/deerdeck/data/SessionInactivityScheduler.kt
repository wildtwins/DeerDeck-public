package com.wildtwins.deerdeck.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wildtwins.deerdeck.data.db.DeerDeckDatabase
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import java.util.concurrent.TimeUnit

private const val InactivityWorkName = "deerdeck-active-session-inactivity"
private const val InactivityLimitMillis = 3 * 60 * 60 * 1_000L

object SessionInactivityScheduler {
    fun schedule(context: Context, lastActivityAt: Long) {
        val delayMillis = (lastActivityAt + InactivityLimitMillis - System.currentTimeMillis())
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<SessionInactivityWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            InactivityWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(InactivityWorkName)
    }
}

class SessionInactivityWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = DeerDeckDatabase.getInstance(applicationContext).dao()
        val repository = DeerDeckRepository(dao)
        val settingsRepository = AppSettingsRepository(applicationContext)
        val endedSession = repository.endInactiveActiveSessionIfNeeded()
        if (endedSession && settingsRepository.autoBackupEnabled.value) {
            try {
                val result = repository.writeVerifiedBackup(
                    context = applicationContext,
                    themeMode = settingsRepository.themeMode.value,
                    autoBackupEnabled = settingsRepository.autoBackupEnabled.value,
                    mediaSaveLocation = settingsRepository.mediaSaveLocation.value,
                    hapticFeedbackEnabled = settingsRepository.hapticFeedbackEnabled.value,
                    environmentCaptureEnabled = settingsRepository.environmentCaptureEnabled.value,
                    activeProfile = settingsRepository.activeProfile.value,
                    customProfiles = settingsRepository.profiles.value.filter { it.isCustom },
                    sessionTags = settingsRepository.sessionTags.value,
                    fieldLocations = settingsRepository.fieldLocations.value,
                    selectedFieldLocation = settingsRepository.selectedFieldLocation.value,
                    sharedLocationPrecision = settingsRepository.sharedLocationPrecision.value,
                    includeNamedFieldLocationsInShares =
                        settingsRepository.includeNamedFieldLocationsInShares.value,
                    fieldModeEnabled = settingsRepository.fieldModeEnabled.value,
                    keepScreenAwake = settingsRepository.keepScreenAwake.value,
                    backupDestination = settingsRepository.backupDestination.value,
                    googleDriveFolderUri = settingsRepository.googleDriveFolderUri.value,
                )
                settingsRepository.recordBackupSuccess(result)
            } catch (error: Throwable) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Backup failed."
                settingsRepository.recordBackupFailure(message)
            }
        }

        val active = dao.getActiveSession()
        if (active != null) {
            SessionInactivityScheduler.schedule(
                context = applicationContext,
                lastActivityAt = active.lastActivityAt ?: active.startedAt,
            )
        }

        return Result.success()
    }
}
