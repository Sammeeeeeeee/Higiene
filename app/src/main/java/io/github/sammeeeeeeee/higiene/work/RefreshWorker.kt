package io.github.sammeeeeeeee.higiene.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Db
import io.github.sammeeeeeeee.higiene.data.FsaImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads the FSA CSV (skipped via ETag when unchanged), then the OS postcode file when missing
 * or a month old, and alerts on rating changes for favourites.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val graph = (applicationContext as HigieneApp).graph
        val userInitiated = inputData.getBoolean(KEY_USER, false)
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (userInitiated) runCatching { setForeground(foregroundInfo(Notifications.progress(applicationContext, null, 0, 0))) }

        var lastPush = 0L
        try {
            graph.authorities.ensureLoaded()
            runCatching { graph.authorities.refreshIfStale() }

            val outcome = graph.fsa.refresh(
                force = force,
                onProgress = { p ->
                    val now = System.currentTimeMillis()
                    if (now - lastPush > 500) {
                        lastPush = now
                        setProgressAsync(workDataOf(KEY_STAGE to p.stage.name, KEY_DONE to p.done, KEY_TOTAL to p.total))
                        if (userInitiated) {
                            runCatching { setForegroundAsync(foregroundInfo(Notifications.progress(applicationContext, p.stage, p.done, p.total))) }
                        }
                    }
                },
                isStopped = { isStopped },
            )
            graph.repo.notifyChanged()

            // Postcode centres: first run, then monthly (OS publishes quarterly).
            val lastPc = graph.db.meta(Db.META_CODEPOINT_AT)?.toLongOrNull() ?: 0
            if (graph.postcodeIndex() == null || System.currentTimeMillis() - lastPc > MONTH) {
                setProgressAsync(workDataOf(KEY_STAGE to STAGE_POSTCODES, KEY_DONE to 0L, KEY_TOTAL to 0L))
                if (userInitiated) runCatching { setForegroundAsync(foregroundInfo(Notifications.progress(applicationContext, null, 0, 0, postcodes = true))) }
                val rebuilt = runCatching { graph.codePoint.refreshIfNeeded(force = false, isStopped = { isStopped }) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(false)
                // Positions were added in place, which fragments the table: compact once (~20% smaller).
                if (rebuilt) runCatching { graph.db.writableDatabase.execSQL("VACUUM") }
                graph.repo.notifyChanged()
            }

            if (outcome is FsaImporter.Outcome.Updated && outcome.favouriteChanges.isNotEmpty()) {
                Notifications.ratingChanges(applicationContext, outcome.favouriteChanges)
            }
            graph.db.setMeta(graph.db.writableDatabase, META_LAST_ERROR, null)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            graph.db.setMeta(graph.db.writableDatabase, META_LAST_ERROR, e.message ?: e.javaClass.simpleName)
            graph.repo.notifyChanged()
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Refresh failed")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(Notifications.progress(applicationContext, null, 0, 0))

    private fun foregroundInfo(n: android.app.Notification) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(Notifications.PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.PROGRESS_ID, n)
        }

    companion object {
        const val KEY_USER = "user"
        const val KEY_FORCE = "force"
        const val KEY_STAGE = "stage"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val STAGE_POSTCODES = "POSTCODES"
        const val META_LAST_ERROR = "last_error"
        private const val MONTH = 30L * 24 * 60 * 60 * 1000
    }
}
