package id.tirtapatriot.activitycamera.sync

import id.tirtapatriot.activitycamera.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

actual object BackgroundSync {
    private const val MAX_ROUNDS = 8
    private const val MAX_BACKOFF_MS = 5 * 60_000L

    // State below is only touched on the main thread.
    private val scope = MainScope()
    private var job: Job? = null
    private var requestedAgain = false

    actual fun request() {
        scope.launch { start() }
    }

    private fun start() {
        if (job?.isActive == true) {
            requestedAgain = true
            return
        }
        job = scope.launch {
            var backoff = 15_000L
            repeat(MAX_ROUNDS) {
                requestedAgain = false
                val result = withExtraTime { withContext(Dispatchers.Default) { AppGraph.syncClient.syncAll() } }
                if (!result.shouldRetry && !requestedAgain) return@launch
                if (result.shouldRetry) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    /** Asks iOS for a little time to finish an upload if the officer leaves the app mid-way. */
    private suspend fun <T> withExtraTime(block: suspend () -> T): T {
        val app = UIApplication.sharedApplication
        var taskId = UIBackgroundTaskInvalid
        taskId = app.beginBackgroundTaskWithExpirationHandler {
            app.endBackgroundTask(taskId)
            taskId = UIBackgroundTaskInvalid
        }
        try {
            return block()
        } finally {
            if (taskId != UIBackgroundTaskInvalid) app.endBackgroundTask(taskId)
        }
    }
}
