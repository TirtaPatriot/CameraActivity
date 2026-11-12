package id.tirtapatriot.activitycamera.sync

/**
 * Runs [SyncClient.syncAll] in the background and retries with backoff while offline.
 * Android: WorkManager (survives the app being closed). iOS: a coroutine while the app is alive;
 * whatever is left is retried the next time the app opens.
 */
expect object BackgroundSync {
    fun request()
}
