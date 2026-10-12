package io.github.sammeeeeeeee.higiene.ui

import android.content.Context
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.sammeeeeeeee.higiene.work.RefreshWorker
import io.github.sammeeeeeeee.higiene.work.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the user-visible refresh is doing right now. */
data class RefreshUi(
    val running: Boolean = false,
    val waitingForWifi: Boolean = false,
    val waiting: Boolean = false,
    val stage: String? = null,
    val done: Long = 0,
    val total: Long = 0,
    val failed: String? = null,
    /** Waiting to retry after an error (shown with [failed]), not waiting for a network. */
    val retrying: Boolean = false,
) {
    val active get() = running || waiting
    val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

fun refreshUiFlow(context: Context, lastError: () -> String? = { null }): Flow<RefreshUi> {
    val wm = WorkManager.getInstance(context)
    return combine(
        wm.getWorkInfosForUniqueWorkFlow(Scheduler.NOW),
        wm.getWorkInfosForUniqueWorkFlow(Scheduler.PERIODIC),
    ) { now, periodic ->
        val n = now.firstOrNull()
        val p = periodic.firstOrNull()
        when {
            n?.state == WorkInfo.State.RUNNING -> n.toUi()
            p?.state == WorkInfo.State.RUNNING -> p.toUi()
            n?.state == WorkInfo.State.ENQUEUED && n.runAttemptCount > 0 -> RefreshUi(retrying = true, failed = lastError())
            n?.state == WorkInfo.State.ENQUEUED || n?.state == WorkInfo.State.BLOCKED -> RefreshUi(
                waiting = true,
                waitingForWifi = n.constraints.requiredNetworkType == NetworkType.UNMETERED,
            )
            n?.state == WorkInfo.State.FAILED -> RefreshUi(failed = n.outputData.getString(RefreshWorker.KEY_ERROR) ?: lastError() ?: "Refresh failed")
            else -> RefreshUi()
        }
    }
}

private fun WorkInfo.toUi() = RefreshUi(
    running = true,
    stage = progress.getString(RefreshWorker.KEY_STAGE),
    done = progress.getLong(RefreshWorker.KEY_DONE, 0),
    total = progress.getLong(RefreshWorker.KEY_TOTAL, 0),
)
