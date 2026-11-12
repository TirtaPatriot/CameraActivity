package id.tirtapatriot.activitycamera.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.platform.AndroidPlatform
import java.util.concurrent.TimeUnit

actual object BackgroundSync {
    private const val WORK_NAME = "capture-sync"

    actual fun request() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: a capture made while a sync runs is picked up by the next run.
        WorkManager.getInstance(AndroidPlatform.appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

/** Uploads waiting captures; WorkManager runs it when online, even after the app was closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AndroidPlatform.init(applicationContext)
        val result = AppGraph.syncClient.syncAll()
        return when {
            result.shouldRetry && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
