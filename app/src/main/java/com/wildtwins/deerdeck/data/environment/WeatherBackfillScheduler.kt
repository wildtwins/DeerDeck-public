package com.wildtwins.deerdeck.data.environment

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.db.DeerDeckDatabase
import java.util.concurrent.TimeUnit

object WeatherBackfillScheduler {
    internal const val SessionIdKey = "session_id"

    fun schedule(context: Context, sessionId: Long) {
        enqueue(context, sessionId, initialDelayMinutes = 10, ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(
        context: Context,
        sessionId: Long,
        initialDelayMinutes: Long,
        policy: ExistingWorkPolicy,
    ) {
        val builder = OneTimeWorkRequestBuilder<WeatherBackfillWorker>()
            .setInputData(Data.Builder().putLong(SessionIdKey, sessionId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        if (initialDelayMinutes > 0) {
            builder.setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
        }
        val request = builder.build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(sessionId),
            policy,
            request,
        )
    }

    fun cancel(context: Context, sessionId: Long) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(sessionId))
    }

    private fun workName(sessionId: Long): String = "deerdeck-weather-backfill-$sessionId"
}

class WeatherBackfillWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(WeatherBackfillScheduler.SessionIdKey, -1L)
        if (sessionId <= 0) return Result.failure()

        val dao = DeerDeckDatabase.getInstance(applicationContext).dao()
        val session = dao.getSession(sessionId) ?: return Result.success()
        if (!SessionEnvironmentFetcher.needsWeatherBackfill(session)) return Result.success()

        val environment = SessionEnvironmentFetcher.backfill(applicationContext, session)
        DeerDeckRepository(dao).updateSessionEnvironment(sessionId, environment)
        val updatedSession = dao.getSession(sessionId) ?: return Result.success()
        return if (!EnvironmentCaptureContract.needsBackfill(updatedSession)) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
